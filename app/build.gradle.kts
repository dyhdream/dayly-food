plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import org.gradle.api.tasks.Copy

android {
    namespace = "com.daylyfood.app"
    compileSdk = 35

    // Seed CSVs are authored in tools/seed; they are copied in at build time so the bundled
    // library can never drift from the source (it did once: drink sizes shipped stale).
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/seedAssets"))

    defaultConfig {
        applicationId = "com.daylyfood.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1101
        versionName = "0.11.1"

        // The OCR engine ships ~10 MB of native code per ABI and real phones here are all ARM.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true            // shortcut: release is debug-signed so we can sideload test builds;
            // replace with a real keystore before publishing anywhere.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Offline OCR for nutrition labels. Bundled model: works with no Google Play Services
    // and no API key, which is the whole point of keeping label scanning free.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Our own viewfinder so the album pick can live inside the camera screen instead of a second button.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

val syncSeedAssets = tasks.register<Copy>("syncSeedAssets") {
    from(rootProject.file("tools/seed")) { include("*.csv") }
    into(layout.buildDirectory.dir("generated/seedAssets/seed"))
}

tasks.named("preBuild") { dependsOn(syncSeedAssets) }




