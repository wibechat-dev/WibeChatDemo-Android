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
        // One transitive dependency of the WibeChat SDK (audioswitch) is only
        // published on JitPack. Scoped so nothing else resolves from there.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "WibeChatDemo"

include(":app")   // Compose UI, notifications, Picture-in-Picture
include(":app-xml") // Same app with XML layouts + Fragments (no Compose UI)
include(":core")  // Config, Firebase (Auth + Firestore), Cloud Functions API clients
include(":calls") // WibeChat SDK wrapper: call session + video renderer (no Firebase)
