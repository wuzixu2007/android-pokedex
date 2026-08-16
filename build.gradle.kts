// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Root build plugins. / 根项目构建插件。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
