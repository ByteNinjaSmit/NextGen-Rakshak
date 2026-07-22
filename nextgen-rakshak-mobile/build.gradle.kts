// Top-level build file. Plugin versions shared across modules.
//
// Kotlin 2.x is required by the LiteRT runtime (see app/build.gradle.kts): its
// metadata is built with Kotlin 2.3, which a 1.9 compiler cannot read. From
// Kotlin 2.0 the Compose compiler ships as its own Gradle plugin instead of the
// `composeOptions.kotlinCompilerExtensionVersion` setting.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
