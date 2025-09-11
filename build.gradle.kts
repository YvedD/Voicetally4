// /build.gradle.kts
plugins {
    id("com.android.application") version "8.10.1" apply false
    kotlin("android") version "2.0.20" apply false
    // kapt versie volgt Kotlin, maar je hoeft 'm hier niet expliciet te declareren
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
