@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    explicitApi()
    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                implementation("org.jetbrains.exposed:exposed-core:0.61.0")
            }
        }
    }
}
