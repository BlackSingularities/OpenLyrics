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
rootProject.name = "OpenLyrics"
// Wspólny kod (interfejs, teksty, tagi, wyszukiwanie) dla Androida i Windows.
include(":shared")
// Aplikacja Android.
include(":app")
// Aplikacja Windows (Compose Desktop).
include(":desktop")
