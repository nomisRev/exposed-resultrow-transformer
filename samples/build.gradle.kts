plugins {
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  id("org.jetbrains.kotlin.compiler.plugin.template") apply false
}
