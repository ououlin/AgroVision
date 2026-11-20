plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.agrovision.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agrovision.app"
        minSdk = 26
        targetSdk = 35
        // V2.0 版本号：2.0.0 → 20000；后续 2.0.1 → 20001；2.1.0 → 20100
        versionCode = 20000
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // 不压缩 TFLite 模型
        aaptOptions {
            noCompress("tflite")
        }

        // BASE_URL 通过 BuildConfig 注入，NetworkModule 读取 BuildConfig.BASE_URL
        // defaultConfig 统一使用生产 HTTPS 地址，避免误打包指向模拟器内网
        // debug { ... } 块内会再覆盖为 10.0.2.2，方便本地调试
        buildConfigField("String", "BASE_URL", "\"https://api.agrovision.cn/\"")
    }

    // Release 签名：从 gradle.properties / 环境变量读取密钥信息
    //   - KEYSTORE_FILE: jks 绝对或相对路径（推荐放到项目外，避免提交）
    //   - KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    // 如果这些变量未配置，则 release 构建使用 debug 签名（可生成 APK 用于合规自查，
    // 但必须在正式上架前配置真 jks，否则所有应用市场都会拒审）
    val storeFileProp: String? = (project.findProperty("KEYSTORE_FILE") as String?)
        ?: System.getenv("KEYSTORE_FILE")
    val storePassProp: String? = (project.findProperty("KEYSTORE_PASSWORD") as String?)
        ?: System.getenv("KEYSTORE_PASSWORD")
    val keyAliasProp: String? = (project.findProperty("KEY_ALIAS") as String?)
        ?: System.getenv("KEY_ALIAS")
    val keyPassProp: String? = (project.findProperty("KEY_PASSWORD") as String?)
        ?: System.getenv("KEY_PASSWORD")

    val hasReleaseKeystore = !storeFileProp.isNullOrBlank()
            && !storePassProp.isNullOrBlank()
            && !keyAliasProp.isNullOrBlank()
            && !keyPassProp.isNullOrBlank()

    // Never allow a distributable release artifact to be produced with the debug key.
    // Lint and compilation-only release tasks remain available without signing secrets.
    gradle.taskGraph.whenReady {
        val createsReleaseArtifact = allTasks.any { task ->
            task.project == project &&
                (task.name == "packageRelease" || task.name == "signReleaseBundle")
        }
        if (createsReleaseArtifact && !hasReleaseKeystore) {
            throw org.gradle.api.GradleException(
                "Release signing is not configured. Set KEYSTORE_FILE, " +
                    "KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
            )
        }
        if (createsReleaseArtifact && !file(storeFileProp!!).isFile) {
            throw org.gradle.api.GradleException(
                "Release keystore does not exist: $storeFileProp"
            )
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(storeFileProp!!)
                storePassword = storePassProp
                keyAlias = keyAliasProp
                keyPassword = keyPassProp
                enableV1Signing = true    // 兼容 Android 6 及以下（minSdk=26 时其实仅 v2+即可，但保留 v1 让部分老 ROM 市场兼容）
                enableV2Signing = true    // Android 7+ 强制
                enableV3Signing = true    // Android 9+ 支持，用于密钥轮换
                enableV4Signing = true    // Android 11+，支持 ADB 增量安装
            } else {
                // 未配置 release jks 时回落到 debug 签名，保证 assembleRelease 不中断
                // 此时产生的 APK 不可上架！只用于本地 release 构建自查混淆/崩溃问题
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 始终挂 signingConfig release：
            //   - 配置了 jks → 用正式签名
            //   - 未配置 jks → release signingConfig 已 initWith debug，仍可构建
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 生产环境强制 HTTPS（defaultConfig 已经是 https://api.agrovision.cn/，此处保留便于未来切换不同生产地址）
            buildConfigField("String", "BASE_URL", "\"https://api.agrovision.cn/\"")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // debug 覆盖 defaultConfig，便于切换本地/测试服务器
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// ============================================================
// Room schema 快照
// 每次改表结构后，KSP 会把 app/schemas/ 下的 JSON 更新一份，
// 与版本号一起入库，迁移 diff 与迁移测试都以此为准。
// ============================================================
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ============ Core ============
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ============ Compose ============
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ============ Navigation ============
    implementation(libs.androidx.navigation.compose)

    // ============ Hilt DI ============
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ============ CameraX ============
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ============ TensorFlow Lite ============
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)

    // ============ ONNX Runtime (V3.0 替代TFLite) ============
    implementation(libs.onnxruntime.android)

    // ============ Location (天气与定位) ============
    implementation(libs.play.services.location)

    // ============ Security (V3.0 Login) ============
    implementation(libs.androidx.security.crypto)

    // ============ DataStore (V3.0 Settings) ============
    implementation(libs.androidx.datastore.preferences)

    // ============ Room ============
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ============ Logging ============
    implementation(libs.timber)

    // ============ Image Loading ============
    implementation(libs.coil.compose)

    // ============ JSON ============
    implementation(libs.gson)

    // ============ Network (V2.0) ============
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ============ OCR ML Kit (V2.0) ============
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)

    // ============ WorkManager + Hilt-Work (V2.0 农情顾问) ============
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ============ Testing ============
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
