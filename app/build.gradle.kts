// SPDX-FileCopyrightText: 2026 petronijevicm
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.android.application")
}

android {
    namespace = "com.petronijevicm.motionassist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.petronijevicm.motionassist"
        minSdk = 29
        targetSdk = 35
        versionCode = 20
        versionName = "2.0.0"
    }

    // Release signing comes from the environment (see .github/workflows/android.yml).
    // Without it, assembleRelease produces an unsigned APK.
    val releaseKeystore = System.getenv("SIGNING_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")
}
