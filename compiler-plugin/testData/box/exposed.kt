package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation

@SomeAnnotation
data class User(val name: String, val age: Int)

fun box(): String {
    return "OK"
}