// DIRECTIVES: FULL_JDK

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation
import org.jetbrains.kotlin.compiler.plugin.template.ResultRow
import org.jetbrains.exposed.sql.Table

@SomeAnnotation(UsersTable::class)
data class User(val name: String, val age: Int)

public object UsersTable : Table("users") {
    public val id = integer("id").autoIncrement().uniqueIndex()
    public val name = varchar("name", length = 50)
    public val age = integer("age")
    public val email = varchar("email", length = 255).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

fun box(): String {
    val row: Iterable<ResultRow> = listOf(ResultRow.createAndFillValues(
        UsersTable.id to 1,
        UsersTable.name to "John",
        UsersTable.age to 20,
        UsersTable.email to "<EMAIL>"
    ))
    val actual = row.toUsers()
    val expected = listOf(User("John", 20))
    return if (actual == expected) "OK" else "Fail: $actual"
}
