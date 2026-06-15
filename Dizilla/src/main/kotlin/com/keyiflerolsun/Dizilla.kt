// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class Dizilla : MainAPI() {
    override var mainUrl = "https://dizillahd.com"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    private val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("verifying")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }
	
    override val supportedSyncNames = setOf(
        SyncIdName.Simkl
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler" to "Yeni Eklenen Bölümler",
        "${mainUrl}/arsiv" to "Yeni Eklenen Diziler",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=15&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Aile",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=9&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Aksiyon",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=5&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Bilim Kurgu",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=2&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Dram",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=12&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Fantastik",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=18&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Gerilim",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=4&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Komedi",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=8&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Korku",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=24&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Macera",
        "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=7&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=1&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=" to "Romantik",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            println("Dizilla DEBUG - getMainPage: ${request.data}, page: $page")

            if (request.data.contains("/arsiv")) {
                // /arsiv için mevcut kod (GET ile çalışıyor)
                val response = app.get("${request.data}?page=$page", interceptor = interceptor)
                val document = response.document

                val script = document.selectFirst("script#__NEXT_DATA__")?.data()
                val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                val secureData = objectMapper.readTree(script)
                    .get("props")?.get("pageProps")?.get("secureData")?.asText()
                    ?: return newHomePageResponse(request.name, emptyList())

                val decodedData = decryptDizillaResponse(secureData)
                    ?: return newHomePageResponse(request.name, emptyList())

                val json = objectMapper.readTree(decodedData)
                val relatedResults = json.get("RelatedResults") ?: return newHomePageResponse(request.name, emptyList())
                val discoverArchive = relatedResults.get("getDiscoverArchive") ?: return newHomePageResponse(request.name, emptyList())
                val resultArray = discoverArchive.get("result") ?: return newHomePageResponse(request.name, emptyList())

                val home = resultArray.mapNotNull {
                    val title = it.get("title")?.asText() ?: return@mapNotNull null
                    val slug = it.get("slug")?.asText() ?: return@mapNotNull null
                    val poster = fixPosterUrl(fixUrlNull(it.get("poster")?.asText()))

                    newTvSeriesSearchResponse(title, fixUrl("/$slug"), TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                }
                return newHomePageResponse(request.name, home)

            } else if (request.data.contains("api/bg/findSeries")) {
                // API için POST kullan
                val apiUrl = request.data

                println("Dizilla DEBUG - API URL: $apiUrl")

                val response = app.post(
                    apiUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                        "Accept" to "application/json, text/plain, */*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "Origin" to mainUrl,
                        "Referer" to mainUrl
                    ),
                    data = mapOf("page" to page.toString())
                )

                val responseBody = response.body.string()
                println("Dizilla DEBUG - Response body: ${responseBody.take(10000)}...")

                val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                val jsonResponse = objectMapper.readTree(responseBody)
                val success = jsonResponse.get("success")?.asBoolean() ?: false
                println("Dizilla DEBUG - success: $success")

                if (!success) {
                    return newHomePageResponse(request.name, emptyList())
                }

                val encryptedData = jsonResponse.get("response")?.asText()
                println("Dizilla DEBUG - encryptedData length: ${encryptedData?.length}")

                if (encryptedData.isNullOrEmpty()) {
                    return newHomePageResponse(request.name, emptyList())
                }

                val decodedData = decryptDizillaResponse(encryptedData)
                println("Dizilla DEBUG - decodedData success: ${decodedData != null}")

                if (decodedData == null) {
                    return newHomePageResponse(request.name, emptyList())
                }

                println("Dizilla DEBUG - Raw decodedData: ${decodedData.take(10000)}...")

                // ★★★ MANUEL PARSE - "result" array'ini bul ve ayıkla ★★★
                val resultStart = decodedData.indexOf("\"result\":[")
                if (resultStart == -1) {
                    println("Dizilla DEBUG - Could not find result array")
                    return newHomePageResponse(request.name, emptyList())
                }

                // "result":[ dizisini bulduktan sonra, dizinin sonunu bul
                var bracketCount = 0
                var resultEnd = resultStart
                var inString = false

                for (i in resultStart until decodedData.length) {
                    val c = decodedData[i]

                    if (c == '"' && (i == 0 || decodedData[i-1] != '\\')) {
                        inString = !inString
                    }

                    if (!inString) {
                        when (c) {
                            '[' -> bracketCount++
                            ']' -> {
                                bracketCount--
                                if (bracketCount == 0) {
                                    resultEnd = i
                                    break
                                }
                            }
                        }
                    }
                }

                if (bracketCount != 0) {
                    println("Dizilla DEBUG - Could not find end of result array")
                    return newHomePageResponse(request.name, emptyList())
                }

                // result array'ini ayıkla (9 = "result":[ uzunluğu)
                val resultArrayJson = decodedData.substring(resultStart + 9, resultEnd + 1)
                println("Dizilla DEBUG - Result array JSON: ${resultArrayJson.take(500)}...")

                // result array'ini parse et
                val resultArray = objectMapper.readTree(resultArrayJson)
                println("Dizilla DEBUG - resultArray isArray: ${resultArray.isArray}, size: ${resultArray.size()}")

                val home = resultArray.mapNotNull { item ->
                    val title = item.get("original_title")?.asText() ?:
                    item.get("culture_title")?.asText() ?:
                    item.get("title")?.asText() ?:
                    return@mapNotNull null

                    val poster = item.get("poster_url")?.asText() ?:
                    item.get("square_url")?.asText() ?:
                    item.get("face_url")?.asText() ?:
                    item.get("back_url")?.asText()

                    // itemString: JSON'ın tamamını içeren ham metin (String)
// item değişkenini (JsonNode) String'e çevirip Regex'e sokuyoruz
                    val slug = Regex("""\"serie_site_id\":0,.*?\"used_slug\":\"(.*?)\"""").find(item.toString())?.groupValues?.get(1) ?: ""
                    newTvSeriesSearchResponse(title, fixUrl(slug), TvType.TvSeries) {
                        this.posterUrl = fixUrlNull(poster)
                    }
                }

                println("Dizilla DEBUG - Found ${home.size} items")
                return newHomePageResponse(request.name, home)

            } else {
                // Diğer case'ler (HTML parsing)
                val document = Jsoup.parse(app.get(request.data, interceptor = interceptor).body.string())
                val home = if (request.data.contains("api")) {
                    document.select("span.watchlistitem-").mapNotNull { it.diziler() }
                } else {
                    document.select("div.col-span-3 a").mapNotNull { it.sonBolumler() }
                }
                return newHomePageResponse(request.name, home)
            }
        } catch (e: Exception) {
            println("Dizilla DEBUG - getMainPage exception: ${e.message}")
            e.printStackTrace()
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.diziler(): SearchResponse {
        val title = this.selectFirst("span.font-normal")?.text() ?: "return null"
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: "return null"
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private suspend fun Element.sonBolumler(): SearchResponse {
        val name = this.selectFirst("h2")?.text() ?: ""
        val epName = this.selectFirst("div.opacity-80")!!.text().replace(". Sezon ", "x")
            .replace(". Bölüm", "")

        val title = "$name - $epName"

        val epDoc = fixUrlNull(this.attr("href"))?.let { Jsoup.parse(app.get(it).body.string()) }

        val href = fixUrlNull(epDoc?.selectFirst("div.poster a")?.attr("href")) ?: "return null"

        val posterUrl = fixUrlNull(epDoc?.selectFirst("div.poster img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 1. API İsteği
        val searchReq = app.post(
            "${mainUrl}/api/bg/searchContent?searchterm=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "${mainUrl}/"
            )
        )

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // 2. İlk JSON yanıtını parse et (Genelde { "state": true, "response": "ENCRYPTED_BASE64_STRING" } yapısındadır)
        val searchResult: SearchResult = objectMapper.readValue(searchReq.toString())

        // 3. Şifreli "response" alanını çöz
        val encryptedBlob = searchResult.response
            ?: throw ErrorLoadingException("API response field is null")

        val decryptedJson = decryptDizillaResponse(encryptedBlob)
            ?: throw ErrorLoadingException("Decryption failed")

// DEBUG: Gelen ham string'i kontrol et
        println("DEBUG RAW: $decryptedJson")

// EĞER string başında { yoksa ve essage ile başlıyorsa tamir et
        val fixedJson = if (!decryptedJson.startsWith("{") && decryptedJson.contains("\"essage\"")) {
            "{m\"$decryptedJson"
        } else {
            decryptedJson
        }

        val contentJson: SearchData = objectMapper.readValue(fixedJson)
        print("Dizilla DEBUG - decryptedJson $decryptedJson")
        if (contentJson.state != true) {
            return emptyList() // State false ise boş liste dönmek daha güvenlidir
        }

        // 5. Sonuçları SearchResponse formatına dönüştür
        val veriler = mutableListOf<SearchResponse>()

        contentJson.result?.forEach { item ->
            val name = item.title.toString()
            val link = fixUrl(item.slug.toString())
            val posterLink = fixPosterUrl(item.poster.toString()) ?: ""
            val toSearchResponse = toSearchResponse(name, link, posterLink)
            veriler.add(toSearchResponse)
        }

        println("Dizilla DEBUG - Found ${veriler.size} items after decryption")
        return veriler
    }

    private fun toSearchResponse(ad: String, link: String, posterLink: String): SearchResponse {
        return newTvSeriesSearchResponse(
            ad,
            link,
            TvType.TvSeries,
        ) {
            this.posterUrl = posterLink
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val mainReq = app.get(url, interceptor = interceptor)
        val document = mainReq.document
        val title = document.selectFirst("div.poster.poster h2")?.text() ?: return null
        val poster = fixPosterUrl(fixUrlNull(document.selectFirst("div.w-full.page-top.relative img")?.attr("src")))
        val year =
            document.select("div.w-fit.min-w-fit")[1].selectFirst("span.text-sm.opacity-60")?.text()
                ?.split(" ")?.last()?.toIntOrNull()
        val description = document.selectFirst("div.mt-2.text-sm")?.text()?.trim()
        val tags = document.selectFirst("div.poster.poster h3")?.text()?.split(",")?.map { it }
        val actors = document.select("div.global-box h5").map {
            Actor(it.text())
        }

        val episodeses = mutableListOf<Episode>()

        for (sezon in document.select("div.flex.items-center.flex-wrap.gap-2.mb-4 a")) {
            val sezonhref = fixUrl(sezon.attr("href"))
            val sezonReq = app.get(sezonhref)
            val split = sezonhref.split("-")
            val season = split[split.size-2].toIntOrNull()
            val sezonDoc = sezonReq.document
            val episodes = sezonDoc.select("div.episodes")
            for (bolum in episodes.select("div.cursor-pointer")) {
                val epName = bolum.select("a").last()?.text() ?: continue
                val epHref = fixUrlNull(bolum.select("a").last()?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("a")?.text()?.trim()?.toIntOrNull()
                val newEpisode = newEpisode(epHref) {
                    this.name = epName
                    this.season = season
                    this.episode = epEpisode
                }
                episodeses.add(newEpisode)
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = interceptor).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return false

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return try {
            val rootNode = objectMapper.readTree(script)
            val secureData = rootNode.path("props").path("pageProps").path("secureData").asText()

            if (secureData.isEmpty()) return false

            val decodedData = decryptDizillaResponse(secureData)

            if (decodedData.isNullOrEmpty()) {
                Log.e("DizillaDebug", "HATA: Decoded data boş!")
                return false
            }

            var linkFound = false

            // 1. JSON Kurallarını ezip geçiyoruz!
            // Ham string içinde "source_content":"..." kalıbını bulan Regex.
            // İçerideki kaçış karakterli tırnakları (\") sorunsuz tolere eder.
            val contentRegex = Regex(""""source_content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            val matches = contentRegex.findAll(decodedData)

            matches.forEach { match ->
                // 2. Regex ile yakalanan string'i JSON kaçış karakterlerinden temizle
                val rawHtml = match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\")

                // 3. Temizlenmiş HTML ( <iframe src="//..." ) içinden Jsoup ile src'yi al
                if (rawHtml.contains("iframe", ignoreCase = true)) {
                    var iframeUrl = Jsoup.parse(rawHtml).select("iframe").attr("src")

                    // Protokol düzeltmesi
                    if (iframeUrl.startsWith("//")) {
                        iframeUrl = "https:$iframeUrl"
                    }

                    val finalUrl = fixUrlNull(iframeUrl)

                    if (!finalUrl.isNullOrEmpty()) {
                        Log.d("DizillaDebug", "BİNGO! Regex ile Kırık Veriden Alınan Link: $finalUrl")
                        loadExtractor(finalUrl, "$mainUrl/", subtitleCallback, callback)
                        linkFound = true
                    }
                }
            }

            if (!linkFound) {
                Log.e("DizillaDebug", "HATA: Regex taraması link bulamadı. Ham Veri: ${decodedData.take(500)}")
            }

            linkFound

        } catch (e: Exception) {
            Log.e("DizillaDebug", "Kritik Hata: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return url

        // AMP CDN URL'lerini temizle:
        // Örn: https://images-macellan-online.cdn.ampproject.org/i/s/images.macellan.online/...
        // Sonuç: https://images.macellan.online/...
        return if (url.contains("cdn.ampproject.org")) {
            val regex = Regex("""cdn\.ampproject\.org/[^/]+/s/(.+)$""")
            val match = regex.find(url)
            if (match != null) {
                "https://${match.groupValues[1]}"
            } else {
                url
            }
        } else {
            url
        }
    }
    private fun decryptDizillaResponse(response: String): String? {
        try {
            val fullData = Base64.decode(response, Base64.DEFAULT)

            // Veriyi BÖLMÜYORUZ, tamamı şifreli veri
            val encryptedData = fullData

            // IV büyük ihtimalle AES Key ile aynıdır. Eğer hata verirse
            // IvParameterSpec(ByteArray(16)) yazarak sıfır IV deneyebilirsin.
            val ivSpec = IvParameterSpec(ByteArray(16))
            val keySpec = SecretKeySpec(privateAESKey.toByteArray(Charsets.UTF_8), "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedData)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
