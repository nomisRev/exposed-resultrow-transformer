// RUN_PIPELINE_TILL: FRONTEND

package my.test

import org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation
import org.jetbrains.kotlin.compiler.plugin.template.UsersTable
import org.jetbrains.kotlin.compiler.plugin.template.johnResultRow

<!DATA_CLASS_EXPECTED!>@SomeAnnotation(UsersTable::class)
class User(val name: String, val age: Int)<!>