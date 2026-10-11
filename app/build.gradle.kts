plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixelschool.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelschool.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank() && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            } else {
                // CI / Local fallback: sign with debug keystore to guarantee a valid signed release APK
                val debugConfig = getByName("debug")
                val fallbackKeystore = debugConfig.storeFile ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
                if (!fallbackKeystore.exists()) {
                    fallbackKeystore.parentFile?.mkdirs()
                    try {
                        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                        val keytoolCmd = if (isWindows) "keytool.exe" else "keytool"
                        val keytoolPath = File(System.getProperty("java.home"), "bin/$keytoolCmd").takeIf { it.exists() }?.absolutePath ?: "keytool"
                        ProcessBuilder(
                            keytoolPath,
                            "-genkeypair",
                            "-keystore", fallbackKeystore.absolutePath,
                            "-storepass", "android",
                            "-alias", "androiddebugkey",
                            "-keypass", "android",
                            "-keyalg", "RSA",
                            "-keysize", "2048",
                            "-validity", "10000",
                            "-dname", "CN=Android Debug,O=Android,C=US"
                        ).start().waitFor()
                    } catch (e: Exception) {
                        logger.warn("Could not generate fallback keystore: ${e.message}")
                    }
                }
                storeFile = fallbackKeystore
                storePassword = debugConfig.storePassword ?: "android"
                keyAlias = debugConfig.keyAlias ?: "androiddebugkey"
                keyPassword = debugConfig.keyPassword ?: "android"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keeps debug builds default, fast and unsigned with release keys for local development
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.okhttp)
    implementation(libs.jsoup)

    debugImplementation(libs.androidx.ui.tooling)
}

