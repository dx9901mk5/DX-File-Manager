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
    namespace = "com.dx9901mk5.filemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dx9901mk5.filemanager"
        minSdk = 29
        targetSdk = 35
        versionCode = 211
        versionName = "2.1.1"
        base.archivesName.set("DX_fileManager-v${versionName}")

        buildConfigField(
            "String",
            "GITHUB_REPO_URL",
            "\"https://github.com/dx9901mk5/DX_fileManager\""
        )
        buildConfigField(
            "String",
            "GITHUB_API_LATEST",
            "\"https://api.github.com/repos/dx9901mk5/DX_fileManager/releases/latest\""
        )
    }

    signingConfigs {
        create("fixedRelease") {
            val keystoreFile = file("release.keystore")
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
            if (file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Shizuku API (靜默安裝 APK)
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    // 壓縮與 AES 加密
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.commons:commons-compress:1.27.1")

    // 網路與雲端協定 (SMB / SFTP / FTP / WebDAV / HTTP)
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("commons-net:commons-net:3.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
