// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation

<!ANNOTATION_ARGUMENT_NOT_TABLE!>@SomeAnnotation(String::class)<!>
data class User(val name: String, val age: Int)
