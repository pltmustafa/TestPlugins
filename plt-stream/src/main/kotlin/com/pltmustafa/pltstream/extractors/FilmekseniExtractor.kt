package com.pltmustafa.pltstream.extractors

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class FilmekseniExtractor : SiteExtractor {
    override val name = "FilmEkseni"
    private val mainUrl = "https://filmekseni.top"

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
        if (title == null) return

        Log.d("plt-stream", "$name: Aranıyor -> $title (imdb: $imdbId)")

        try {
            val response = app.get(
                "$mainUrl/api/search?q=$title",
                headers = mapOf("Accept" to "application/json")
            ).parsedSafe<ApiSearchResponse>()

            val results = response?.data ?: return
            Log.d("plt-stream", "$name: ${results.size} sonuç bulundu")

            for (item in results) {
                val slug = item.slug ?: continue
                val itemTitle = item.title ?: continue
                val itemYear = item.releaseYear?.toIntOrNull()
                val isSeries = item.type?.contains("Movie", true) != true

                val cleanSlug = slug.removePrefix("dizi/").removePrefix("film/")
                val itemUrl = if (isSeries) "$mainUrl/dizi/$cleanSlug/" else "$mainUrl/$cleanSlug/"

                Log.d("plt-stream", "$name: İnceleniyor -> Başlık: $itemTitle, Yıl: $itemYear")

                // 've' ile '&' değişimleri vb. için basit bir kontrol
                val normalizedItemTitle = itemTitle.replace("&", "ve").replace(Regex("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ]"), "").lowercase()
                val normalizedSearchTitle = title.replace("&", "ve").replace(Regex("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ]"), "").lowercase()

                val titleMatch = normalizedItemTitle == normalizedSearchTitle
                val yearMatch = (year == null || itemYear == null || year == itemYear)

                if (titleMatch && yearMatch) {
                    Log.d("plt-stream", "$name: Eşleşme bulundu! -> $itemTitle ($itemUrl)")

                    val targetUrl = if (isSeries && season != null && episode != null) {
                        val pageDoc = app.get(itemUrl).document
                        val epLink = pageDoc.select("a[href*='/sezon-']").firstOrNull { link ->
                            val href = link.attr("href")
                            val sMatch = Regex("""/sezon-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            val eMatch = Regex("""/bolum-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            sMatch == season && eMatch == episode
                        }?.attr("href")
                        epLink ?: itemUrl
                    } else {
                        itemUrl
                    }

                    extractLinksFromPage(targetUrl, subtitleCallback, callback)
                    return
                }
            }

            Log.d("plt-stream", "$name: Başlık ve yıl eşleşmesi bulunamadı")
        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private suspend fun extractLinksFromPage(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("plt-stream", "$name: Link çıkarılıyor -> $url")
        val html = app.get(url).document.outerHtml()

        val jsonStrMatch = Regex("""JSON\.parse\('([^']*)'\)""").find(html)
        if (jsonStrMatch == null) {
            Log.d("plt-stream", "$name: JSON.parse bloğu bulunamadı")
            return
        }

        val rawJson = jsonStrMatch.groupValues[1].replace("\\u0022", "\"").replace("\\\\", "\\")
        val objects = rawJson.split("},{")

        for (obj in objects) {
            val link = Regex(""""link":"([^"]*)"""").find(obj)?.groupValues?.get(1)
            val template = Regex(""""template":"([^"]*)"""").find(obj)?.groupValues?.get(1)

            if (link != null && template != null) {
                try {
                    val decodedTemplate = String(Base64.decode(template, Base64.DEFAULT))
                    val iframeSrcMatch = Regex("""data-src=\"([^\"]+)\"""").find(decodedTemplate)
                        ?: Regex("""src=\"([^\"]+)\"""").find(decodedTemplate)

                    if (iframeSrcMatch != null) {
                        var iframeUrl = iframeSrcMatch.groupValues[1].replace("{url}", link)
                        if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"

                        val iframeRes = app.get(iframeUrl, referer = url)
                        val iframeDoc = iframeRes.document.outerHtml()
                        val parsedDomain = Regex("""(https?://[^/]+)""").find(iframeRes.url)?.groupValues?.get(1) ?: "https://vidload.top"

                        val m3u8Match = Regex("""file:\s*['"](.*?.m3u8)['"]""").find(iframeDoc)
                        if (m3u8Match != null) {
                            val m3u8Url = if (m3u8Match.groupValues[1].startsWith("http")) m3u8Match.groupValues[1] else parsedDomain + m3u8Match.groupValues[1]

                            callback.invoke(
                                newExtractorLink(
                                    source = "$name - Vidload",
                                    name = "$name - Vidload",
                                    url = m3u8Url,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    headers = mapOf("Referer" to iframeRes.url)
                                    quality = Qualities.Unknown.value
                                }
                            )

                            Log.d("plt-stream", "$name: M3U8 eklendi -> $m3u8Url")

                            val blocks = Regex("""\{([^}]*?\.(?:vtt|srt)[^}]*?)\}""").findAll(iframeDoc)
                            blocks.forEach { blockMatch ->
                                val block = blockMatch.groupValues[1]
                                val fileMatch = Regex(""""?file"?\s*:\s*['"](.*?\.(?:vtt|srt))['"]""").find(block)
                                val labelMatch = Regex(""""?label"?\s*:\s*['"](.*?)['"]""").find(block)
                                
                                if (fileMatch != null) {
                                    val trackUrl = fileMatch.groupValues[1]
                                    val subUrl = if (trackUrl.startsWith("http")) trackUrl else parsedDomain + trackUrl
                                    val lang = labelMatch?.groupValues?.get(1) ?: "Türkçe"
                                    
                                    Log.d("plt-stream", "$name: Altyazı bulundu -> $lang - $subUrl")
                                    subtitleCallback.invoke(SubtitleFile(lang, subUrl))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("plt-stream", "$name iframe hatası: ${e.message}")
                }
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
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("releaseYear") val releaseYear: String? = null
    )
}
