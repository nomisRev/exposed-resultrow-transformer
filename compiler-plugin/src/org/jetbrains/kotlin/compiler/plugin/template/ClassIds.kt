package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class ClassIds(
    val resultRow: ClassId =
        ClassId(
//    packageFqName = FqName("org.jetbrains.kotlin.compiler.plugin.template"),
            packageFqName = FqName("org.jetbrains.exposed.sql"),
            relativeClassName = FqName("ResultRow"),
            isLocal = false,
        ),
    val annotation: AnnotationFqn = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation"),
    val table: ClassId =
        ClassId(
            packageFqName = FqName("org.jetbrains.exposed.sql"),
            relativeClassName = FqName("Table"),
            isLocal = false,
        ),
)

val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
    get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

val TABLE_CLASS_ID =
    ClassId(
        packageFqName = FqName("org.jetbrains.exposed.sql"),
        relativeClassName = FqName("Table"),
        isLocal = false,
    )
