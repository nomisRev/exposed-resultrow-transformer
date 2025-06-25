plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    id("org.jetbrains.exposed.compiler.plugin") apply false
}
