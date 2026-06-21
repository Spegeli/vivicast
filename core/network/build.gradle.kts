plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vivicast.tv.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.retrofit)
}
