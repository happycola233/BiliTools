// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // 显式声明以将版本目录中的 Kotlin 提到根类加载器，避免第三方插件使 AGP 回落到内置旧版 Kotlin 编译器
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}
