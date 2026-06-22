@file:Suppress("DEPRECATION")
package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class DiziYouExtractor : ExtractorApi(), SiteExtractor {
    override val name = "DiziYou"
    override val mainUrl = "https://www.diziyou.one"
    override val requiresReferer = false

    private val cloudflareKiller by lazy { com.lagradost.cloudstream3.network.CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: com.lagradost.cloudstream3.network.CloudflareKiller) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(10 * 1024).string())

            if (response.code == 503 || doc.html().contains("Cloudflare", ignoreCase = true) || doc.selectFirst("meta[name='cloudflare']") != null || doc.html().contains("Just a moment", ignoreCase = true)) {
                response.close()
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    @Suppress("DEPRECATION")
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
        Log.d("plt-stream", "$name: Aranıyor -> $searchQuery")

        try {
            val searchUrl = "$mainUrl/wp-admin/admin-ajax.php"
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            )
            val payload = mapOf(
                "action" to "data_fetch",
                "keyword" to searchQuery
            )

            val searchRes = app.post(
                searchUrl,
                headers = headers,
                data = payload,
                interceptor = interceptor
            ).text

            val doc = Jsoup.parse(searchRes)
            val searchElements = doc.select("div#searchelement")
            
            var seriesUrl: String? = null
            
            for (el in searchElements) {
                val itemTitle = el.select("a").last()?.text()?.trim() ?: continue
                if (itemTitle.equals(searchQuery, ignoreCase = true) ||
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    seriesUrl = el.select("a").first()?.attr("href")
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    break
                }
            }

            if (seriesUrl == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            if (seriesUrl.startsWith("/")) seriesUrl = "$mainUrl$seriesUrl"

            // Get episode link from series page
            val seriesHtml = app.get(seriesUrl, interceptor = interceptor, headers = headers).document
            var epUrl: String? = null
            
            if (season != null && episode != null) {
                val bolumElements = seriesHtml.select("div.bolumust")
                for (el in bolumElements) {
                    val epName = el.selectFirst("div.baslik")?.ownText()?.trim() ?: continue
                    val epSeasonMatch = Regex("""(\d+)\.\s*Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epEpisodeMatch = Regex("""(\d+)\.\s*Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                    
                    if (epSeasonMatch == season && epEpisodeMatch == episode) {
                        epUrl = el.closest("a")?.attr("href")
                        break
                    }
                }
            } else {
                epUrl = seriesUrl // Fallback or Movie logic
            }

            if (epUrl == null) {
                Log.d("plt-stream", "$name: Bölüm URL'si bulunamadı")
                return
            }

            if (epUrl.startsWith("/")) epUrl = "$mainUrl$epUrl"
            Log.d("plt-stream", "$name: Bölüm Sayfası -> $epUrl")

            val epHtml = app.get(epUrl, interceptor = interceptor, headers = headers).document
            val itemId = epHtml.selectFirst("iframe#diziyouPlayer")?.attr("src")?.split("/")?.lastOrNull()?.substringBefore(".html")
            
            if (itemId == null) {
                Log.e("plt-stream", "$name: Gerekli itemId bulunamadı")
                return
            }

            val storageUrl = mainUrl.replace("www", "storage")

            epHtml.select("span.diziyouOption").forEach { opt ->
                val optId = opt.attr("id")

                if (optId == "turkceAltyazili") {
                    subtitleCallback.invoke(SubtitleFile("Türkçe", "$storageUrl/subtitles/$itemId/tr.vtt"))
                    callback.invoke(newExtractorLink(
                        source = "$name - Altyazılı",
                        name = "$name - Orijinal",
                        url = "$storageUrl/episodes/$itemId/play.m3u8",
                        type = INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                    })
                }

                if (optId == "ingilizceAltyazili") {
                    subtitleCallback.invoke(SubtitleFile("English", "$storageUrl/subtitles/$itemId/en.vtt"))
                    callback.invoke(newExtractorLink(
                        source = "$name - İng. Altyazılı",
                        name = "$name - Orijinal",
                        url = "$storageUrl/episodes/$itemId/play.m3u8",
                        type = INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                    })
                }

                if (optId == "turkceDublaj") {
                    callback.invoke(newExtractorLink(
                        source = "$name - Dublaj",
                        name = "$name - Dublaj",
                        url = "$storageUrl/episodes/${itemId}_tr/play.m3u8",
                        type = INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                    })
                }
            }
            
            Log.d("plt-stream", "$name: Tamamlandı")

        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }
}
