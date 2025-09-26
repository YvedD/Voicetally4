pluginManagement {
    repositories {
        // Volgorde: plugin portal eerst is oké
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Centrale plugin-versies (één bron van waarheid)
        id("com.android.application") version "8.6.1"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
        id("com.google.dagger.hilt.android") version "2.57.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoiceTally4"
include(":app")
