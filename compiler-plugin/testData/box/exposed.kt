package my.test

import org.jetbrains.kotlin.compiler.plugin.template.JohnResultRow

@org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation(
    org.jetbrains.kotlin.compiler.plugin.template.UsersTable::class
)
data class User(val name: String, val age: Int)

fun box(): String {
    return "OK"
}
