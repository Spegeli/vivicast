import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// P2-08: single root-applied detekt scanning every module's main sources. One config, one baseline,
// one `./gradlew detekt` task. The gate protects against new god-files (large classes/long/complex
// methods); existing accepted large files are tolerated via config/detekt/baseline.xml.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(
        files(subprojects.map { file("${it.projectDir}/src/main/java") }),
    )
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}

// Strings must live ONLY in :core:designsystem (see CLAUDE.md § Mandatory Architecture Rules). A <string>
// in app/ or a feature/ module silently shadows the designsystem value at resource-merge time (application
// resources override library resources), which once made corrected labels stop rendering (commit 97330fa).
// This guard fails the build on any stray <string> outside :core:designsystem. Hooked into the `detekt`
// architecture gate; also runnable standalone via `./gradlew checkStringsOnlyInDesignsystem`.
// File list captured at configuration time so it stays configuration-cache-safe.
val strayStringResourceFiles = subprojects
    .filter { it.path != ":core:designsystem" }
    .flatMap { it.fileTree("src/main/res").matching { include("values*/strings.xml") }.files }

tasks.register("checkStringsOnlyInDesignsystem") {
    group = "verification"
    description = "Fails if any module other than :core:designsystem defines a <string> resource."
    doLast {
        val offenders = strayStringResourceFiles.filter { it.readText().contains("<string ") }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Strings must live only in :core:designsystem (see CLAUDE.md). Move these into " +
                    "core/designsystem res:" + offenders.joinToString("\n  ", "\n  ") { it.path },
            )
        }
    }
}

tasks.named("detekt").configure { dependsOn("checkStringsOnlyInDesignsystem") }
