@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("jvm")
    id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.exposed.core)
}
