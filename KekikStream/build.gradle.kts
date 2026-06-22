version = 10

cloudstream {
    authors     = listOf("pltmustafa")
    language    = "tr"
    description = "WatchBuddy api ile film ve dizi izleme eklentisi."
    
    status  = 0
    tvTypes = listOf("Movie", "TvSeries", "Anime")
    iconUrl = "https://watchbuddy.tv/static/home/ico/225x225bb.png"
}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
