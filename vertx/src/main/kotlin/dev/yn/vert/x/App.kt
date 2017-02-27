package dev.yn.vert.x

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions

fun main(args: Array<String>): Unit {

    val appVertx = Vertx.vertx(VertxOptions())
    dev.yn.vert.x.async.initItem(appVertx)
}



