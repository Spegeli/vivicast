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

include(":app")
include(":core:common")
include(":core:cache")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:player")
include(":core:security")
include(":data:provider")
include(":data:epg")
include(":data:media")
include(":data:favorites")
include(":data:playback")
include(":domain")
include(":feature:live-tv")
include(":feature:movies")
include(":feature:series")
include(":feature:search")
include(":feature:settings")
include(":feature:player")
include(":iptv:m3u")
include(":iptv:xtream")
include(":iptv:xmltv")
include(":worker")
