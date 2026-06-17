dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

version = 1

cloudstream {
    description = "TRT 1 Canli Yayin"
    authors = listOf("pltmustafa")
    status = 1
    tvTypes = listOf("Live")
    requiresResources = false
    language = "tr"
    iconUrl = "https://cms-tabii-public-image.tabii.com/int/webp/w600/q84/23846_1-0-465-262.jpeg"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
