package com.pltmustafa

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

class Filmekseni : MainAPI() {
    override var mainUrl              = "https://filmekseni.top"
    override var name                 = "FilmEkseni"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/filmler/" to "Tüm Filmler",
        "$mainUrl/tur/aile-filmleri/" to "Aile",
        "$mainUrl/tur/aksiyon-filmleri/" to "Aksiyon",
        "$mainUrl/tur/animasyon-filmleri/" to "Animasyon",
        "$mainUrl/tur/belgesel-filmleri/" to "Belgesel",
        "$mainUrl/tur/bilim-kurgu-filmleri/" to "Bilim Kurgu",
        "$mainUrl/tur/biyografi-filmleri/" to "Biyografi",
        "$mainUrl/tur/dram-filmleri/" to "Dram",
        "$mainUrl/tur/fantastik-filmler/" to "Fantastik",
        "$mainUrl/tur/gerilim-filmleri/" to "Gerilim",
        "$mainUrl/tur/gizem-filmleri/" to "Gizem",
        "$mainUrl/tur/komedi-filmleri/" to "Komedi",
        "$mainUrl/tur/korku-filmleri/" to "Korku",
        "$mainUrl/tur/macera-filmleri/" to "Macera",
        "$mainUrl/tur/muzik-filmleri/" to "Müzik",
        "$mainUrl/tur/romantik-filmler/" to "Romantik",
        "$mainUrl/tur/savas-filmleri/" to "Savaş",
        "$mainUrl/tur/spor-filmleri/" to "Spor",
        "$mainUrl/tur/suc-filmleri/" to "Suç",
        "$mainUrl/tur/tarih-filmleri/" to "Tarih",
        "$mainUrl/tur/western-filmler/" to "Western",
        "$mainUrl/diziler/" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val url = if (page == 1) request.data else "${request.data}?page=$page"
            val document = app.get(url).document
            
            val home = document.select("a[class*='group/poster']").mapNotNull {
                it.toSearchResult()
            }
            
            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(FilmekseniPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val isSeries = href.contains("/dizi/")
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search?q=$query"
        val response = app.get(url, headers = mapOf("Accept" to "application/json")).parsedSafe<ApiSearchResponse>()
        return response?.data?.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            val poster = fixUrlNull(item.posterUrl)
            
            val type = if (item.type?.contains("Movie", true) == true) TvType.Movie else TvType.TvSeries
            
            val cleanSlug = slug.removePrefix("dizi/").removePrefix("film/")
            val itemUrl = if (type == TvType.Movie) "$mainUrl/$cleanSlug/" else "$mainUrl/dizi/$cleanSlug/"
            
            if (type == TvType.Movie) {
                newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url).document
            val title = document.selectFirst("h1")?.text()?.trim() ?: throw Exception("Başlık bulunamadı")
            val poster = fixUrlNull(document.selectFirst("div.aspect-\\[2\\/3\\] img")?.attr("src") ?: document.selectFirst("img[alt='$title']")?.attr("src"))
            
            val description = document.selectFirst("div.text-gray-300.leading-relaxed")?.text()?.trim()
            val infoBox = document.selectFirst("h3:contains(Bilgileri)")?.parent()
            
            val tags = infoBox?.select("a[href*='/tur/']")?.map { it.text().trim() } ?: emptyList()
            val year = infoBox?.selectFirst("a[href*='/yil/']")?.text()?.toIntOrNull()
            val ratingText = infoBox?.selectFirst("a[href*='imdb.com']")?.text()
            val ratingString = ratingText?.let { Regex("""(\d+\.\d+|\d+)""").find(it)?.groupValues?.get(1) }
            
            val actorsElements = document.select("div.horizontal-scroller-track a.group, div.horizontal-scroller-track div.group")
            val actorsList = actorsElements.mapNotNull { actorEl ->
                val name = actorEl.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val image = fixUrlNull(actorEl.selectFirst("img")?.attr("src"))
                Actor(name, image)
            }
            
            
            Log.d("Filmekseni", "Extracted Year: $year")
            Log.d("Filmekseni", "Extracted RatingText: $ratingText -> $ratingString")
            
            val isSeries = url.contains("/dizi/")
            val duration = document.select("div.flex.items-center.gap-4 span").firstOrNull { it.text().contains("dk") }?.text()?.filter { it.isDigit() }?.toIntOrNull()
            
            Log.d("Filmekseni", "load() called with url: $url, isSeries: $isSeries")
            Log.d("Filmekseni", "Title: $title")
            
            if (isSeries) {
                val epElements = document.select("a[href*='/sezon-']")
                Log.d("Filmekseni", "Found ${epElements.size} episode links")
                
                val episodes = epElements.mapNotNull {
                    val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                    val epText = it.text().trim()
                    
                    val seasonRegex = Regex("""/sezon-(\d+)""")
                    val episodeRegex = Regex("""/bolum-(\d+)""")
                    
                    val sMatch = seasonRegex.find(epHref)
                    val eMatch = episodeRegex.find(epHref)
                    
                    val sNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val eNum = eMatch?.groupValues?.get(1)?.toIntOrNull()
                    
                    newEpisode(epHref) {
                        this.name = epText
                        this.season = sNum
                        this.episode = eNum
                    }
                }
                Log.d("Filmekseni", "Total parsed episodes: ${episodes.size}")
                
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    if (ratingString != null) {
                        try {
                            this.score = Score.from10(ratingString)
                        } catch (e: Exception) {}
                    }
                    addActors(actorsList)
                }
            } else {
                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    this.duration = duration
                    if (ratingString != null) {
                        try {
                            this.score = Score.from10(ratingString)
                        } catch (e: Exception) {}
                    }
                    addActors(actorsList)
                }
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(FilmekseniPlugin.appContext, this.name, "LOAD_DETAILS", url)
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
            Log.d("Filmekseni", "loadLinks() called with data: $data")
            val html = app.get(data).document.outerHtml()
            
