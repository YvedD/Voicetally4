plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.2" apply false
}

tasks.register("clean", Delete::class) {
    // vervangt deprecated buildDir getter
    delete(layout.buildDirectory)
}
