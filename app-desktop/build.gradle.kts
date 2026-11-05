import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(project(":core-dsp"))
    implementation(project(":core-library"))
    implementation(project(":core-network"))
    implementation(project(":integration-api"))
    implementation(project(":core-ui"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Pure JVM decoders for MP3 and FLAC
    implementation("javazoom:jlayer:1.0.1")
    implementation("org.jflac:jflac-codec:1.5.2")

    // Windows Native Access (WASAPI, SMTC)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // SQLite JDBC for desktop library database
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.nodeplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "NodePlayer"
            packageVersion = "1.6.0"
            description = "Node Player for Windows - Shared Liquid Glass Pure Audio"
            vendor = "Krighz"
            copyright = "© 2026 Krighz. Licensed under GPLv3."

            modules("java.instrument", "java.sql", "jdk.unsupported", "java.naming")

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "63d1bc8e-1f8d-4f05-89f3-8b7bf48291f0"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
