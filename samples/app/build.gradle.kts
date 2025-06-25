import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
    id("org.jetbrains.kotlin.compiler.plugin.template")
    application
}

application.mainClass.set("MainKt")

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
}

tasks.withType<KotlinCompile>().configureEach {
    incremental = false
}
