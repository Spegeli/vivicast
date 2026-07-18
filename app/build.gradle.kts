import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is loaded from a gitignored keystore.properties (local dev) or, when that is absent,
// environment variables (CI). If neither provides a complete set, the release signingConfig is skipped so a
// fresh clone / CI-without-secrets can still configure and build debug. Secrets are never committed.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
fun signingSecret(propertyKey: String, envName: String): String? =
    (keystoreProperties.getProperty(propertyKey) ?: System.getenv(envName))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("storeFile", "VIVICAST_KEYSTORE_FILE")
val releaseStorePassword = signingSecret("storePassword", "VIVICAST_STORE_PASSWORD")
val releaseKeyAlias = signingSecret("keyAlias", "VIVICAST_KEY_ALIAS")
val releaseKeyPassword = signingSecret("keyPassword", "VIVICAST_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.vivicast.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vivicast.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8/minify intentionally OFF: open-source, non-Play (sideload / GitHub-release) app, so
            // obfuscation is pointless and code/resource shrink isn't worth the keep-rule/strip risk.
            // Retrofittable later if APK size ever matters.
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:cache"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:player"))
    implementation(project(":core:security"))
    implementation(project(":data:epg"))
    implementation(project(":data:favorites"))
    implementation(project(":data:media"))
    implementation(project(":data:playback"))
    implementation(project(":data:provider"))
    implementation(project(":domain"))
    implementation(project(":feature:home"))
    implementation(project(":feature:live-tv"))
    implementation(project(":feature:movies"))
    implementation(project(":feature:series"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:player"))
    implementation(project(":iptv:m3u"))
    implementation(project(":iptv:xmltv"))
    implementation(project(":iptv:xtream"))
    implementation(project(":worker"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
