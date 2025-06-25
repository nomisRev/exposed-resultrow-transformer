package org.jetbrains.kotlin.compiler.plugin.template

import kotlin.reflect.KClass

public annotation class SomeAnnotation(
    val kClass: KClass<*>,
)
