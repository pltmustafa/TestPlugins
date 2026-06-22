package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl

import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class FilmModuExtractor : SiteExtractor {
    override val name = "FilmModu"
    private val mainUrl = "https://www.filmmodu.one"

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
        if (season != null) return

        Log.d("plt-stream", "$name: Aranıyor -> $title (imdb: $imdbId)")

        try {
            val cleanTitle = title.replace(Regex("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ ]"), " ").replace(Regex("\\s+"), " ").trim()
            val query = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val document = app.get("$mainUrl/film-ara?term=$query").document
            val results = document.select("div.movie")

            Log.d("plt-stream", "$name: ${results.size} sonuç bulundu")

            for (result in results) {
                val itemHref = result.selectFirst("a")?.attr("href")?.takeIf { url -> url.isNotBlank() } ?: continue
                
                val originalName = result.selectFirst(".original-name")?.text()?.trim()
                val turkishName = result.selectFirst(".turkish-name")?.text()?.trim()
                val topText = result.selectFirst("p.top")?.text() ?: ""
                val itemYear = topText.substringBefore("-").trim().toIntOrNull()

                Log.d("plt-stream", "$name: İnceleniyor -> Orijinal: $originalName, TR: $turkishName, Yıl: $itemYear")

                val titleMatch = (originalName.equals(title, ignoreCase = true) || turkishName.equals(title, ignoreCase = true))
                val yearMatch = (year == null || itemYear == null || year == itemYear)

                if (titleMatch && yearMatch) {
                    Log.d("plt-stream", "$name: Eşleşme bulundu! -> $itemHref")
                    extractLinksFromPage(itemHref, subtitleCallback, callback)
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
        val document = app.get(url).document

        val alternates = document.select("div.alternates a")
        Log.d("plt-stream", "$name: Bulunan alternatif link sayısı: ${alternates.size}")

        alternates.forEach {
            val altLink = it.attr("href")?.takeIf { url -> url.isNotBlank() } ?: return@forEach
            val altName = it.text()
            if (altName == "Fragman") return@forEach

            Log.d("plt-stream", "$name: Alternatif işleniyor -> $altName ($altLink)")

            try {
                val altReq = app.get(altLink)
                val vidId = Regex("""var videoId = '(.*)'""").find(altReq.text)?.groupValues?.get(1)
                val vidType = Regex("""var videoType = '(.*)'""").find(altReq.text)?.groupValues?.get(1)

                Log.d("plt-stream", "$name: $altName için vidId: $vidId, vidType: $vidType")

                if (vidId == null || vidType == null) return@forEach

                val getSourceUrl = "$mainUrl/get-source?movie_id=$vidId&type=$vidType"
                Log.d("plt-stream", "$name: Kaynak isteniyor -> $getSourceUrl")

                val vidReq = app.get(getSourceUrl).parsedSafe<GetSource>()
                
                if (vidReq == null) {
                    Log.d("plt-stream", "$name: $altName için kaynak json parse edilemedi veya boş!")
                    return@forEach
                }

                Log.d("plt-stream", "$name: $altName için sources: ${vidReq.sources?.size}, subtitle: ${vidReq.subtitle}")

                if (vidReq.subtitle != null) {
                    subtitleCallback.invoke(SubtitleFile(lang = "Türkçe", url = fixUrl(vidReq.subtitle)))
                }

                if (vidReq.sources.isNullOrEmpty()) return@forEach

                vidReq.sources.forEach { source ->
                    val qualityStr = source.label ?: ""
                    val parsedQuality = com.lagradost.cloudstream3.utils.getQualityFromName(qualityStr)
                    
                    Log.d("plt-stream", "$name: Link bulundu -> ${source.src} ($qualityStr)")
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $altName",
                            name = "$name - $altName",
                            url = fixUrl(source.src),
                            type = ExtractorLinkType.M3U8
                        ) {
                            headers = mapOf("Referer" to "$mainUrl/")
                            quality = parsedQuality
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("plt-stream", "$name link hatası ($altName): ${e.message}")
            }
        }
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url
        else if (url.startsWith("//")) "https:$url"
        else "$mainUrl/$url"
    }

    data class GetSource(
        @JsonProperty("sources") val sources: List<Source>? = null,
        @JsonProperty("subtitle") val subtitle: String? = null
    )

    data class Source(
        @JsonProperty("src") val src: String = "",
        @JsonProperty("label") val label: String? = null
    )
}
