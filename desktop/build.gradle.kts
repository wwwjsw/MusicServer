plugins {

    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.jetbrainsCompose)

    alias(libs.plugins.compose.compiler)

}



kotlin {

    jvm("desktop")



    sourceSets {

        val jvmMain by getting {

            dependencies {

                implementation(compose.desktop.currentOs)

                implementation(compose.runtime)

                implementation(compose.foundation)

                implementation(compose.material3)

                implementation(libs.ktor.server.core)

                implementation(libs.ktor.server.netty)

                implementation(libs.ktor.server.cors)

                implementation(libs.ktor.server.content.negotiation)

                implementation(libs.ktor.serialization.jackson)

            }

        }

    }

}



compose.desktop {

    application {

        mainClass = "com.wwwjsw.musicserver.MainKt"

    }

}

