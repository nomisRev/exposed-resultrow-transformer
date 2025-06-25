// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.Transformer

<!ANNOTATION_ARGUMENT_NOT_TABLE!>@Transformer(String::class)<!>
data class User(val name: String, val age: Int)
