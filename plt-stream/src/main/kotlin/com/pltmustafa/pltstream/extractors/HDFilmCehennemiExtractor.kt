package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class HDFilmCehennemiExtractor : SiteExtractor {
    override val name = "HDFilmCehennemi"
    private val mainUrl = "https://www.hdfilmcehennemi.nl"

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CFInterceptor(cloudflareKiller) }

    class CFInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
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

    override suspend fun extract(
        tmdbId: String?,
        imdbId: String?,
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchQuery = title ?: return
        val sourceName = "HDFilmCehennemi"

        Log.d("plt-stream", "$sourceName: Aranıyor -> $searchQuery")

        try {
            val searchResponse = app.get(
                "$mainUrl/search?q=$searchQuery",
                headers = mapOf("X-Requested-With" to "fetch"),
                interceptor = interceptor
            ).text

            val json = AppUtils.tryParseJson<HDFilmSearchResponse>(searchResponse) ?: return
            val results = json.results ?: return

            Log.d("plt-stream", "$sourceName: ${results.size} sonuç bulundu")

            for (item in results) {
                val doc = Jsoup.parse(item)
                val itemTitle = doc.selectFirst("h4.title")?.text()?.trim() ?: continue
                val itemHref = doc.selectFirst("a")?.attr("href") ?: continue

                val pageResponse = app.get(itemHref, interceptor = interceptor).document
                val pageImdb = pageResponse.select("div.post-info-imdb a")
                    .firstOrNull()?.attr("href")
                    ?.substringAfter("title/")
                    ?.substringBefore("/")

                Log.d("plt-stream", "$sourceName: Sayfa IMDb -> $pageImdb vs Aranan IMDb -> $imdbId")

                if (imdbId != null && pageImdb != null && pageImdb == imdbId) {
                    Log.d("plt-stream", "$sourceName: IMDb eşleşti! -> $itemTitle ($itemHref)")

                    val isTv = pageResponse.select("div.seasons").isNotEmpty()

                    val targetUrl = if (isTv && season != null && episode != null) {
                        val epLink = pageResponse.select("div.seasons-tab-content a").firstOrNull { link ->
                            val epName = link.selectFirst("h4")?.text()?.trim() ?: return@firstOrNull false
                            val epNum = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                            val sNum = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                            sNum == season && epNum == episode
                        }?.attr("href")
                        epLink ?: itemHref
                    } else {
                        itemHref
                    }

                    extractLinksFromPage(targetUrl, sourceName, subtitleCallback, callback)
                    return
                }
            }

            Log.d("plt-stream", "$sourceName: Başlık ve yıl eşleşmesi bulunamadı")

        } catch (e: Exception) {
            Log.e("plt-stream", "$sourceName hata: ${e.message}")
        }
    }

    private suspend fun extractLinksFromPage(
        url: String,
        sourceName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("plt-stream", "$sourceName: Link çıkarılıyor -> $url")
        val document = app.get(url, interceptor = interceptor).document

        val altLinks = document.select("div.alternative-links")
        Log.d("plt-stream", "$sourceName: ${altLinks.size} alternatif link grubu bulundu")

        altLinks.map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            val buttons = element.select("button.alternative-link")

            buttons.map { button ->
                button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
            }.forEach { (source, videoID) ->
                Log.d("plt-stream", "$sourceName: İşleniyor -> $source, videoID: $videoID")
                try {
                    val apiGet = app.get(
                        "$mainUrl/video/$videoID/", interceptor = interceptor,
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "X-Requested-With" to "fetch"
                        ),
                        referer = url
                    ).text

                    var iframe = Regex("""data-src=\\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)?.replace("\\", "")

                    if (iframe == null) return@forEach

                    if (iframe.contains("rapidrame")) {
                        iframe = "$mainUrl/rplayer/" + iframe.substringAfter("?rapidrame_id=")
                    } else if (iframe.contains("mobi")) {
                        val iframeDoc = Jsoup.parse(apiGet)
                        iframe = iframeDoc.selectFirst("iframe")?.attr("data-src") ?: return@forEach
                        if (!iframe.startsWith("http")) iframe = "$mainUrl$iframe"
                    }

                    invokeLocalSource(source, iframe, subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("plt-stream", "$sourceName link hatası ($source): ${e.message}")
                }
            }
        }
    }

    private suspend fun invokeLocalSource(
        source: String,
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("plt-stream", "invokeLocalSource -> $url")
        val script = app.get(url, referer = "$mainUrl/", interceptor = interceptor)
            .document.select("script")
            .find { it.data().contains("sources:") }?.data() ?: return

        val videoData = getAndUnpack(script).substringAfter("file_link=\"").substringBefore("\";")

        val lastUrl = if (videoData.contains("dc_hello(\"")) {
            val base64Input = videoData.substringAfter("dc_hello(\"").substringBefore("\");")
            dcHello(base64Input)
        } else {
            val unmixed = decryptNewFormat(videoData)
            extractFinalUrl(unmixed)
        }

        if (lastUrl.isBlank()) return

        val subData = script.substringAfter("tracks: [").substringBefore("]")
        AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions" }?.map {
            val subtitleUrl = "${mainUrl}${it.file}/"
            subtitleCallback(SubtitleFile(it.language.toString(), subtitleUrl))
        }

        callback.invoke(
            newExtractorLink(
                source = "${this.name} - $source",
                name = "${this.name} - $source",
                url = lastUrl,
                type = ExtractorLinkType.M3U8
            ) {
                headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                quality = Qualities.Unknown.value
            }
        )
    }

    private fun extractFinalUrl(decrypted: String): String {
        val hdchLink = if (decrypted.contains("+")) {
            decrypted.substringAfterLast("+")
        } else if (decrypted.contains(" ")) {
            decrypted.substringAfterLast(" ")
        } else if (decrypted.contains("|")) {
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
            return unmix
        } catch (e: Exception) {
            return ""
        }
    }

    private fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)
        return extractFinalUrl(decodedTwice)
    }

    data class HDFilmSearchResponse(
        @JsonProperty("results") val results: List<String>?
    )

    data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )
}
