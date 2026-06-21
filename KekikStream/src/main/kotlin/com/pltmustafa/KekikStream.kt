package com.pltmustafa

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLDecoder
import java.net.URLEncoder
import okhttp3.Dispatcher
import okhttp3.Request

class KekikStream : MainAPI() {
    override var mainUrl              = "https://stream.watchbuddy.tv/api/v1"
    override var name                 = "KekikStream"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val userAgent = "Dart/3.11 (dart:io)"
    private val headers = mapOf("User-Agent" to userAgent, "Accept" to "application/json")

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private var cachedPlugins: List<WBPlugin>? = null

    private val asyncClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 20
            maxRequestsPerHost = 20
        }
        app.baseClient.newBuilder()
            .dispatcher(dispatcher)
            .build()
    }

    private val asyncApp by lazy {
        com.lagradost.nicehttp.Requests(asyncClient)
    }

    override val mainPage: List<MainPageData> by lazy {
        try {
            val req = Request.Builder()
                .url("$mainUrl/get_all_plugins")
                .header("User-Agent", userAgent)
                .build()
                
            val responseText = asyncClient.newCall(req).execute().body?.string() ?: ""
            val response = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<WBResponse<List<WBPlugin>>>())
            val plugins = response?.result ?: emptyList()
            
            if (plugins.isNotEmpty()) {
                cachedPlugins = plugins
                val pluginNames = plugins.mapNotNull { it.name }.sorted()
                KekikStreamPlugin.savePluginNames(pluginNames)
            }

            if (plugins.isEmpty()) {
                listOf(MainPageData("Tüm Kaynaklar", "all"))
            } else {
                plugins.mapNotNull { plugin ->
                    val pluginName = plugin.name ?: return@mapNotNull null
                    if (!KekikStreamPlugin.isPluginEnabled(pluginName)) return@mapNotNull null
                    val categoryEntry = plugin.mainPage?.entries?.firstOrNull() ?: return@mapNotNull null
                    val encodedUrl = categoryEntry.key
                    val categoryName = categoryEntry.value
                    val encodedCategory = URLEncoder.encode(categoryName, "UTF-8")
                    MainPageData(pluginName, "$pluginName||$encodedUrl||$encodedCategory")
                }
            }
        } catch (e: Exception) {
            Log.e("KEKIK_DEBUG", "Failed to init mainPage tabs", e)
            listOf(MainPageData("Bağlantı Hatası", "all"))
        }
    }

    private suspend fun getPluginsForSearch(): List<WBPlugin> {
        cachedPlugins?.let { return it }
        try {
            val req = Request.Builder()
                .url("$mainUrl/get_all_plugins")
                .header("User-Agent", userAgent)
                .build()
            val responseText = asyncClient.newCall(req).execute().body?.string() ?: ""
            val res = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<WBResponse<List<WBPlugin>>>())
            val plugins = res?.result ?: emptyList()
            if (plugins.isNotEmpty()) {
                cachedPlugins = plugins
            }
            return plugins
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data
        if (data == "all") {
            return newHomePageResponse(emptyList())
        }

        val parts = data.split("||")
        if (parts.size < 3) return newHomePageResponse(emptyList())

        val pluginName = parts[0]
        val encodedUrl = parts[1]
        val encodedCategory = parts[2]

        try {
            val url = "$mainUrl/get_main_page?plugin=$pluginName&page=1&encoded_url=$encodedUrl&encoded_category=$encodedCategory"
            val req = asyncApp.get(url, headers = headers, timeout = 30000)
            
            val responseText = req.text
            val res = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<WBResponse<List<WBSearchItem>>>())
            val items = res?.result?.mapNotNull { it.toSearchResponse(pluginName, isSearch = false) } ?: emptyList()

            if (items.isEmpty()) return newHomePageResponse(emptyList())

            val homePageList = HomePageList(pluginName, items)
            return newHomePageResponse(listOf(homePageList), hasNext = false)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(KekikStreamPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        java.lang.Thread.sleep(1900L)
        
        val fullMessage = "Lütfen Sağ Üstteki Filtreleme Menüsünden İstediğiniz Kaynakları Seçin"
        val maxLength = 30
        
        val words = fullMessage.split(" ")
        val chunks = mutableListOf<String>()
        var currentChunk = ""
        
        for (word in words) {
            if (currentChunk.isEmpty()) {
                currentChunk = word
            } else if (currentChunk.length + 1 + word.length <= maxLength) {
                currentChunk += " $word"
            } else {
                chunks.add(currentChunk)
                currentChunk = word
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }
        
        return chunks.mapIndexed { index, text ->
            newMovieSearchResponse(
                text,
                "wb://watchbuddy?plugin=none&url=none$index",
                TvType.Movie
            ) {
                this.posterUrl = "https://img.icons8.com/color/512/error--v1.png"
            }
        }
    }

    private fun WBSearchItem.toSearchResponse(pluginName: String, isSearch: Boolean): SearchResponse? {
        val titleStr = this.title ?: this.name ?: return null
        val displayTitle = if (isSearch) "[$pluginName] $titleStr" else titleStr
        val posterStr = fixUrlNull(this.poster)
        val safeUrl = this.url ?: return null
        
        val encodedPlugin = URLEncoder.encode(pluginName, "UTF-8")
        val finalUrl = "wb://watchbuddy?plugin=$encodedPlugin&url=$safeUrl"

        return newMovieSearchResponse(displayTitle, finalUrl, TvType.Movie) {
            this.posterUrl = posterStr
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val queryPart = url.substringAfter("?", "")
            val queryParams = queryPart.split("&").associate { 
                val parts = it.split("=")
                parts[0] to if (parts.size > 1) parts[1] else ""
            }
            
            val pluginRaw = queryParams["plugin"] ?: throw Exception("Eklenti adı eksik")
            val decodedPluginName = URLDecoder.decode(pluginRaw, "UTF-8")
            val decodedUrl = queryParams["url"] ?: throw Exception("URL eksik")
            
            val apiUrl = "$mainUrl/load_item?plugin=$decodedPluginName&encoded_url=$decodedUrl"

            val reqBuilder = Request.Builder().url(apiUrl)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val responseText = asyncClient.newCall(reqBuilder.build()).execute().body?.string() ?: ""

            val res = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<WBResponse<WBItemDetail>>())
            val item = res?.result ?: throw Exception("İçerik detayı bulunamadı")

            val titleStr = item.title ?: item.name ?: "Bilinmeyen Başlık"
            val posterStr = fixUrlNull(item.poster)
            val descStr = item.description
            val yearInt = item.year?.toString()?.toIntOrNull()
            val ratingStr = item.rating?.toString()
            
            val tagsList = when (val t = item.tags) {
                is List<*> -> t.mapNotNull { it?.toString() }
                is String -> listOf(t)
                else -> emptyList()
            }
            
            val actorsList = when (val a = item.actors) {
                is List<*> -> a.mapNotNull { it?.toString() }
                is String -> listOf(a)
                else -> emptyList()
            }

            val isSeries = !item.episodes.isNullOrEmpty()

            if (isSeries) {
                val episodes = item.episodes!!.mapNotNull { ep ->
                    val epUrl = ep.url ?: return@mapNotNull null
                    val encodedPlugin = URLEncoder.encode(decodedPluginName, "UTF-8")
                    val epFinalUrl = "wb://watchbuddy?plugin=$encodedPlugin&url=$epUrl"
                    
                    newEpisode(epFinalUrl) {
                        this.name = ep.title ?: "Bölüm ${ep.episode}"
                        this.season = ep.season
                        this.episode = ep.episode
                    }
                }

                return newTvSeriesLoadResponse(titleStr, url, TvType.TvSeries, episodes) {
                    this.posterUrl = posterStr
                    this.plot = descStr
                    this.year = yearInt
                    this.tags = tagsList
                    if (ratingStr != null) {
                        try { this.score = Score.from10(ratingStr) } catch (e: Exception) {}
                    }
                    addActors(actorsList.map { Actor(it) })
                }
            } else {
                return newMovieLoadResponse(titleStr, url, TvType.Movie, url) {
                    this.posterUrl = posterStr
                    this.plot = descStr
                    this.year = yearInt
                    this.tags = tagsList
                    if (ratingStr != null) {
                        try { this.score = Score.from10(ratingStr) } catch (e: Exception) {}
                    }
                    addActors(actorsList.map { Actor(it) })
                }
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(KekikStreamPlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
        var _linksFound = 0
        val _callback: (ExtractorLink) -> Unit = { link ->
            _linksFound++
            callback.invoke(link)
        }
            val queryPart = data.substringAfter("?", "")
            val queryParams = queryPart.split("&").associate { 
                val parts = it.split("=")
                parts[0] to if (parts.size > 1) parts[1] else ""
            }
            
            val pluginRaw = queryParams["plugin"] ?: throw Exception("Eklenti adı bulunamadı")
            val decodedPluginName = URLDecoder.decode(pluginRaw, "UTF-8")
            val decodedUrl = queryParams["url"] ?: throw Exception("URL bulunamadı")
            
            Log.d("KEKIK_DEBUG", "loadLinks called for plugin: $decodedPluginName, url: $decodedUrl")
            
            val apiUrl = "$mainUrl/load_links?plugin=$decodedPluginName&encoded_url=$decodedUrl"
            Log.d("KEKIK_DEBUG", "loadLinks API Request: $apiUrl")

            val reqBuilder = Request.Builder().url(apiUrl)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val responseText = asyncClient.newCall(reqBuilder.build()).execute().body?.string() ?: ""

            val res = mapper.readValue(responseText, com.fasterxml.jackson.module.kotlin.jacksonTypeRef<WBResponse<List<WBLink>>>())
            val links = res?.result
            
            if (links == null || links.isEmpty()) {
                Log.e("KEKIK_DEBUG", "loadLinks found no links for $decodedPluginName. Response: $responseText")
                throw Exception("Link bulunamadı")
            }

            Log.d("KEKIK_DEBUG", "Extracted ${links.size} links for $decodedPluginName")

            links.forEach { link ->
                val linkUrl = link.url ?: return@forEach
                val isM3u8 = linkUrl.contains(".m3u8") || linkUrl.contains("master.txt") || linkUrl.contains("/hls/")
                val isDash = linkUrl.contains(".mpd")
                
                Log.d("KEKIK_DEBUG", "Extracted Link -> Name: ${link.name}, URL: $linkUrl, isM3u8: $isM3u8")

                val customHeaders = mutableMapOf<String, String>()
                customHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                customHeaders["Accept"] = "*/*"
                
                val ref = link.referer
                if (!ref.isNullOrEmpty()) {
                    customHeaders["Referer"] = ref
                    try {
                        val uri = java.net.URI(ref)
                        val origin = "${uri.scheme}://${uri.host}"
                        customHeaders["Origin"] = origin
        if (_linksFound == 0) throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        return true
                    } catch (e: Exception) {}
                }
                
                link.headers?.forEach { (k, v) -> customHeaders[k] = v }
                
                val extType = if (isM3u8) ExtractorLinkType.M3U8 else if (isDash) ExtractorLinkType.DASH else ExtractorLinkType.VIDEO

                callback.invoke(
                    ExtractorLink(
                        source = link.name ?: "WatchBuddy",
                        name = link.name ?: "WatchBuddy",
                        url = linkUrl,
                        referer = link.referer ?: "",
                        quality = Qualities.Unknown.value,
                        type = extType,
                        headers = customHeaders
                    )
                )

                link.subtitles?.forEach { sub ->
                    val subUrl = sub.url ?: return@forEach
                    val langName = (sub.name ?: "").lowercase()
                    val langCode = if (langName.contains("tur") || langName.contains("türk")) "tr" else "en"
                    Log.d("KEKIK_DEBUG", "Extracted Subtitle -> Lang: $langCode, URL: $subUrl")
                    subtitleCallback.invoke(
                        SubtitleFile(langCode, subUrl)
                    )
                }
            }
            return true
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ErrorUtils.showPluginError(KekikStreamPlugin.appContext, this.name, "LOAD_LINKS", data)
            }, 500)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    data class WBResponse<T>(
        @JsonProperty("result") val result: T? = null,
        @JsonProperty("success") val success: Boolean? = null
    )

    data class WBPlugin(
        @JsonProperty("name") val name: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("favicon") val favicon: String?,
        @JsonProperty("main_page") val mainPage: Map<String, String>?
    )

    data class WBSearchItem(
        @JsonProperty("url") val url: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster") val poster: String?
    )

    data class WBItemDetail(
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("year") val year: Any?,
        @JsonProperty("rating") val rating: Any?,
        @JsonProperty("tags") val tags: Any?,
        @JsonProperty("actors") val actors: Any?,
        @JsonProperty("episodes") val episodes: List<WBEpisode>?
    )

    data class WBEpisode(
        @JsonProperty("url") val url: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episode") val episode: Int?
    )

    data class WBLink(
        @JsonProperty("name") val name: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("referer") val referer: String?,
        @JsonProperty("headers") val headers: Map<String, String>?,
        @JsonProperty("subtitles") val subtitles: List<WBSubtitle>?
    )

    data class WBSubtitle(
        @JsonProperty("name") val name: String?,
        @JsonProperty("url") val url: String?
    )
}
