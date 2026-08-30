// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
}

// Registers the `fetchWebPlayer` task (downloads the latest web player release
// from wwwjsw/musicclient and refreshes the bundled music.zip files).
apply(from = "gradle/webplayer.gradle.kts")
