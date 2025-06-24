package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.kotlin.compiler.plugin.template.fir.FirCheckers
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class SimplePluginRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirCheckers
        +::MyCodeGenerationExtension
    }
}
