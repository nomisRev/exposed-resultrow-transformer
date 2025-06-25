// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.exposed.compiler.plugin.Transformer
import org.jetbrains.exposed.sql.Table

public object UsersTable : Table("users") {
    public val id = integer("id").autoIncrement().uniqueIndex()
    public val name = varchar("name", length = 50)
    public val age = integer("age")
    public val email = varchar("email", length = 255).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

<!DATA_CLASS_EXPECTED!>@Transformer(UsersTable::class)
class User(val name: String, val age: Int)<!>