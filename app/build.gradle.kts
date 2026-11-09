plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ir.kimia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.kimia.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // A fixed key committed to the repo so every CI build has the SAME signature
    // (otherwise Android refuses to update the app in place and you'd lose downloaded models).
    signingConfigs {
        create("kimia") {
            storeFile = rootProject.file("keystore/kimia-debug.jks")
            storePassword = "kimia-debug"
            keyAlias = "kimia"
            keyPassword = "kimia-debug"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("kimia") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("kimia")
        }
    }

    buildFeatures { compose = true }

    packaging {
        // llama.cpp loads its CPU backend variants from the native lib directory at runtime
        jniLibs { useLegacyPackaging = true }
    }

    lint { checkReleaseBuilds = false; abortOnError = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":aichat"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
