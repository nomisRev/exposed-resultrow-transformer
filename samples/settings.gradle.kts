pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs { maybeCreate("libs").apply { from(files("../gradle/libs.versions.toml")) } }
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "samples"

include("app")

includeBuild("..")
