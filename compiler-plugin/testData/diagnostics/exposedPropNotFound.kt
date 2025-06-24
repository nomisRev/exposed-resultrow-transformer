// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation
import org.jetbrains.kotlin.compiler.plugin.template.UsersTable

@SomeAnnotation(UsersTable::class)
data class User(
    val name: String,
    <!DATA_CLASS_PROPERTY_NOT_FOUND!>val notExisting: Int<!>,
    <!DATA_CLASS_PROPERTY_NOT_FOUND!>val notExisting2: String<!>,
)