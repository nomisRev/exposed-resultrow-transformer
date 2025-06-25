// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.Transformer
import org.jetbrains.exposed.sql.Table

public object UsersTable : Table("users") {
    public val id = integer("id").autoIncrement().uniqueIndex()
    public val name = varchar("name", length = 50)
    public val age = integer("age")
    public val email = varchar("email", length = 255).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

@Transformer(UsersTable::class)
data class User(
    val name: String,
    <!DATA_CLASS_PROPERTY_NOT_FOUND!>val notExisting: Int<!>,
    <!DATA_CLASS_PROPERTY_NOT_FOUND!>val notExisting2: String<!>,
)