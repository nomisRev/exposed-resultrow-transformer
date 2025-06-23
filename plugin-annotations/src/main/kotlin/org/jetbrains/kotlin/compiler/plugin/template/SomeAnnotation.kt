package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import kotlin.reflect.KClass

public annotation class SomeAnnotation(val kClass: KClass<*>)

public object UsersTable : Table("users") {
    public val id: Column<Int> = integer("id").autoIncrement().uniqueIndex()
    public val name: Column<String> = varchar("name", length = 50)
    public val age: Column<Int> = integer("age")
    public val email: Column<String?> = varchar("email", length = 255).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

public fun johnResultRow(): ResultRow =
    ResultRow.createAndFillValues(
        mapOf(
            UsersTable.id to 1,
            UsersTable.name to "John",
            UsersTable.age to 20,
            UsersTable.email to "<EMAIL>"
        )
    )