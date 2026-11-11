plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    stabilityConfigurationFile.set(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }
    jvm("desktop") {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core-common"))
                implementation(project(":core-model"))
                implementation(project(":core-dsp"))
                implementation(project(":core-library"))
                implementation(project(":core-network"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation("androidx.annotation:annotation:1.8.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.activity:activity-compose:1.9.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("androidx.annotation:annotation:1.8.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
    }
}

android {
    namespace = "com.nodeplayer.ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
