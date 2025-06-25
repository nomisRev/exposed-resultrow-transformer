package org.jetbrains.exposed.compiler.plugin

import kotlin.reflect.KClass

public annotation class Transformer(
    val kClass: KClass<*>,
)
