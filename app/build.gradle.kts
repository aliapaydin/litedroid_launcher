plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.litedroid.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.litedroid.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "1.9.0"
    }

    buildTypes {
        release {
            // R8 küçültme + optimizasyon: Compose'u debug'a göre belirgin şekilde akıcı yapar
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Kişisel kullanım için debug imzasıyla imzala (debug sürümün üstüne kurulabilir)
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Compose'un önceden derlenmiş profillerini kurar: ilk açılış ve kaydırma takılmalarını azaltır
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // "Devam et" ve uygulama içerik görselleri için
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    // Bilgi yarışması: telefonların okutacağı QR kod
    implementation("com.google.zxing:core:3.5.3")
}