            val jsonStrMatch = Regex("""JSON\.parse\('([^']*)'\)""").find(html)
            if (jsonStrMatch != null) {
                Log.d("Filmekseni", "Found JSON.parse block")
                val rawJson = jsonStrMatch.groupValues[1].replace("\\u0022", "\"").replace("\\\\", "\\")
                Log.d("Filmekseni", "Raw JSON parsed length: ${rawJson.length}")
                
                val objects = rawJson.split("},{") 
                Log.d("Filmekseni", "Found ${objects.size} source objects")
                for (obj in objects) {
                    val link = Regex(""""link":"([^"]*)"""").find(obj)?.groupValues?.get(1)
                    val template = Regex(""""template":"([^"]*)"""").find(obj)?.groupValues?.get(1)
                    
                    Log.d("Filmekseni", "Parsed object link: $link, template: $template")
                    
                    if (link != null && template != null) {
                        val decodedTemplate = String(Base64.decode(template, Base64.DEFAULT))
                        Log.d("Filmekseni", "Decoded template: $decodedTemplate")
                        
                        val iframeSrcMatch = Regex("""data-src=\"([^\"]+)\"""").find(decodedTemplate) ?: Regex("""src=\"([^\"]+)\"""").find(decodedTemplate)
                        if (iframeSrcMatch != null) {
                            var iframeUrl = iframeSrcMatch.groupValues[1].replace("{url}", link)
                            if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
                            
                            Log.d("Filmekseni", "Final iframe URL: $iframeUrl")
                            try {
                                val iframeRes = app.get(iframeUrl, referer = data)
                                val iframeDoc = iframeRes.document.outerHtml()
                                val parsedDomain = Regex("""(https?://[^/]+)""").find(iframeRes.url)?.groupValues?.get(1) ?: "https://vidload.top"
                                
                                val m3u8Match = Regex("""file:\s*['"](.*?.m3u8)['"]""").find(iframeDoc)
                                if (m3u8Match != null) {
                                    val m3u8Url = if (m3u8Match.groupValues[1].startsWith("http")) m3u8Match.groupValues[1] else parsedDomain + m3u8Match.groupValues[1]
                                    Log.d("Filmekseni", "Found m3u8: $m3u8Url")
                                    _callback.invoke(
                                        newExtractorLink(
                                            source = this.name,
                                            name = "Vidload",
                                            url = m3u8Url,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            headers = mapOf("Referer" to iframeRes.url)
                                            quality = Qualities.Unknown.value
                                        }
                                    )
                                    
                                    val tracks = Regex("""file:\s*['"](.*?.vtt)['"],\s*label:\s*['"](.*?)['"]""").findAll(iframeDoc)
                                    tracks.forEach { track ->
                                        val subUrl = if (track.groupValues[1].startsWith("http")) track.groupValues[1] else parsedDomain + track.groupValues[1]
                                        val lang = track.groupValues[2]
                                        Log.d("Filmekseni", "Found subtitle: $lang - $subUrl")
                                        subtitleCallback.invoke(
                                            SubtitleFile(lang, subUrl)
                                        )
                                    }
                                } else {
                                    Log.d("Filmekseni", "m3u8 not found in iframe HTML")
                                }
        if (_linksFound == 0) throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        return true
                            } catch (e: Exception) {
                                Log.e("Filmekseni", "Error fetching iframe: ${e.message}")
                            }
                        } else {
                            Log.d("Filmekseni", "No iframe src found in decoded template")
                        }
                    }
                }
            } else {
                Log.d("Filmekseni", "Failed to find JSON.parse block in HTML")
                val altMatch = Regex("""x-data="videoPlayerData\((.*?)\)"""").find(html)
                if (altMatch != null) {
                    Log.d("Filmekseni", "Found videoPlayerData but not JSON.parse: ${altMatch.groupValues[1].take(200)}")
                }
            }
            
            return true
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ErrorUtils.showPluginError(FilmekseniPlugin.appContext, this.name, "LOAD_LINKS", data)
            }, 500)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }
}

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<ApiSearchData>? = null
)

data class ApiSearchData(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("contentableType") val type: String? = null,
    @JsonProperty("posterUrl") val posterUrl: String? = null
)
