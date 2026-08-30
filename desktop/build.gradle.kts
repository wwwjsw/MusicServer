plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                // Compose Desktop
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)

                // Ktor server (same version as Android via libs.versions.toml)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.cors)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization.jackson)

                // Jackson Kotlin module for data class serialization
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")

                // Audio tag reading (ID3, FLAC, OGG, M4A…)
                implementation("net.jthink:jaudiotagger:3.0.1")

                // QR code generation (same ZXing lib as the Android module)
                implementation(libs.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.wwwjsw.musicserver.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "MusicServer"
            packageVersion = "1.5.0"
            description = "Music Server – Linux Desktop"
            vendor = "wwwjsw"

            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
    }
}

// Always bundle the latest web player release before the resources are
// processed and before running/packaging the app. KMP names the jvm resource
// and run tasks after the target ("desktopProcessResources", "desktopRun").
listOf(
    "processResources", "desktopProcessResources",
    "run", "desktopRun",
    "packageDeb", "packageRpm", "packageAppImage",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach {
        dependsOn(rootProject.tasks.named("fetchWebPlayer"))
    }
}
