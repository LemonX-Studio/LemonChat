plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lemonchat.log"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lemonchat.log"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file("lemonchat.jks")
            storePassword = System.getenv("JKS_PASS") ?: ""
            keyAlias = "LemonChat"
            keyPassword = System.getenv("JKS_PASS") ?: ""
        }
    }

    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
