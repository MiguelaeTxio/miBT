plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miguelaetxio.mibt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.miguelaetxio.mibt"
        minSdk = 26
        targetSdk = 34
        // versionCode comes from the workflow (-PversionCode=run_number)
        // so every CI build can update the previous one.
        // ---
        // versionCode llega del workflow (-PversionCode=run_number) para
        // que cada build de CI pueda actualizar la anterior.
        versionCode = (project.findProperty("versionCode") as String?)
            ?.toIntOrNull() ?: 1
        versionName = "0.1"
    }

    signingConfigs {
        getByName("debug") {
            // Fixed debug keystore restored by the workflow, so updates
            // keep the same signature.
            // ---
            // Keystore de debug fija restaurada por el workflow, para que
            // las actualizaciones conserven la misma firma.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
