package dev.yn.vert.x.jdbc

import io.vertx.core.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider
import io.vertx.ext.sql.SQLConnection
import io.vertx.ext.sql.UpdateResult
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.funktionale.tries.Try
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

abstract class SimpleCodec<S>: MessageCodec<S, S> {
    override fun name(): String {
        return this.javaClass.simpleName
    }

    override fun systemCodecID(): Byte {
        return -1
    }

    override fun transform(s: S): S {
        return s
    }
}

object ItemCodec: SimpleCodec<Item>() {
    override fun encodeToWire(buffer: Buffer, s: Item) {
        buffer.appendInt(s.id.toString().length)
        buffer.appendString(s.id.toString())
        buffer.appendInt(s.name.length)
        buffer.appendString(s.name)

    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): Item {
        val idLength = buffer.getInt(pos)
        val id = UUID.fromString(buffer.getString(pos + 4, pos + idLength + 4))
        val namePos = idLength + 4 + pos
        val nameLength = buffer.getInt(namePos)
        val name = buffer.getString(namePos + 4, namePos + 4 + nameLength)
        return Item(id, name)
    }
}

object UUIDCodec: SimpleCodec<UUID>() {
    override fun encodeToWire(buffer: Buffer, s: UUID) {
        buffer.appendInt(s.toString().length)
        buffer.appendString(s.toString())
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): UUID {
        val idLength = buffer.getInt(pos)
        val id = UUID.fromString(buffer.getString(pos + 4, pos + idLength + 4))
        return id
    }
}

data class Item(val id: UUID, val name: String) {
    companion object {
        fun create(body: Buffer): Try<Item> =
                Try { JsonObject(String(body.bytes)) }
                        .flatMap { json ->
                            when(json.getString("name")) {
                                null -> Try.Failure<JsonObject>(ItemError.MissingRequiredField("name"))
                                else -> Try.Success(json)
                            }
                        }.map { Item(UUID.randomUUID(), it.getString("name")) }
    }

    fun serialize(format: String = "json"): Buffer {
        when(format) {
            "json" -> return Buffer.buffer("{\"id\":\"$id\",\"name\":\"$name\"}")
            else -> throw IllegalArgumentException("Illegal format $format")
        }
    }
}


sealed abstract class ItemError: Throwable() {
    class NoRowsUpdated(val item: Item): ItemError()
    class DatabaseError(override val cause: Throwable): ItemError()
    class ItemNotFound(val id: UUID): ItemError()
    class MissingRequiredField(val fieldName: String): ItemError()
    class Conflict(val id: UUID): ItemError()
    class UnhandledError(val error: Throwable): ItemError()
}
val lock = "lock"
fun createJdbcClient(vertx: Vertx): Unit {
    val jdbcConfig = JsonObject()
            .put("jdbcUrl", "jdbc:postgresql://localhost:5432/biz")
            .put("username", "postgres")
            .put("driverClassName", "org.postgresql.Driver")
            .put("maximumPoolSize", 30)
    val dataSource: DataSource = HikariCPDataSourceProvider().getDataSource(jdbcConfig)
    globalJdbcClient = JDBCClient.create(vertx, dataSource)
}
var globalJdbcClient: JDBCClient? = null

fun getOrCreateJdbcClient(vertx: Vertx): JDBCClient = synchronized(lock) {
    when(globalJdbcClient) {
        null -> {
            createJdbcClient(vertx)
            return globalJdbcClient!!
        }
        else -> return globalJdbcClient!!
    }
}
fun initItem(vertx: Vertx) {
    vertx.eventBus().registerDefaultCodec(Item::class.java, ItemCodec)
    vertx.eventBus().registerDefaultCodec(UUID::class.java, UUIDCodec)
    vertx.deployVerticle("dev.yn.vert.x.jdbc.ItemControllerVerticle", DeploymentOptions().setWorker(false).setInstances(4))
}

class ItemControllerVerticle(): AbstractVerticle() {
    val itemService: ItemService by lazy { ItemService(ItemRepository(getOrCreateJdbcClient(vertx))) }

