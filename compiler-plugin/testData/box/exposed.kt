package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation
import org.jetbrains.kotlin.compiler.plugin.template.UserTable

@SomeAnnotation(UserTable::class)
data class User(val name: String, val age: Int)

fun box(): String {
    return "OK"
}