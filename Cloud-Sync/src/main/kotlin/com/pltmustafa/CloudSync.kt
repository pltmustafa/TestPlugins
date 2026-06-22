package com.pltmustafa

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieLoadResponse

class CloudSync(val plugin: CloudSyncPlugin) : MainAPI() {
    override var name = "CloudSync"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "tr"
    override val hasMainPage = false
    override val hasQuickSearch = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    override suspend fun search(query: String): List<SearchResponse> = emptyList()
    override suspend fun load(url: String): LoadResponse? {
        return newMovieLoadResponse("Cloud-Sync", "", TvType.Others, "")
    }
}