    override fun start(): Unit {
        val router = Router.router(vertx)
        router.route().handler(BodyHandler.create());

        router.get("/items/:itemId").handler { routingContext ->
            try {
                val itemId = UUID.fromString(routingContext.request().getParam("itemId"))
                itemService.getItem(itemId,
                        Handler { item -> routingContext.request().response().setStatusCode(200).end(item.serialize()) },
                        itemErrorHandler(routingContext))
            } catch(e: IllegalArgumentException) {
                routingContext.request().response().setStatusCode(400).end()
            }
        }
        router.post("/items").handler { routingContext ->
            Item.create(routingContext.body).map { item: Item ->
                itemService.createItem(
                        item,
                        Handler<Item> { item -> routingContext.request().response().setStatusCode(201).end(item.serialize()) },
                        itemErrorHandler(routingContext))
            }.onFailure {
                routingContext.response().setStatusCode(400).end()
            }
        }

        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(8080)
    }

    fun itemErrorHandler(routingContext: RoutingContext): Handler<ItemError> = Handler {
        when(it) {
            is ItemError.NoRowsUpdated -> routingContext.request().response().setStatusCode(413).end()
            is ItemError.DatabaseError -> routingContext.request().response().setStatusCode(500).end()
            is ItemError.ItemNotFound -> routingContext.request().response().setStatusCode(404).end()
            is ItemError.MissingRequiredField -> routingContext.request().response().setStatusCode(400).end()
            is ItemError.Conflict -> routingContext.request().response().setStatusCode(413).end()
            is ItemError.UnhandledError -> routingContext.request().response().setStatusCode(500).end()
        }
    }
}
class ItemService(val itemRepository: ItemRepository) {
    fun createItem(item: Item, onSuccess: Handler<Item>, onFailure: Handler<ItemError>): Unit {
        try {
            val insertedItem: Item = item.let { Item(UUID.randomUUID(), it.name) }
            itemRepository.createItem(item, onSuccess, onFailure)
        } catch(e: Throwable) {
            onFailure.handle(ItemError.UnhandledError(e))
        }

    }

    fun getItem(id: UUID, onSuccess: Handler<Item>, onFailure: Handler<ItemError>): Unit {
        try {
            itemRepository.fetchItem(id, onSuccess, onFailure)
        } catch(e: Throwable) {
            onFailure.handle(ItemError.UnhandledError(e))
        }
    }
}

abstract class Repository(val jdbcClient: JDBCClient) {
    fun doWithConnection(onSuccess: Handler<SQLConnection>, onFailure: Handler<ItemError>) {
        jdbcClient.getConnection { connectionResult: AsyncResult<SQLConnection> ->
            if(connectionResult.succeeded()) {
                onSuccess.handle(connectionResult.result())
            } else {
                onFailure.handle(ItemError.DatabaseError(connectionResult.cause()))
            }
        }
    }

    fun <T> handleDatabaseExecution(onSuccess: Handler<T>, onFailure: Handler<ItemError>): (AsyncResult<T>) -> Unit = { executionResult: AsyncResult<T> ->
        if(executionResult.succeeded()) {
            onSuccess.handle(executionResult.result())
        } else {
            onFailure.handle(ItemError.DatabaseError(executionResult.cause()))
        }
    }
}

class ItemRepository(jdbcClient: JDBCClient): Repository(jdbcClient) {
    companion object {
        val InsertItemQuery = "INSERT INTO item (id, name) VALUES (?::uuid, ?)"

        val FetchItemById = "SELECT id, name FROM item WHERE id=?::uuid"
    }
    fun fetchItem(id: UUID, onSuccess: Handler<Item>, onFailure: Handler<ItemError>) {
        doWithConnection(
                Handler<SQLConnection> { connection ->
                    connection.queryWithParams(
                        FetchItemById,
                        JsonArray(listOf(id)),
                        handleDatabaseExecution(
                                Handler { it.rows.map { Item(UUID.fromString(it.getString("id")), it.getString("name")) }.firstOrNull()
                                        ?.let { onSuccess.handle(it) } ?: onFailure.handle(ItemError.ItemNotFound(id))
                                    connection.close()
                                },
                                onFailure
                        )
                    )
                },
                onFailure
        )
    }

    fun createItem(item: Item, onSuccess: Handler<Item>, onFailure: Handler<ItemError>) {
        doWithConnection(
                Handler<SQLConnection> { connection ->
                    connection.updateWithParams(
                            InsertItemQuery,
                            JsonArray(listOf(item.id, item.name)),
                            handleDatabaseExecution(
                                    Handler<UpdateResult> {
                                        connection.close()
                                        if(it.updated == 1) onSuccess.handle(item)
                                        else onFailure.handle(ItemError.Conflict(item.id))
                                    },
                                    onFailure))
                },
                onFailure
        )
    }
}
