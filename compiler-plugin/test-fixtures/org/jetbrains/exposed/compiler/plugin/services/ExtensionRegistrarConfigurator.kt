package org.jetbrains.exposed.compiler.plugin.services

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.exposed.compiler.plugin.ClassIds
import org.jetbrains.exposed.compiler.plugin.Logger
import org.jetbrains.exposed.compiler.plugin.Module
import org.jetbrains.exposed.compiler.plugin.Options
import org.jetbrains.exposed.compiler.plugin.SimplePluginRegistrar
import org.jetbrains.exposed.compiler.plugin.ir.MyCodeIrGenerationExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(
    testServices: TestServices,
) : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        val module =
            Module(
                classIds =
                    ClassIds(
                        resultRow =
                            ClassId(
                                packageFqName = FqName("org.jetbrains.exposed.compiler.plugin"),
                                relativeClassName = FqName("ResultRow"),
                                isLocal = false,
                            ),
                    ),
                options = Options(configuration),
                logger = Logger(true),
            )
        FirExtensionRegistrarAdapter.registerExtension(SimplePluginRegistrar(module))
        IrGenerationExtension.registerExtension(MyCodeIrGenerationExtension(module))
    }
}
