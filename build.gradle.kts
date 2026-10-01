// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// AGP 9 启用内置 Kotlin（默认 KGP 2.2.0）。miuix 0.9.4 由 Kotlin 2.4.20 编译（元数据 2.4.0），
// 内置 2.2.0 编译器读不了，因此在这里把内置 Kotlin 升级到 2.4.20（官方支持的覆盖方式）。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
