package com.pltmustafa.pltstream.extractors

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.pltmustafa.pltstream.utils.CryptoJS
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder

data class DiziBoxSearchResponse(
    @JsonProperty("searchTerms") val searchTerms: String?,
    @JsonProperty("results") val results: List<DiziBoxResult>?
)

data class DiziBoxResult(
    @JsonProperty("post_title") val post_title: String?,
    @JsonProperty("permalink") val permalink: String?
)

class DiziBoxExtractor : SiteExtractor {
    override val name = "DiziBox"
    private val mainUrl = "https://www.dizibox.live"

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(10 * 1024).string())

            if (response.code == 503 || doc.selectFirst("meta[name='cloudflare']") != null) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    private val defaultCookies = mapOf(
        "LockUser" to "true",
        "isTrustedUser" to "true",
        "dbxu" to "1743289650198"
    )

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
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val searchUrl = "$mainUrl/wp-admin/admin-ajax.php?s=$encodedQuery&action=dwls_search"

            val response = app.get(
                searchUrl,
                cookies = defaultCookies,
                interceptor = interceptor,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:152.0) Gecko/20100101 Firefox/152.0",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"
                )
            ).text

            val json = AppUtils.tryParseJson<DiziBoxSearchResponse>(response)
            val results = json?.results ?: return
            
            var seriesUrl: String? = null
            
            for (item in results) {
                val itemTitle = item.post_title ?: continue
                if (itemTitle.equals(searchQuery, ignoreCase = true) || 
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    seriesUrl = item.permalink
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    break
                }
            }

            if (seriesUrl == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            // DiziBox genellikle sadece diziler için kullanıldığı için bölüm arayacağız
            if (season != null && episode != null) {
                val seriesDoc = app.get(seriesUrl, cookies = defaultCookies, interceptor = interceptor).document
                var episodeUrl: String? = null
                
                val seasonTabs = seriesDoc.select("div#seasons-list a")
                for (tab in seasonTabs) {
                    val tabText = tab.text()
                    if (tabText.contains("$season. Sezon", ignoreCase = true)) {
                        val tabHref = tab.attr("href")
                        if (tabHref.isNotBlank()) {
                            val seasonDoc = app.get(tabHref, cookies = defaultCookies, interceptor = interceptor).document
                            val episodes = seasonDoc.select("article.grid-box")
                            for (ep in episodes) {
                                val epTitle = ep.selectFirst("div.post-title a")?.text()?.trim() ?: continue
                                val epEpRegex = Regex("""(\d+)\. ?Bölüm""", RegexOption.IGNORE_CASE).find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                                if (epEpRegex == episode) {
                                    episodeUrl = ep.selectFirst("div.post-title a")?.attr("href")
                                    break
                                }
                            }
                        }
                        break
                    }
                }

                if (episodeUrl == null) {
                    val slug = seriesUrl.trimEnd('/').substringAfterLast('/')
                    val guessUrl = "$mainUrl/$slug-$season-sezon-$episode-bolum-izle/"
                    val guessRes = app.get(guessUrl, cookies = defaultCookies, interceptor = interceptor)
                    if (guessRes.code == 200) {
                        episodeUrl = guessUrl
                    } else {
                        Log.d("plt-stream", "$name: Bölüm URL'si bulunamadı!")
                        return
                    }
                }

                Log.d("plt-stream", "$name: Bölüm URL -> $episodeUrl")
                
                val epDoc = app.get(episodeUrl!!, cookies = defaultCookies, interceptor = interceptor).document
                
                var iframe = epDoc.selectFirst("div#video-area iframe")?.attr("src")
                if (iframe != null) {
                    iframeDecode(episodeUrl, iframe, subtitleCallback, callback)
                }
                
                epDoc.select("div.video-toolbar option[value]").forEach {
                    val altLink = it.attr("value")
                    val subDoc  = app.get(altLink, cookies = defaultCookies, interceptor = interceptor).document
                    val altIframe = subDoc.selectFirst("div#video-area iframe")?.attr("src")
                    if (altIframe != null) {
                        iframeDecode(episodeUrl, altIframe, subtitleCallback, callback)
                    }
                }
            } else {
                // Eğer Film ise
                val epDoc = app.get(seriesUrl, cookies = defaultCookies, interceptor = interceptor).document
                var iframe = epDoc.selectFirst("div#video-area iframe")?.attr("src")
                if (iframe != null) {
                    iframeDecode(seriesUrl, iframe, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private suspend fun iframeDecode(data: String, iframe: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        var iframeUrl = iframe

        val safeCallback = { link: ExtractorLink ->
            callback.invoke(
                ExtractorLink(
                    source = "${this.name} - ${link.source}",
                    name = "${this.name} - ${link.name}",
                    url = link.url,
                    referer = link.referer,
                    quality = link.quality,
                    type = link.type,
                    headers = link.headers,
                    extractorData = link.extractorData
                )
            )
        }

        if (iframeUrl.contains("/player/king/king.php")) {
            iframeUrl = iframeUrl.replace("king.php?v=", "king.php?wmode=opaque&v=")
            val subDoc = app.get(
                iframeUrl,
                referer     = data,
                cookies     = defaultCookies,
                interceptor = interceptor
            ).document
            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            val iDoc          = app.get(subFrame, referer="$mainUrl/").text
            val cryptData     = Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(iDoc)?.groupValues?.get(1) ?: return false
            val cryptPass     = Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: return false
            val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
            val decryptedDoc  = Jsoup.parse(decryptedData)
            val vidUrl        = Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1) ?: return false

            safeCallback.invoke(
                newExtractorLink(
                    source = "King",
                    name = "King",
                    url = vidUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    headers = mapOf("Referer" to vidUrl)
                    quality = getQualityFromName("4k")
                }
            )

        } else if (iframeUrl.contains("/player/moly/moly.php")) {
            iframeUrl = iframeUrl.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
            var subDoc = app.get(
                iframeUrl,
                referer     = data,
                cookies     = defaultCookies,
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false
            loadExtractor(subFrame, "$mainUrl/", subtitleCallback, safeCallback)

        } else if (iframeUrl.contains("/player/haydi.php")) {
            iframeUrl = iframeUrl.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
            var subDoc = app.get(
                iframeUrl,
                referer     = data,
                cookies     = defaultCookies,
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false
            loadExtractor(subFrame, "$mainUrl/", subtitleCallback, safeCallback)
        }

        return true
    }
}
