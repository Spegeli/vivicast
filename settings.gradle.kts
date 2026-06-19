pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ViviCast"

include(":app-tv")
include(":app-mobile")
include(":core:model")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:playlist")
include(":core:epg")
include(":core:player-media3")
include(":core:domain")
