version = 2

cloudstream {
    authors     = listOf("pltmustafa")
    language    = "tr"
    description = "plt-stream - Birden fazla siteden içerik sağlayan hub görevi gören bir eklenti"

    status  = 1
    tvTypes = listOf("TvSeries", "Movie", "Anime")
    iconUrl = "https://raw.githubusercontent.com/pltmustafa/plt-stream/master/icon.png"
}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
