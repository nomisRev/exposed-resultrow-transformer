package org.jetbrains.kotlin.compiler.plugin.template.services

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.template.ClassIds
import org.jetbrains.kotlin.compiler.plugin.template.Logger
import org.jetbrains.kotlin.compiler.plugin.template.Module
import org.jetbrains.kotlin.compiler.plugin.template.Options
import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.template.ir.MyCodeIrGenerationExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration
    ) {
        val module = Module(
            classIds = ClassIds(
                resultRow = ClassId(
                    packageFqName = FqName("org.jetbrains.kotlin.compiler.plugin.template"),
                    relativeClassName = FqName("ResultRow"),
                    isLocal = false,
                )
            ),
            options = Options(configuration),
            logger = Logger(true),
        )
        FirExtensionRegistrarAdapter.registerExtension(SimplePluginRegistrar(module))
        IrGenerationExtension.registerExtension(MyCodeIrGenerationExtension(module))
    }
}
