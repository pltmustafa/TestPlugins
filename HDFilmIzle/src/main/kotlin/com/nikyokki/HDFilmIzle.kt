package com.nikyokki

import Video
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class HDFilmIzle : MainAPI() {
    override var mainUrl = "https://www.hdfilmizle.to"
    override var name = "HDFilmİzle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/tur/aile-1/" to "Aile Filmleri",
        "${mainUrl}/tur/aksiyon-2/" to "Aksiyon Filmleri",
        "${mainUrl}/tur/animasyon-1/" to "Animasyon Filmleri",
        "${mainUrl}/tur/belgesel/" to "Belgesel Filmleri",
        "${mainUrl}/tur/bilim-kurgu-1/" to "Bilim Kurgu Filmleri",
        "${mainUrl}/tur/dram-1/" to "Dram Filmleri",
        "${mainUrl}/tur/fantastik-1/" to "Fantastik Filmleri",
        "${mainUrl}/tur/gerilim-1/" to "Gerilim Filmleri",
        "${mainUrl}/tur/gizem-1/" to "Gizem Filmleri",
        "${mainUrl}/tur/komedi-1/" to "Komedi Filmleri",
        "${mainUrl}/tur/korku-1/" to "Korku Filmleri",
        "${mainUrl}/tur/macera-1/" to "Macera Filmleri",
        "${mainUrl}/tur/muzik/" to "Müzik Filmleri",
        "${mainUrl}/tur/romantik-1/" to "Romantik Filmler",
        "${mainUrl}/tur/savas-1/" to "Savaş Filmleri",
        "${mainUrl}/tur/suc-1/" to "Suç Filmleri",
        "${mainUrl}/tur/tarih-1/" to "Tarih Filmleri",
        "${mainUrl}/tur/tv-film-1/" to "TV Filmleri",
        "${mainUrl}/tur/vahsi-bati/" to "Vahşi Batı Filmleri",
        "${mainUrl}/tur/yerli-film-izle-1/" to "Yerli Filmler",
        "${mainUrl}/yabanci-dizi-izle-2/" to "Yabancı Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val url = if (page == 1) request.data else "${request.data}page/$page/"
            val document = app.get(url).document

            val home: List<SearchResponse>?

            home = document.select("div#moviesListResult a.poster").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(request.name, home, hasNext = true)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(HDFilmIzlePlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2.title")?.text() ?: ""
        val href = fixUrlNull(this.attr("href")) ?: ""
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score = this.selectFirst("div.poster-imdb")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val mainResp = app.get(mainUrl, cacheTime = 0)
        val mainDoc = mainResp.document
        
        val cookieStr = mainResp.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val rawXsrf = mainResp.cookies["XSRF-TOKEN"] ?: ""
        val xsrfToken = java.net.URLDecoder.decode(rawXsrf, "UTF-8")

        Log.e("HDF", "search X-XSRF-TOKEN: $xsrfToken")
        Log.e("HDF", "search Cookie: $cookieStr")

        val responseText = app.post(
            "$mainUrl/search/",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-XSRF-TOKEN" to xsrfToken,
                "Cookie" to cookieStr
            ),
            referer = mainUrl,
            data = mapOf("query" to query)
        ).text
        
        Log.e("HDF", "search response length: ${responseText.length}")
        
        val searchResults = mutableListOf<SearchResponse>()

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        try {
            val videos: List<Video> = objectMapper.readValue(responseText)
            Log.e("HDF", "search parsed videos count: ${videos.size}")
            videos.forEach { video ->
                val title = video.name
                val href = "$mainUrl/${video.slug}"
                val posterUrl = fixUrlNull(video.thumbUrl) ?: fixUrlNull(video.thumbWebp)

                searchResults.add(
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
                )
            }
        } catch (e: Exception) {
            Log.e("HDF", "Error parsing JSON: ${e.message}")
            Log.e("HDF", "Raw Response: $responseText")
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url).document

            val orgTitle = document.selectFirst("div.page-title h1")?.text() ?: ""
            val altTitle =
                document.selectFirst("div.page-title")?.selectFirst("small.text-muted.alt-name")?.text()
                    ?: ""
            val title =
                if (altTitle.isNotEmpty() && orgTitle != altTitle) "$orgTitle - $altTitle" else orgTitle
            val poster = fixUrlNull(document.selectFirst("picture.poster-auto img")?.attr("data-src"))
            val tags = document.select("div.pb-2.genres a").map { it.text() }
            val year = document.selectFirst("div.page-title")?.selectFirst("small.text-muted")?.text()
                ?.replace("(", "")?.replace(")", "")?.toIntOrNull()
            val description = document.selectFirst("article.text-white > p")?.text()?.trim()
            val rating = document.selectFirst("div.rate.mb-2 span")?.text()
            val actors = document.select("div.stories-wrapper a").map {
                Actor(
                    it.selectFirst("div.story-item-title")!!.text(),
                    fixUrlNull(it.select("img").attr("data-src"))
                )
            }

            val recommendations = document.select("div#swiper-wrapper-benzer").mapNotNull {
                val recName = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                val recHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
                    ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                newMovieSearchResponse(recName, recHref, TvType.Movie) {
                    this.posterUrl = recPosterUrl
                }
            }
            val trailer = document.selectFirst("div.nav-link")?.attr("data-trailer")

            val finalUrl = document.location()
            val isTvSeries = finalUrl.contains("/dizi/") || document.select("div.card-list-item a").isNotEmpty()
            
            if (isTvSeries) {
                val episodes = document.select("div.card-list-item a").mapNotNull { epNode ->
                    val href = fixUrlNull(epNode.attr("href")) ?: return@mapNotNull null
                    val epTitle = epNode.selectFirst("h3")?.text()?.trim() ?: ""
                    
                    val seasonMatch = Regex("(\\d+)\\.\\s*Sezon").find(epTitle)
                    val episodeMatch = Regex("(\\d+)\\.\\s*Bölüm").find(epTitle)
                    
                    val sNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
                    val eNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                    
                    newEpisode(href) {
                        this.name = epTitle
                        this.season = sNum
                        this.episode = eNum
                    }
                }
                
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(rating)
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            }

            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(HDFilmIzlePlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
        var _linksFound = 0
        val _callback: (ExtractorLink) -> Unit = { link ->
            if (link.url.isNotBlank()) {
    _linksFound++
    callback.invoke(link)
}
}
            Log.d("HDF", "data » ${data}")
            val document = app.get(data).document

            val iframe = document.selectFirst("iframe")?.attr("data-src") ?: ""
            Log.d("HDF", "iframe » ${iframe}")
            loadExtractor(iframe, mainUrl, subtitleCallback, _callback)

            if (_linksFound == 0) throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        return true
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ErrorUtils.showPluginError(HDFilmIzlePlugin.appContext, this.name, "LOAD_LINKS", data)
            }, 500)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
}