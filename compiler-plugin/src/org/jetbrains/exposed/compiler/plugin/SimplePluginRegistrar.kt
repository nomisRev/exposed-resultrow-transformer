package org.jetbrains.exposed.compiler.plugin

import org.jetbrains.exposed.compiler.plugin.fir.FirCheckers
import org.jetbrains.exposed.compiler.plugin.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class SimplePluginRegistrar(
    val logger: Module,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirCheckers.bind(logger)
        +::MyCodeGenerationExtension.bind(logger)
    }
}
