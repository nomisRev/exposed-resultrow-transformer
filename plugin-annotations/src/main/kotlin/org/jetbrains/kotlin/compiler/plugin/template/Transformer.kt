package org.jetbrains.kotlin.compiler.plugin.template

import kotlin.reflect.KClass

public annotation class Transformer(
    val kClass: KClass<*>,
)
