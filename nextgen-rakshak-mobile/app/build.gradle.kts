import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.rakshak.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.rakshak.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Shared secret for the mesh HMAC (MeshCrypto). A packet whose MAC does not
        // verify under this key is dropped on receipt, so a device not running the
        // official build cannot inject or tamper with alert content on a relay.
        // Override per-deployment in local.properties (gitignored); the default
        // only keeps a dev build compiling.
        buildConfigField(
            "String",
            "MESH_HMAC_KEY",
            "\"${localProperties.getProperty("MESH_HMAC_KEY") ?: "rakshak-mesh-v1-dev-key-override-in-local-properties"}\"",
        )
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Guava — provides CameraX's ListenableFuture at compile time
    implementation("com.google.guava:guava:33.2.1-android")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Local prefs (volunteer identity)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Fused location (match GPS)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Image loading (child photo in match dialog)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room — offline pending-match store
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // WorkManager — background sync of pending matches
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // ML Kit face detection
    implementation("com.google.mlkit:face-detection:16.1.7")

    // LiteRT — the current name for the TensorFlow Lite runtime (MobileFaceNet
    // embeddings). Same org.tensorflow.lite.Interpreter API as org.tensorflow:
    // tensorflow-lite, so this is a drop-in replacement.
    //
    // Used in preference to org.tensorflow:tensorflow-lite because that artifact
    // still ships an x86_64 libtensorflowlite_jni.so aligned to 4 KB, which makes
    // Android 15 raise a "not 16 KB compatible" warning on launch. Every LiteRT
    // .so is 16 KB aligned on all three ABIs.
    //
    // tensorflow-lite-support is deliberately absent: nothing used it, and it
    // pulls in the old tensorflow-lite-api, whose org.tensorflow.lite.* classes
    // collide with LiteRT's.
    implementation("com.google.ai.edge.litert:litert:2.1.6")

    // Firebase (Firestore + Cloud Messaging)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Google sign-in via Credential Manager (the legacy GoogleSignIn API is deprecated)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Nearby Connections (offline mesh)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
