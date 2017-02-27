package dev.yn.load.analyzer

import org.funktionale.collections.prependTo
import java.io.File

fun main(args: Array<String>) {
    val targets = listOf("spring-boot-jpa", "spring-boot-jdbc-template", "vertx-jdbc", "vertx-async", "vertx-hikari")
    val rates = listOf(100, 250, 500, 1000, 5000, 10000, 20000, 50000)

    val results = targets.map { target ->
        target to rates.map { rate ->
            rate to (1..10).mapNotNull { run ->
                try {
                    parseResults(File("../load-resources/$target/$rate-$run.txt"))
                } catch(e: Throwable) {
                    println("Error while processing $target:$rate:$run [$e]")
                    e.printStackTrace()
                    null
                }
            }
        }.toMap()
    }.toMap()

    fun printResultsForStat(name: String, getField: (LoadResult) -> String) {
        rates.forEach { rate ->
            File("rate-$rate-$name.csv").bufferedWriter().use { out ->
                out.appendln(listOf("Target").prependTo((1..10).map { it.toString() }).joinToString(","))
                targets.forEach { target ->
                    out.appendln(listOf<String>(target).prependTo(results.get(target)?.get(rate)?.map(getField) ?: emptyList<String>()).joinToString(","))
                }
            }
        }
    }


    printResultsForStat("MeanLatency", { it.latencies.mean.toString() })
    printResultsForStat("SuccessRate", { it.success.toString() })

//    File("results.csv").bufferedWriter().use { out ->
//        out.appendln(csvHeader)
//
//
//        targets.forEach { target ->
//            rates.forEach { rate ->
//                val results = (1..10).mapNotNull { it ->
//                    try {
//                        parseResults(File("../load-resources/${target}/${rate}.txt"))
//                    } catch(e: Throwable) {
//                        println("Error while processing $target:$rate [$e]")
//                        e.printStackTrace()
//                        null
//                    }
//                }
//                results
//            }
//        }
//    }
}


data class LoadResult(
        val requests: Requests,
        val duration: Duration,
        val latencies: Latencies,
        val bytesIn: ByteRate,
        val bytesOut: ByteRate,
        val success: Double,
        val statusCodes: List<StatusCode>,
        val error: String?
)
data class Requests(val total: Long, val rate: Double)
data class Duration(val total: Long, val attack: Long, val wait: Long)
data class Latencies(val mean: Long,
                     val fiftiethPercentile: Long,
                     val ninetyFifthPercentile: Long,
                     val ninetyNinethPercentile: Long,
                     val max: Long)
data class ByteRate(val total: Long, val mean: Double)

data class StatusCode(val code: Int, val count: Long)

val csvHeader = "Target,Rate,RequestCount,RequestPerSecond," +
        "TotalDuration,AttackDuration,WaitDuration," +
        "MeanLatency,50Latency,90Latency,95Latency,MaxLatency," +
        "BytesInTotal,BytesInMean,BytesOutTOtal,BytesOutMean," +
        "SuccessRatio,StatusCode0,StatusCode2xx,StatusCode4xx,StatusCode5xx,StatusCodeOther,Error"

val statusCodes = hashSetOf(0, 2, 4, 5)

fun toCsv(target: String, rate: Int, loadResult: LoadResult): List<String> {
    return listOf(target,rate.toString(),loadResult.requests.total.toString(),loadResult.requests.rate.toString(),
            loadResult.duration.total.toString(),loadResult.duration.attack.toString(),loadResult.duration.wait.toString(),
            loadResult.latencies.mean.toString(),loadResult.latencies.fiftiethPercentile.toString(),loadResult.latencies.ninetyFifthPercentile.toString(), loadResult.latencies.ninetyNinethPercentile.toString(), loadResult.latencies.max.toString(),
            loadResult.bytesIn.total.toString(), loadResult.bytesIn.mean.toString(), loadResult.bytesOut.total.toString(), loadResult.bytesOut.mean.toString(),
            loadResult.success.toString(),
            loadResult.statusCodes.filter { it.code / 100 == 0}.map { it.count }.sum().toString(),
            loadResult.statusCodes.filter { it.code / 100 == 2}.map { it.count }.sum().toString(),
            loadResult.statusCodes.filter { it.code / 100 == 4}.map { it.count }.sum().toString(),
            loadResult.statusCodes.filter { it.code / 100 == 5}.map { it.count }.sum().toString(),
            loadResult.statusCodes.filterNot { statusCodes.contains(it.code / 100) }.map { it.count }.sum().toString(),
            loadResult.error ?: "")
}

fun parseResults(file: File): LoadResult {
    val lines = file.readLines()
    val requestsLine = lines.get(1).removePrefixes(listOf("Requests", "[total, rate]")).split(",")
    val durationLine = lines.get(2).removePrefixes(listOf("Duration", "[total, attack, wait]")).split(",")
    val latenciesLine = lines.get(3).removePrefixes(listOf("Latencies", "[mean, 50, 95, 99, max]")).split(",")
    val bytesInLine = lines.get(4).removePrefixes(listOf("Bytes In", "[total, mean]")).split(",")
    val bytesOutLine = lines.get(5).removePrefixes(listOf("Bytes Out", "[total, mean]")).split(",")
    val successLine = lines.get(6).removePrefixes(listOf("Success", "[ratio]"))
    val statusCodesLine = lines.get(7).removePrefixes(listOf("Status Codes", "[code:count]")).split(" ")

    return LoadResult(
            Requests(requestsLine.get(0).trim().toLong(), requestsLine.get(1).trim().toDouble()),
            Duration(readTime(durationLine.get(0)), readTime(durationLine.get(1)), readTime(durationLine.get(2))),
            Latencies(readTime(latenciesLine.get(0)), readTime(latenciesLine.get(1)), readTime(latenciesLine.get(2)), readTime(latenciesLine.get(3)), readTime(latenciesLine.get(4))),
            ByteRate(bytesInLine.get(0).toLong(), bytesInLine.get(1).toDouble()),
            ByteRate(bytesOutLine.get(0).toLong(), bytesOutLine.get(1).toDouble()),
            successLine.removeSuffix("%").toDouble(),
            statusCodesLine.filter { it.trim().isNotEmpty() }.map {
                val split = it.split(":")
                StatusCode(split.get(0).toInt(), split.get(1).toLong())
            },
            if(lines.size > 9) lines.get(9) else null
    )
}

fun readTime(string: String): Long {
    if(string.endsWith("ns")) {
        return string.removeSuffix("ns").toDouble().toLong()
    } else if(string.endsWith("µs")) {
        return (string.removeSuffix("µs").toDouble() * 1000).toLong()
    } else if(string.endsWith("ms")) {
        return (string.removeSuffix("ms").toDouble() * 1000000).toLong()
    }else if(string.endsWith("s")) {
        return (string.removeSuffix("s").toDouble() * 1000000000).toLong()
    } else {
        throw RuntimeException("unhandled time")
    }
}

fun String.removePrefixes(prefixes: List<String>): String {
    return prefixes.fold(this) { string, prefix -> string.trim().removePrefix(prefix) }.trim()
}