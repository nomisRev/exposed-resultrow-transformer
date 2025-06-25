// DIRECTIVES: FULL_JDK

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.Transformer
import org.jetbrains.kotlin.compiler.plugin.template.ResultRow
import org.jetbrains.exposed.dao.id.LongIdTable

@Transformer(UsersTable::class)
data class User(val name: String, val age: Int)

public object UsersTable : LongIdTable("users", "user_id") {
    public val name = varchar("name", length = 50)
    public val age = integer("age")
    public val email = varchar("email", length = 255).nullable()
}

fun box(): String {
    val row = ResultRow.createAndFillValues(
        UsersTable.id to 1,
        UsersTable.name to "John",
        UsersTable.age to 20,
        UsersTable.email to "<EMAIL>"
    )
    val actual = row.toUser()
    val expected = User("John", 20)
    return if (actual == expected) "OK" else "Fail: $actual"
}
