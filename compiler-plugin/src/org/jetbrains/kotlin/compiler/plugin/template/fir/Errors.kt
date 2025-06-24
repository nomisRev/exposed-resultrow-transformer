package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory2DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory

/**
 * The compiler and the IDE use a different version of this class, so use reflection to find the available version.
 * https://github.com/TadeasKriz/K2PluginBase/blob/main/kotlin-plugin/src/main/kotlin/com/tadeaskriz/example/ExamplePluginErrors.kt#L8
 */
private val psiElementClass by lazy {
    try {
        Class.forName("org.jetbrains.kotlin.com.intellij.psi.PsiElement")
    } catch (_: ClassNotFoundException) {
        Class.forName("com.intellij.psi.PsiElement")
    }.kotlin
}

internal object Errors : BaseDiagnosticRendererFactory() {

    val MISSMATCH_CONSTRUCTOR_DATA_CLASS by DiagnosticFactory2DelegateProvider<String, String>(
        severity = Severity.ERROR,
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = psiElementClass,
    )

    override val MAP: KtDiagnosticFactoryToRendererMap =
        KtDiagnosticFactoryToRendererMap("ExposedPlugin").apply {
            put(
                MISSMATCH_CONSTRUCTOR_DATA_CLASS,
                "Constructor data class mismatch. Expected: {0}, actual: {1}.",
                TO_STRING,
                TO_STRING,
            )
        }

    init {
        RootDiagnosticRendererFactory.registerFactory(this)
    }
}
