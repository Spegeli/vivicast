plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vivicast.tv.core.logging"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    // BuildConfig.DEBUG is the release kill-switch for vcLog(): R8/minify is off (see CLAUDE.md),
    // so android.util.Log is NOT stripped from release — the DEBUG guard is what keeps it out.
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
