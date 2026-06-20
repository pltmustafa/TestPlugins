@file:Suppress("UnstableApiUsage")

dependencies {
    implementation("com.google.android.material:material:1.14.0")

    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
version = 2

cloudstream {
    description = "Cloud sync plugin for Cloudstream - syncs bookmarks, settings, extensions, watch history across devices"
    authors = listOf("pltmustafa")

    status = 1

    tvTypes = listOf("All")

    requiresResources = true
    language = "tr"

    iconUrl = "https://img.icons8.com/color/512/cloud-sync.png"

    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
