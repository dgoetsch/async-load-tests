package dev.yn.spring.boot.async
/*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.RowMapper
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.Callable

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}

@javax.persistence.Entity(name = "item")
data class Item(@javax.persistence.Id val id: UUID, val name: String)


sealed abstract class ItemError: Throwable() {
    class NoRowsUpdated(val item: Item): ItemError()
    class DatabaseError(override val cause: Throwable): ItemError()

    class MissingRequiredField(val fieldName: String): ItemError()
}

@SpringBootApplication(scanBasePackages = arrayOf("dev.yn.spring.boot.async"))
@EnableAsync
class Application {

}

@RestController
@RequestMapping("items")
class ItemController(val itemService: ItemService) {

    data class ItemRequest(val name: String)

    @GetMapping("/{id}")
    fun getItem(@PathVariable id: UUID): DeferredResult<ResponseEntity<Item>> {
        val deferred = DeferredResult<ResponseEntity<Item>>()

        return itemService.getItem(id)?.let {
            ResponseEntity<Item>(it, HttpStatus.OK)
        } ?: ResponseEntity<Item>(HttpStatus.NOT_FOUND)
    }

    @PostMapping("/")
    fun createItem(@RequestBody item: ItemRequest): DeferredResult<ResponseEntity<Item>> {
        return itemService.createItem(Item(UUID.randomUUID(), item.name))?.let {
            ResponseEntity<Item>(it, HttpStatus.CREATED)
        } ?: ResponseEntity<Item>(HttpStatus.CONFLICT)
    }
}

@Service
class ItemService(val itemRepository: ItemRepository) {

    fun getItem(id: UUID): Item? {
        return itemRepository.findOne(id)
    }

    fun createItem(item: Item): Item? {
        return itemRepository.save(item)
    }
}

@Repository
interface ItemRepository: JpaRepository<Item, UUID>

@Service
class ItemJdbcRepository(@Autowired val jdbcTemplate: JdbcTemplate) {
    fun findOne(id: UUID, callback: (Item?) -> Unit) {
        jdbcTemplate.query(
                "SELECT id, name FROM item WHERE id=?::uuid LIMIT 1",
                arrayOf(id.toString()),
                RowCallbackHandler { row ->
                    callback(Item(UUID.fromString(row.getString("id")), row.getString("name")))
                })
    }
    fun save(item: Item, callback: (Int) -> Unit): {
        return if(jdbcTemplate.update(
                "INSERT INTO item(id,  name) VALUES (?::uuid,?)",
                PreparedStatementSetter {
                    it.setString(1, item.id.toString())
                    it.setString(2, item.name)
                }) == 1) item else null
    }
}*/
