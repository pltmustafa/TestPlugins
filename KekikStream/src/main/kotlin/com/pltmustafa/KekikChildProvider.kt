package com.pltmustafa

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import java.net.URLEncoder

class KekikChildProvider(val pluginName: String) : MainAPI() {
    override var name = "KekikStream - $pluginName"
    override var mainUrl = "https://stream.watchbuddy.tv/api/v1"
    override val hasMainPage = false
    override val hasQuickSearch = false
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val userAgent = "Dart/3.11 (dart:io)"
    private val headers = mapOf("User-Agent" to userAgent, "Accept" to "application/json")

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override suspend fun search(query: String): List<SearchResponse> {
        if (!KekikStreamPlugin.isPluginEnabled(pluginName)) {
            return emptyList()
        }
        
        java.lang.Thread.sleep(2000L)
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        try {
            val url = "$mainUrl/search?plugin=$pluginName&query=$encodedQuery"
            val req = app.get(url, headers = headers, timeout = 30000)
            
            val responseText = req.text
            val res = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<KekikStream.WBResponse<List<KekikStream.WBSearchItem>>>())
            
            val results = res?.result?.mapNotNull { it.toSearchResponse() } ?: emptyList()
            return results
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun KekikStream.WBSearchItem.toSearchResponse(): SearchResponse? {
        val titleStr = this.title ?: this.name ?: return null
        val posterStr = fixUrlNull(this.poster)
        val safeUrl = this.url ?: return null
        
        val encodedPlugin = URLEncoder.encode(pluginName, "UTF-8")
        val finalUrl = "wb://watchbuddy?plugin=$encodedPlugin&url=$safeUrl"

        return newMovieSearchResponse(titleStr, finalUrl, TvType.Movie) {
            this.posterUrl = posterStr
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return KekikStream().load(url)
    }
}
