package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1DelegateProvider
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory3DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.CLASS_ID
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.COLLECTION
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

object Errors : BaseDiagnosticRendererFactory() {

    val DATA_CLASS_PROPERTY_NOT_FOUND by DiagnosticFactory3DelegateProvider<String, String, List<String>>(
        severity = Severity.ERROR,
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtParameter::class,
    )

    val DATA_CLASS_EXPECTED by DiagnosticFactory1DelegateProvider<ClassId>(
        severity = Severity.ERROR,
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtClass::class,
    )

    val ANNOTATION_ARGUMENT_NOT_TABLE by DiagnosticFactory1DelegateProvider<String>(
        severity = Severity.ERROR,
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtAnnotation::class,
    )

    override val MAP: KtDiagnosticFactoryToRendererMap =
        KtDiagnosticFactoryToRendererMap("ExposedPlugin").apply {
            put(
                DATA_CLASS_PROPERTY_NOT_FOUND,
                "data class constructor property: {0} was not found in {1} found columns: {2}.",
                TO_STRING,
                TO_STRING,
                COLLECTION(TO_STRING),
            )

            put(
                DATA_CLASS_EXPECTED,
                "Required declaration must be a data class, but found {0}.",
                CLASS_ID
            )

            put(
                ANNOTATION_ARGUMENT_NOT_TABLE,
                "Required declaration must have super type org.jetbrains.exposed.sql.Table, but found {0}.",
                TO_STRING
            )
        }

    init {
        RootDiagnosticRendererFactory.registerFactory(this)
    }
}
