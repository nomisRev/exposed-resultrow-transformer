import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.compiler.plugin.Transformer

@Transformer(Users::class)
data class User(
    val name: String,
    val age: Int,
    val email: String? = null,
)

object Users : LongIdTable("users", "user_id") {
    val name = varchar("name", length = 50)
    val age = integer("age")
    val email = varchar("email", length = 255).nullable()
}

fun main() {
    val db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction(db) {
        SchemaUtils.create(Users)
        val id =
            Users
                .insertAndGetId {
                    it[name] = "Simon"
                    it[age] = 20
                    it[email] = "<EMAIL>"
                }.value
        val rows =
            Users
                .selectAll()
                .where { Users.id eq id }

        val users = rows.toUsers()
        val user = rows.single().toUser()
        println("Generated user: $user, users: $users")
    }
}
