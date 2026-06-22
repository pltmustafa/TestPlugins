package com.nikyokki

import com.pltmustafa.common.ErrorUtils
import com.pltmustafa.common.safeLoadLinks
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
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.Calendar

class SelcukFlix : MainAPI() {
    override var mainUrl = "https://selcukflix.co"
    override var name = "SelcukFlix"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Yeni Eklenen Filmler",
        "49" to "Aile Film",
        "44" to "Animasyon Film",
        "59" to "Aksiyon Film",
        "66" to "Bilim Kurgu Film",
        "48" to "Dram Film",
        "61" to "Fantastik Film",
        "68" to "Gerilim Film",
        "51" to "Gizem Film",
        "63" to "Korku Film",
        "45" to "Komedi Film",
        "65" to "Romantik Film",
        "46" to "Suç Film",
        "69" to "Savaş Film",
        "78" to "Western Film",

        "" to "Yeni Eklenen Diziler",
        "15" to "Aile Dizi",
        "17" to "Animasyon Dizi",
        "9" to "Aksiyon Dizi",
        "5" to "Bilim Kurgu Dizi",
        "2" to "Dram Dizi",
        "12" to "Fantastik Dizi",
        "18" to "Gerilim Dizi",
        "3" to "Gizem Dizi",
        "8" to "Korku Dizi",
        "4" to "Komedi Dizi",
        "7" to "Romantik Dizi",
        "1" to "Suç Dizi",
        "26" to "Savaş Dizi",
        "11" to "Western Dizi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val yil = Calendar.getInstance().get(Calendar.YEAR)
            var url =
                "$mainUrl/api/bg/findMovies?releaseYearStart=1900&releaseYearEnd=$yil&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=KATID&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=12&queryStr=&categorySlugsComma=&countryCodesComma="
            if (request.name.contains("Dizi")) {
                url = url.replace("findMovies", "findSeries")
            }
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val catDocument = app.post(
                url.replace("KATID", request.data), headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                    "Accept" to "application/json, text/plain, */*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Sec-Fetch-Site" to "same-origin",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Dest" to "empty",
                    "Referer" to "${mainUrl}/"
                ),
                referer = "${mainUrl}/"
            )
            val responseString = catDocument.toString()
            Log.d("SFX_DEBUG", "getMainPage Raw Response: $responseString")
            val searchResult: SearchResult = objectMapper.readValue(responseString)
            val converted = decryptResponse(searchResult.response.toString())
            Log.d("SFX_DEBUG", "getMainPage Converted JSON: $converted")
            val listItems: ListItems = objectMapper.readValue(converted)
            val home = listItems.result.map { it.toMainPageResult() }
            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(SelcukFlixPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun ContentItem.toMainPageResult(): SearchResponse {
        val title = this.originalTitle
        val href = fixUrlNull(this.usedSlug)
        val posterUrl = fixUrlNull(
            this.posterUrl.toString().replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
        )
        val score = this.imdbPoint

        return if (href!!.contains("/dizi/")) {
            newTvSeriesSearchResponse(title!!, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        } else {
            newMovieSearchResponse(title!!, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
            "${mainUrl}/api/bg/searchcontent?searchterm=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.5",
                "X-Requested-With" to "XMLHttpRequest",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Dest" to "empty",
                "Referer" to "${mainUrl}/"
            ),
            referer = "${mainUrl}/",
        )
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val searchReqString = searchReq.toString()
        Log.d("SFX_DEBUG", "search Raw Response: $searchReqString")
        val searchResult: SearchResult = objectMapper.readValue(searchReqString)
        val converted = decryptResponse(searchResult.response.toString())
        Log.d("SFX_DEBUG", "search Converted JSON: $converted")
        val contentJson: SearchData = objectMapper.readValue(converted)
        if (contentJson.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }
        val veriler = mutableListOf<SearchResponse>()
        contentJson.result?.forEach {
            val name = it.title.toString()
            val link = fixUrl(it.slug.toString())
            val posterLink =
                it.poster.toString().replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
            val type = it.type.toString()
            val toSearchResponse = toSearchResponse(name, link, posterLink, type)
            if (!link.contains("/seri-filmler/")) {
                veriler.add(toSearchResponse)
            }
        }
        return veriler
    }

    private fun decryptResponse(encryptedBase64: String): String {
        val cleanBase64 = encryptedBase64.replace(Regex("[^A-Za-z0-9+/=]"), "")
        val keyString = "!!22xx!!90!!"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(keyString.toByteArray(Charsets.UTF_8))
        val keyBase64 = android.util.Base64.encodeToString(hashBytes, android.util.Base64.NO_WRAP)
        val key = keyBase64.substring(0, 32).toByteArray(Charsets.UTF_8)

        val iv = ByteArray(16) { 0 }

        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val ivParameterSpec = javax.crypto.spec.IvParameterSpec(iv)

        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

        val encryptedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun toSearchResponse(
        ad: String,
        link: String,
        posterLink: String,
        type: String
    ): SearchResponse {
        if (type == "Movies") {
            return newMovieSearchResponse(
                ad,
                link,
                TvType.Movie,
            ) {
                this.posterUrl = posterLink
            }
        } else {
            return newTvSeriesSearchResponse(
                ad,
                link,
                TvType.TvSeries,
            ) {
                this.posterUrl = posterLink
            }
        }

    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        try {
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val encodedDoc = app.get(url).document
            val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
            val secureData =
                objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
            val converted = decryptResponse(secureData.toString())
            Log.d("SFX_DEBUG", "load Converted JSON: $converted")
            val root: Root = objectMapper.readValue(converted)
            val item = root.contentItem
            val orgTitle = item.originalTitle
            val cultTitle = item.cultureTitle.toString()
            val title =
                if (orgTitle == cultTitle || cultTitle.isEmpty()) orgTitle else "$orgTitle - $cultTitle"
            val poster = fixUrlNull(
                item.posterUrl?.replace(
                    "images-macellan-online.cdn.ampproject.org/i/s/",
                    ""
                )
            )
            val description = item.description
            val year = item.releaseYear
            val tags = item.categories?.split(",")
            val rating = item.imdbPoint
            val duration = item.totalMinutes
            val actors = root.relatedResults.getMovieCastsById?.result?.map {
                Actor(
                    it.name!!,
                    fixUrlNull(
                        it.castImage?.replace(
                            "images-macellan-online.cdn.ampproject.org/i/s/",
                            ""
                        )
                    )
                )
            }
            var trailer = ""
            if (root.relatedResults.getContentTrailers?.state == true && root.relatedResults.getContentTrailers.result?.size!! > 0) {
                Log.d("SFX", "getContentTrailers null değil")
                Log.d(
                    "SFX",
                    "getContentTrailers ->  ${root.relatedResults.getContentTrailers.result}"
                )
                trailer = root.relatedResults.getContentTrailers.result[0].rawUrl.toString()
            }

            if (root.relatedResults.getSerieSeasonAndEpisodes != null) {
                Log.d("SFX", "getSerieSeasonAndEpisodes null değil")
                Log.d(
                    "SFX",
                    "getSerieSeasonAndEpisodes ->  ${root.relatedResults.getSerieSeasonAndEpisodes}"
                )
                val eps = mutableListOf<Episode>()
                root.relatedResults.getSerieSeasonAndEpisodes.seasons?.forEach { it ->
                    val szn = it.seasonNo
                    Log.d("SFX", "Szn -> $szn")
                    val episodes = it.episodes
                    Log.d("SFX", "episodes -> $episodes")
                    episodes?.forEach {
                        eps.add(newEpisode(fixUrlNull(it.usedSlug)) {
                            this.name = it.epText
                            this.season = szn
                            this.episode = it.episodeNo
                        })
                    }
                    Log.d("SFX", "eps -> $eps")
                }
                return newTvSeriesLoadResponse(title!!, url, TvType.TvSeries, eps) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(rating)
                    addActors(actors)
                    addTrailer(trailer)
                }
            }

            return newMovieLoadResponse(title!!, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(SelcukFlixPlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return safeLoadLinks(SelcukFlixPlugin.appContext, this.name, data, callback) { safeCallback ->
            Log.d("SFX", "data » $data")
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val encodedDoc = app.get(data).document
            val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
            val secureData =
                objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
            val converted = decryptResponse(secureData.toString())
            Log.d("SFX_DEBUG", "loadLinks Converted JSON: $converted")
            val root: Root = objectMapper.readValue(converted)
            val iframes = mutableListOf<SourceItem>()
            val relatedResults = root.relatedResults
            if (data.contains("/dizi/")) {
                if (relatedResults.getEpisodeSources?.state == true) {
                    relatedResults.getEpisodeSources.result?.forEach { it ->
                        iframes.add(SourceItem(it.sourceContent.toString(), it.qualityName.toString()))
                    }
                }
            } else {
                if (relatedResults.getMoviePartsById?.state == true) {
                    val ids = mutableListOf<Int>()
                    objectMapper.readTree(converted).get("RelatedResults").get("getMoviePartsById")
                        .get("result").forEach { it ->
                            ids.add(it.get("id").asInt())
                        }
                    relatedResults.getMoviePartsById.result?.forEach { it ->
                        objectMapper.readTree(converted).get("RelatedResults")
                            .get("getMoviePartSourcesById_${it.id}")
                            .get("result").forEach { ifs ->
                                iframes.add(
                                    SourceItem(
                                        ifs.get("source_content").asText(),
                                        ifs.get("quality_name").asText()
                                    )
                                )
                            }
                    }
                }
            }
            Log.d("SFX", "iframes -> $iframes")
            iframes.forEach { it ->
                val iframe = fixUrlNull(Jsoup.parse(it.sourceContent).select("iframe").attr("src"))
                Log.d("SFX", "iframe » $iframe")
                loadExtractor(iframe!!, "${mainUrl}/", subtitleCallback, safeCallback)
            }
        }
    }
}