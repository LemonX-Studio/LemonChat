plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lemon.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lemon.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 39
        versionName = "0.3.5"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../lemonchat.jks")
            storePassword = "lemonchat2026"
            keyAlias = "LemonChat"
            keyPassword = "lemonchat2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 高德地图定位+地图（定位功能启用时打开）
    // implementation("com.amap.api:3dmap:10.0.600")
    // implementation("com.amap.api:location:6.3.0")
}
