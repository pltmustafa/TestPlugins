// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl              = "https://www.hdfilmcehennemi.nl"
    override var name                 = "HDFilmCehennemi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/load/page/sayfano/home/"                                       to "Yeni Eklenen Filmler",
        "${mainUrl}/load/page/sayfano/categories/nette-ilk-filmler/"               to "Nette İlk Filmler",
        "${mainUrl}/load/page/sayfano/home-series/"                                to "Yeni Eklenen Diziler",
        "${mainUrl}/load/page/sayfano/categories/tavsiye-filmler-izle2/"           to "Tavsiye Filmler",
        "${mainUrl}/load/page/sayfano/imdb7/"                                      to "IMDB 7+ Filmler",
        "${mainUrl}/load/page/sayfano/mostCommented/"                              to "En Çok Yorumlananlar",
        "${mainUrl}/load/page/sayfano/mostLiked/"                                  to "En Çok Beğenilenler",
        "${mainUrl}/load/page/sayfano/genres/aile-filmleri-izleyin-6/"             to "Aile Filmleri",
        "${mainUrl}/load/page/sayfano/genres/aksiyon-filmleri-izleyin-5/"          to "Aksiyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/animasyon-filmlerini-izleyin-5/"      to "Animasyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/belgesel-filmlerini-izle-1/"          to "Belgesel Filmleri",
        "${mainUrl}/load/page/sayfano/genres/bilim-kurgu-filmlerini-izleyin-3/"    to "Bilim Kurgu Filmleri",
        "${mainUrl}/load/page/sayfano/genres/komedi-filmlerini-izleyin-1/"         to "Komedi Filmleri",
        "${mainUrl}/load/page/sayfano/genres/korku-filmlerini-izle-4/"             to "Korku Filmleri",
        "${mainUrl}/load/page/sayfano/genres/romantik-filmleri-izle-2/"            to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val url = request.data.replace("sayfano", page.toString())
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Accept" to "*/*", "X-Requested-With" to "fetch"
            )
            val doc = app.get(url, headers = headers, referer = mainUrl, interceptor = interceptor)
            val home: List<SearchResponse>?
            if (!doc.toString().contains("Sayfa Bulunamadı")) {
                try {
                    val aa: HDFC = objectMapper.readValue(doc.toString())
                    val document = Jsoup.parse(aa.html)

                    home = document.select("a").mapNotNull { it.toSearchResult() }
                    return newHomePageResponse(request.name, home)
                } catch (e: Exception) {
                    Log.e("HDCH_Log", "Ana sayfa JSON okuma hatası: ${e.message}")
                }
            }
            return newHomePageResponse(request.name, emptyList())
        } catch (e: Exception) {
            ErrorUtils.showPluginError(HDFilmCehennemiPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response      = app.get(
            "${mainUrl}/search?q=${query}",
            headers = mapOf("X-Requested-With" to "fetch")
        ).parsedSafe<Results>() ?: return emptyList()
        val searchResults = mutableListOf<SearchResponse>()

        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml)

            val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
            val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl?.replace("/thumb/", "/list/") }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url, interceptor = interceptor).document

            val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
            val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
            val tags        = document.select("div.post-info-genres a").map { it.text() }
            val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
            val tvType      = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
            val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
            val actors      = document.select("div.post-info-cast a").map {
                Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
            }

            val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
                    val recName      = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                    val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                    newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                        this.posterUrl = recPosterUrl
                    }
                }

            return if (tvType == TvType.TvSeries) {
                val trailer  = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
                val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                    val epName    = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                    val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                    val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                    val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    newEpisode(epHref) {
                        this.name = epName
                        this.season = epSeason
                        this.episode = epEpisode
                    }
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl       = poster
                    this.year            = year
                    this.plot            = description
                    this.tags            = tags
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            } else {
                val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl       = poster
                    this.year            = year
                    this.plot            = description
                    this.tags            = tags
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(HDFilmCehennemiPlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun extractFinalUrl(decrypted: String): String {
        val hdchLink = if (decrypted.contains("+")) {
            decrypted.substringAfterLast("+")
        } else if (decrypted.contains(" ")) {
            decrypted.substringAfterLast(" ")
        } else if (decrypted.contains("|")){
            decrypted.substringAfterLast("|")
        } else {
            decrypted
        }
        return if (hdchLink.contains("https")) {
            hdchLink.substringAfter("https").let { "https$it" }
        } else {
            hdchLink
        }
    }

    private fun decryptNewFormat(jsCode: String): String {
        Log.d("HDCH_Extractor", "decryptNewFormat -> jsCode: $jsCode")
        try {
            val arrayString = jsCode.substringAfter("([", "").substringBefore("])")
            if (arrayString.isEmpty()) return ""
            
            val valueParts = arrayString.split(",")
                .map { it.trim('"', ' ', '\'') }
                .joinToString("")
            
            var result = valueParts
            
            result = result.map { c ->
                when {
                    c in 'a'..'z' -> if (c + 13 > 'z') c - 13 else c + 13
                    c in 'A'..'Z' -> if (c + 13 > 'Z') c - 13 else c + 13
                    else -> c
                }
            }.joinToString("")
            
            val decodedBytes = android.util.Base64.decode(result, android.util.Base64.DEFAULT)
            
            val reversedBytes = decodedBytes.reversedArray()
            
            val magicNumberRegex = Regex("""\((\d+)%\(i\+5\)""")
            val magicMatch = magicNumberRegex.find(jsCode)
            val magicNumber = magicMatch?.groupValues?.get(1)?.toIntOrNull() ?: 399756995
            
            var unmix = ""
            for (i in reversedBytes.indices) {
                var charCode = reversedBytes[i].toInt() and 0xFF
                charCode = (charCode - (magicNumber % (i + 5)) + 256) % 256
                unmix += charCode.toChar()
            }
            
            Log.d("HDCH_Extractor", "decryptNewFormat -> success! Decrypted URL part length: ${unmix.length}")
            return unmix
        } catch (e: Exception) {
            Log.e("HDCH_Extractor", "Error decrypting new format: ${e.message}")
            return ""
        }
    }

    private fun dcHello(base64Input: String): String {
        Log.d("HDCH_Extractor", "dcHello -> base64Input: $base64Input")
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)
        val extractedUrl = extractFinalUrl(decodedTwice)
        Log.d("HDCH_Extractor", "dcHello -> extractedUrl: $extractedUrl")
        return extractedUrl
    }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ) {
        Log.d("HDCH_Extractor", "invokeLocalSource -> url: $url")
        val script    = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).document.select("script").find { it.data().contains("sources:") }?.data() ?: return
        Log.d("HDCH_Extractor", "invokeLocalSource -> script found")
        val videoData = getAndUnpack(script).substringAfter("file_link=\"").substringBefore("\";")
        Log.d("HDCH_Extractor", "invokeLocalSource -> videoData: $videoData")
        
        val lastUrl = if (videoData.contains("dc_hello(\"")) {
            val base64Input = videoData.substringAfter("dc_hello(\"").substringBefore("\");")
            dcHello(base64Input)
        } else {
            val unmixed = decryptNewFormat(videoData)
            extractFinalUrl(unmixed)
        }
        
        Log.d("HDCH_Extractor", "Source: $source | Final URL: $lastUrl")
        
        if (lastUrl.isBlank()) {
            throw Exception("Video URL decrypt edilemedi (şifre kırma başarısız), site algoritması değişmiş!")
        }
        
        val subData   = script.substringAfter("tracks: [").substringBefore("]")
        AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions"}?.map {
            val subtitleUrl = "${mainUrl}${it.file}/"

	    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
        "Referer" to "subtitleUrl"
    )
    val subtitleResponse = app.get(subtitleUrl, headers = headers, allowRedirects=true, interceptor = interceptor)
                if (subtitleResponse.isSuccessful) {
                    subtitleCallback(SubtitleFile(it.language.toString(), subtitleUrl))
                    Log.d("HDCH", "Subtitle added: $subtitleUrl")
                } else {
                    Log.d("HDCH", "Subtitle URL inaccessible: ${subtitleResponse.code}")
                }
        }
        callback.invoke(
            newExtractorLink(
                source  = source,
                name    = source,
                url     = lastUrl,
                type    = ExtractorLinkType.M3U8
			) {
                headers = mapOf("Referer" to "${mainUrl}/", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0")
                quality = Qualities.Unknown.value
            }
        )
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    try {
        Log.d("HDCH_Extractor", "loadLinks processing: $data")
        val document = app.get(data, interceptor = interceptor).document
        
        val altLinks = document.select("div.alternative-links")
        Log.d("HDCH_Extractor", "Found alternative-links divs: ${altLinks.size}")
        
        var linksFound = 0

        altLinks.map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            val buttons = element.select("button.alternative-link")
            Log.d("HDCH_Extractor", "Found ${buttons.size} buttons for lang $langCode")
            
            buttons.map { button ->
                button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
            }.forEach { (source, videoID) ->
                linksFound++
                Log.d("HDCH_Extractor", "Processing source: $source, videoID: $videoID")
                val apiGet = app.get(
                    "${mainUrl}/video/$videoID/", interceptor = interceptor,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Requested-With" to "fetch"
                    ),
                    referer = data
                ).text
                Log.d("HDCH_Extractor", "apiGet -> response: $apiGet")
                var iframe = Regex("""data-src=\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)?.replace("\\", "")
                
                if (iframe == null) {
                    Log.d("HDCH_Extractor", "Iframe regex failed to find data-src in apiGet")
                    return@forEach
                }
                
                if (iframe.contains("rapidrame")) {
                    iframe = "${mainUrl}/rplayer/" + iframe.substringAfter("?rapidrame_id=")
                } else if (iframe.contains("mobi")) {
                    val iframeDoc = Jsoup.parse(apiGet)
                    iframe = fixUrlNull(iframeDoc.selectFirst("iframe")?.attr("data-src")) ?: return@forEach
                }
                Log.d("HDCH_Extractor", "Found iframe: $iframe for source: $source")
                invokeLocalSource(source, iframe, subtitleCallback, callback)
            }
        }
        
        Log.d("HDCH_Extractor", "Total links processed: $linksFound")
        if (linksFound == 0) {
            Log.e("HDCH_Extractor", "No links found, throwing exception to trigger popup!")
            throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        }
        
        return true
    } catch (e: Exception) {
        Log.e("HDCH_Extractor", "loadLinks error: ${e.message}")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ErrorUtils.showPluginError(HDFilmCehennemiPlugin.appContext, this.name, "LOAD_LINKS", data)
        }, 500)
        throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
    }
}
    private data class SubSource(
        @JsonProperty("file")    val file: String?  = null,
        @JsonProperty("label")   val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind")    val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
    data class HDFC(
        @JsonProperty("html") val html: String,
        @JsonProperty("meta") val meta: Meta
    )

    data class Meta(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("canonical") val canonical: String? = null,
        @JsonProperty("keywords") val keywords: String? = null
    )
}
