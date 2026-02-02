import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ml.innocomm.age_genderdetection"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ml.innocomm.age_genderdetection"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        setProperty("archivesBaseName", "AgeGenderEstimator_" +   versionName  )
    }
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    signingConfigs {
        create("releaseAndDefault") {
            storeFile = file(localProperties.getProperty("signing.storeFilePath", "../agegenderEst.jks"))
            storePassword = localProperties.getProperty("signing.storePassword", "")
            keyAlias = localProperties.getProperty("signing.keyAlias", "")
            keyPassword = localProperties.getProperty("signing.keyPassword", "")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseAndDefault")
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseAndDefault")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // 修正：更新到 1.5.1 以兼容 Kotlin 1.9.x (Metadata 2.0.0)
        kotlinCompilerExtensionVersion = "1.5.1"
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        noCompress += "tflite"
    }
    packaging {
        jniLibs {
            // 這是關鍵：強制讓所有 .so 檔案在打包時對齊到 16 KB
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Jetpack Compose (BoM)
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.fragment:fragment-ktx:1.6.1")

    // MLKit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ExifInterface for Android
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")



    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("androidx.camera:camera-extensions:1.3.0")

    // Media3 - ExoPlayer core
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    // Media3 - UI (包含 PlayerView)
    implementation("androidx.media3:media3-ui:1.3.1")
    // QR Code 核心編碼依賴 (Google 的開源項目，穩定可靠)
    implementation("com.google.zxing:core:3.5.3") // 使用最新穩定版本
    implementation("com.herohan:UVCAndroid:1.0.11")
}