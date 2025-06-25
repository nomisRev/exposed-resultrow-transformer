package org.jetbrains.exposed.compiler.plugin

class Logger(
    private val debug: Boolean,
) {
    fun log(message: () -> String) {
        if (debug) {
            println(message())
        }
    }
}
