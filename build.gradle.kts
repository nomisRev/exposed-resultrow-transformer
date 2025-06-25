import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.binaryCompatibilityValidator) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

project.group = "org.jetbrains.kotlin.compiler.plugin.template"
project.version = "0.0.1"

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "**/buildConfig/**", "**/testData/**")
        ktlint(libs.versions.ktlintTool.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlintTool.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("spotlessKotlinGradle") {
    dependsOn(":compiler-plugin:generateTests")
}

subprojects {
    plugins.apply("io.gitlab.arturbosch.detekt")

    detekt {
        parallel = true
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }

    val buildDirPath = layout.buildDirectory.asFile.get()
    val projectDirPath = projectDir.absoluteFile

    exclude {
        it.file
            .relativeTo(projectDirPath)
            .startsWith(buildDirPath.relativeTo(projectDirPath))
    }
}
