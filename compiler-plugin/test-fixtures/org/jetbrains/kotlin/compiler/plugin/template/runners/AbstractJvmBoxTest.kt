package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.template.services.ClasspathBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.compiler.plugin.template.services.ExtensionRegistrarConfigurator
import org.jetbrains.kotlin.compiler.plugin.template.services.PluginAnnotationsProvider
import org.jetbrains.kotlin.compiler.plugin.template.services.RuntimeClassPathProvider
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.junit.jupiter.api.BeforeEach
import kotlin.test.BeforeTest

open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        ClasspathBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)

        with(builder) {
            /*
             * Containers of different directives, which can be used in tests:
             * - ModuleStructureDirectives
             * - LanguageSettingsDirectives
             * - DiagnosticsDirectives
             * - FirDiagnosticsDirectives
             * - CodegenTestDirectives
             * - JvmEnvironmentConfigurationDirectives
             *
             * All of them are located in `org.jetbrains.kotlin.test.directives` package
             */
            defaultDirectives {
                JVM_TARGET.with(JvmTarget.JVM_11)
                +FULL_JDK
                +WITH_STDLIB

                +CodegenTestDirectives.DUMP_IR
                +FirDiagnosticsDirectives.FIR_DUMP
                +CodegenTestDirectives.IGNORE_DEXING // Avoids loading R8 from the classpath.
            }

            useConfigurators(::PluginAnnotationsProvider, ::ExtensionRegistrarConfigurator)
            useCustomRuntimeClasspathProviders(::RuntimeClassPathProvider)
        }
    }
}
