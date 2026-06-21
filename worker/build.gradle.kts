plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vivicast.tv.worker"
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
    implementation(project(":core:cache"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":data:epg"))
    implementation(project(":data:media"))
    implementation(project(":data:provider"))
    implementation(project(":domain"))
    implementation(project(":iptv:m3u"))
    implementation(project(":iptv:xmltv"))
    implementation(project(":iptv:xtream"))

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
