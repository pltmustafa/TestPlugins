package com.pltmustafa.pltstream.extractors

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class FullHDFilmizleseneExtractor : SiteExtractor {
    override val name = "FullHDFilmizlesene"
    private val mainUrl = "https://www.fullhdfilmizlesene.life"

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
            val document = app.get("$mainUrl/arama/$title").document
            val results = document.select("li.film")

            Log.d("plt-stream", "$name: ${results.size} sonuç bulundu")

            for (result in results) {
                val itemHref = result.selectFirst("a")?.attr("href")?.takeIf { url -> url.isNotBlank() } ?: continue
                
                val itemTitle = result.selectFirst("span.film-title")?.text()?.trim() ?: continue
                val itemYear = result.selectFirst("span.film-yil")?.text()?.trim()?.toIntOrNull()

                Log.d("plt-stream", "$name: İnceleniyor -> Başlık: $itemTitle, Yıl: $itemYear")

                val normalizedItemTitle = itemTitle.replace("&", "ve").replace(Regex("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ]"), "").lowercase()
                val normalizedSearchTitle = title.replace("&", "ve").replace(Regex("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ]"), "").lowercase()

                val titleMatch = normalizedItemTitle == normalizedSearchTitle
                val yearMatch = (year == null || itemYear == null || year == itemYear)

                if (titleMatch && yearMatch) {
                    Log.d("plt-stream", "$name: Eşleşme bulundu! -> $itemTitle ($itemHref)")
                    extractLinksFromPage(itemHref, subtitleCallback, callback)
                    return
                }
            }

            Log.d("plt-stream", "$name: Başlık ve yıl eşleşmesi bulunamadı")
        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private fun atob(s: String): String {
        return String(Base64.decode(s, Base64.DEFAULT))
    }

    private fun rtt(s: String): String {
        return s.map { c ->
            when (c) {
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                else -> c
            }
        }.joinToString("")
    }

    private suspend fun extractLinksFromPage(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("plt-stream", "$name: Link çıkarılıyor -> $url")
        val document = app.get(url).document

        val scriptElement = document.select("script").firstOrNull { it.data().isNotEmpty() && it.data().contains("scx") }
        val scriptContent = scriptElement?.data()?.trim() ?: return

        val scxData = Regex("scx = (.*?);").find(scriptContent)?.groupValues?.get(1) ?: return
        val scxMap: SCXData = jacksonObjectMapper().readValue(scxData)
        val keys = listOf("atom", "advid", "advidprox", "proton", "fast", "fastly", "tr", "en")

        for (key in keys) {
            val t = when (key) {
                "atom" -> scxMap.atom?.sx?.t
                "advid" -> scxMap.advid?.sx?.t
                "advidprox" -> scxMap.advidprox?.sx?.t
                "proton" -> scxMap.proton?.sx?.t
                "fast" -> scxMap.fast?.sx?.t
                "fastly" -> scxMap.fastly?.sx?.t
                "tr" -> scxMap.tr?.sx?.t
                "en" -> scxMap.en?.sx?.t
                else -> null
            }

            when (t) {
                is List<*> -> {
                    val links = t.filterIsInstance<String>().map { link -> atob(rtt(link)) }
                    for (videoUrl in links) {
                        val fixedUrl = if (videoUrl.startsWith("http")) videoUrl
                        else if (videoUrl.startsWith("//")) "https:$videoUrl"
                        else continue
                        Log.d("plt-stream", "$name: loadExtractor çağrılıyor -> $fixedUrl (key: $key)")
                        try {
                            loadExtractor(fixedUrl, "$mainUrl/", subtitleCallback) { link ->
                                callback.invoke(
                                    ExtractorLink(
                                        source = "$name - ${link.name}",
                                        name = "$name - ${link.name}",
                                        url = link.url,
                                        referer = link.referer,
                                        quality = link.quality,
                                        type = link.type,
                                        headers = link.headers,
                                        extractorData = link.extractorData
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("plt-stream", "$name extractor hatası: ${e.message}")
                        }
                    }
                }
                is Map<*, *> -> {
                    t.values.forEach { value ->
                        if (value is String) {
                            val videoUrl = atob(rtt(value))
                            val fixedUrl = if (videoUrl.startsWith("http")) videoUrl
                            else if (videoUrl.startsWith("//")) "https:$videoUrl"
                            else return@forEach
                            Log.d("plt-stream", "$name: loadExtractor çağrılıyor -> $fixedUrl (key: $key)")
                            try {
                                loadExtractor(fixedUrl, "$mainUrl/", subtitleCallback) { link ->
                                    callback.invoke(
                                        ExtractorLink(
                                            source = "$name - ${link.name}",
                                            name = "$name - ${link.name}",
                                            url = link.url,
                                            referer = link.referer,
                                            quality = link.quality,
                                            type = link.type,
                                            headers = link.headers,
                                            extractorData = link.extractorData
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("plt-stream", "$name extractor hatası: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SCXData(
        @JsonProperty("atom") val atom: AtomData? = null,
        @JsonProperty("advid") val advid: AtomData? = null,
        @JsonProperty("advidprox") val advidprox: AtomData? = null,
        @JsonProperty("proton") val proton: AtomData? = null,
        @JsonProperty("fast") val fast: AtomData? = null,
        @JsonProperty("fastly") val fastly: AtomData? = null,
        @JsonProperty("tr") val tr: AtomData? = null,
        @JsonProperty("en") val en: AtomData? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AtomData(
        @JsonProperty("sx") var sx: SXData
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SXData(
        @JsonProperty("t") var t: Any
    )
}
