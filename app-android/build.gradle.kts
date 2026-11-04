import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val defaultLastFmProperties = Properties().apply {
    val propFile = file("lastfm.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}
val localProperties = Properties().apply {
    val localPropFile = rootProject.file("local.properties")
    if (localPropFile.exists()) {
        localPropFile.inputStream().use { load(it) }
    }
}
fun getPropValue(props: Properties, key: String): String? {
    return props.getProperty(key) ?: props.getProperty("\uFEFF$key")
}
val lastFmApiKey = getPropValue(localProperties, "LASTFM_API_KEY")
    ?: getPropValue(defaultLastFmProperties, "LASTFM_API_KEY")
    ?: System.getenv("LASTFM_API_KEY")
    ?: ""
val lastFmApiSecret = getPropValue(localProperties, "LASTFM_API_SECRET")
    ?: getPropValue(defaultLastFmProperties, "LASTFM_API_SECRET")
    ?: System.getenv("LASTFM_API_SECRET")
    ?: ""

android {
    namespace = "com.nodeplayer.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nodeplayer.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.6.0"

        manifestPlaceholders["appName"] = "Node Player"

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmApiKey\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"$lastFmApiSecret\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            val userHome = System.getProperty("user.home") ?: System.getenv("USERPROFILE") ?: ""
            val defaultSigningPropsFile = File(userHome, "Documents/node-player-signing/signing.properties")
            val envSigningPropsPath = System.getenv("NODEPLAYER_SIGNING_PROPS")?.let { File(it) }
            val projectSigningPropsPath = (project.findProperty("signingProps") as? String)?.let { File(it) }

            val candidates = listOfNotNull(
                projectSigningPropsPath,
                envSigningPropsPath,
                defaultSigningPropsFile,
                File("C:/Users/satya/Documents/node-player-signing/signing.properties")
            )
            val signingPropFile = candidates.firstOrNull { it.exists() }

            val signingProperties = Properties().apply {
                if (signingPropFile != null && signingPropFile.exists()) {
                    signingPropFile.inputStream().use { load(it) }
                }
            }

            fun getProp(key: String): String? {
                return signingProperties.getProperty(key)
                    ?: signingProperties.getProperty("\uFEFF$key")
            }

            val envKeystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
            val propKeystorePath = getProp("storeFile")?.let { File(it) }

            val resolvedKeystore = envKeystoreFile ?: propKeystorePath
            if (resolvedKeystore != null && resolvedKeystore.exists()) {
                storeFile = resolvedKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: getProp("storePassword")
                keyAlias = System.getenv("KEY_ALIAS") ?: getProp("keyAlias")
                keyPassword = System.getenv("KEY_PASSWORD") ?: getProp("keyPassword")
            } else {
                storeFile = null
            }
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
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = releaseSigning
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            manifestPlaceholders["appName"] = "Node Player (Test)"
            matchingFallbacks += listOf("release")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(project(":core-dsp"))
    implementation(project(":core-library"))
    implementation(project(":core-network"))
    implementation(project(":integration-api"))
    implementation(project(":core-ui"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Haze Blur for Glassmorphism
    implementation("dev.chrisbanes.haze:haze:0.7.1")
    implementation("dev.chrisbanes.haze:haze-materials:0.7.1")

    // Media3 (ExoPlayer + MediaSession)
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Palette & Coil
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager for background audio analysis
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
