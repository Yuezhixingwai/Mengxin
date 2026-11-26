import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zhiyin"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.zhiyin"
        minSdk = 24
        targetSdk = 36
        versionCode = 40
        versionName = "2.3.14"
        // 同一 versionCode 复用批次号。每次配置都写新时间会让 BuildConfig 变化，正式包只能全量重编、重跑 R8。
        val batchKey = "$versionCode-$versionName"
        val batchFile = file(".build-batch")
        val cachedBatch = batchFile.takeIf { it.isFile }?.readLines()?.firstOrNull()?.trim()
        val buildBatch = if (cachedBatch != null && cachedBatch.startsWith("$batchKey ")) {
            cachedBatch.removePrefix("$batchKey ")
        } else {
            val fresh = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            batchFile.writeText("$batchKey $fresh\n")
            fresh
        }
        buildConfigField("String", "BUILD_BATCH", "\"$buildBatch\"")
        val localApi = providers.gradleProperty("localApiMode").orElse("false").get().toBoolean()
        val apiUrl = if (localApi) providers.gradleProperty("localApiBaseUrl").get()
            else "https://api.zhiyin.yuezhixingwai.cn"
        buildConfigField("boolean", "LOCAL_API", localApi.toString())
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        buildConfigField("String", "LOCAL_MEMORY_URL", "\"${if (localApi) providers.gradleProperty("localMemoryBaseUrl").get() else ""}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("zhiyin-release.keystore")
            storePassword = "ZhiYin@2026"
            keyAlias = "zhiyin"
            keyPassword = "ZhiYin@2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.okhttp)
    implementation(libs.haze)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.material.icons.extended)
    // 灵心助手（手机操控）：Shizuku 进阶通道（授权 Provider + 客户端 API + 低层 aidl）
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
}
