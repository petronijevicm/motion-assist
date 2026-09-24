plugins {
    id("com.android.application")
}

android {
    namespace = "com.rhythmcreative.motionassist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rhythmcreative.motionassist"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    implementation("com.airbnb.android:lottie:6.6.2") {
        exclude(group = "com.squareup.okio")
    }
    implementation("com.squareup.okio:okio-jvm:3.9.1")

    testImplementation("junit:junit:4.13.2")
}
