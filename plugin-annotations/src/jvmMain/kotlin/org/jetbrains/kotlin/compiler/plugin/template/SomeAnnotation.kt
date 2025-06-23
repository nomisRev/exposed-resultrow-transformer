package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import kotlin.reflect.KClass

public annotation class SomeAnnotation(val kClass: KClass<*>)

public object UsersTable : Table("users") {
    public val id: Column<Int> = integer("id").autoIncrement().uniqueIndex()
    public val name: Column<String> = varchar("name", length = 50)
    public val email: Column<String> = varchar("email", length = 255)
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
