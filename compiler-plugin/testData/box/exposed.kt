package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation
import org.jetbrains.kotlin.compiler.plugin.template.UsersTable
import org.jetbrains.kotlin.compiler.plugin.template.JohnResultRow

@SomeAnnotation(UsersTable::class)
data class User(val name: String, val age: Int)

fun box(): String {
    return JohnResultRow.toUser()
}
