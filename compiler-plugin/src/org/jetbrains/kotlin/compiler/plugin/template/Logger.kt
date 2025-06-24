package org.jetbrains.kotlin.compiler.plugin.template

class Logger(private val debug: Boolean) {
    fun log(message: () -> String) {
        if (debug) {
            println(message())
        }
    }
}
