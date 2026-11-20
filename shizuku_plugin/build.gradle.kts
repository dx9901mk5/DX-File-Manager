import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val propFile = rootProject.file("keystore.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.dx9901mk5.filemanager.shizukuplugin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dx9901mk5.filemanager.shizukuplugin"
        minSdk = 29
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
        base.archivesName.set("DX_Shizuku_Helper-v${versionName}")
    }

    signingConfigs {
        create("fixedRelease") {
            val keystoreFile = rootProject.file("app/release.keystore")
            storeFile = keystoreFile
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("KEY_STORE_PASSWORD")
                ?: "dx9901mk5"
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("KEY_ALIAS")
                ?: "dx_key"
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD")
                ?: "dx9901mk5"
        }
    }

    buildTypes {
        debug {
            if (rootProject.file("app/release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("app/release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
}
