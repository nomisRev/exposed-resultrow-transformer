package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.kotlin.compiler.plugin.template.fir.FirCheckers
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class SimplePluginRegistrar(
    val logger: Module,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirCheckers.bind(logger)
        +::MyCodeGenerationExtension.bind(logger)
    }
}
