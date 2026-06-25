package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.pltmustafa.pltstream.utils.CryptoJS
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder

data class YabanciDiziSearchResponse(
    @JsonProperty("success") val success: Int?,
    @JsonProperty("data") val data: YabanciDiziSearchData?
)

data class YabanciDiziSearchData(
    @JsonProperty("result") val result: List<YabanciDiziSearchResultItem>?
)

data class YabanciDiziSearchResultItem(
    @JsonProperty("s_type") val s_type: String?,
    @JsonProperty("s_link") val s_link: String?,
    @JsonProperty("s_name") val s_name: String?,
    @JsonProperty("s_year") val s_year: String?
)

data class YabanciDiziAjaxResponse(
    @JsonProperty("data") val data: String?
)

class YabanciDiziExtractor : SiteExtractor {
    override val name = "YabanciDizi"
    private val mainUrl = "https://yabancidizi.life"

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Güvenlik taramasından geçiriliyorsunuz. Lütfen bekleyiniz..") || doc.html().contains("Cloudflare", ignoreCase = true) || doc.html().contains("Just a moment", ignoreCase = true)) {
                response.close()
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
        Log.d("plt-stream", "$name: Aranıyor -> $searchQuery")

        try {
            val searchUrl = "$mainUrl/search?qr=${URLEncoder.encode(searchQuery, "UTF-8")}"
            val searchRes = app.post(
                searchUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "$mainUrl/"
                ),
                interceptor = interceptor
            ).text

            val jsonResponse = AppUtils.tryParseJson<YabanciDiziSearchResponse>(searchRes)
            val results = jsonResponse?.data?.result ?: return
            
            var matchedSlug: String? = null
            var isMovie = false
            
            for (item in results) {
                val itemTitle = item.s_name ?: continue
                if (itemTitle.equals(searchQuery, ignoreCase = true) || 
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    matchedSlug = item.s_link
                    isMovie = item.s_type == "1"
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    break
                }
            }

            if (matchedSlug == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            var epUrl = if (isMovie) "$mainUrl/film/$matchedSlug" else "$mainUrl/dizi/$matchedSlug"
            
            if (!isMovie && season != null && episode != null) {
                val seriesHtml = app.get(epUrl, interceptor = interceptor).document
                var foundEpUrl: String? = null
                
                seriesHtml.select("div.tabular-content").forEach { tab ->
                    val epSeason = tab.parent()?.attr("data-season")?.toIntOrNull()
                    if (epSeason == season) {
                        tab.select("div.item").forEach { episodeElement ->
                            val epText = episodeElement.selectFirst("div.content span")?.text() ?: episodeElement.text()
                            val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                            if (epNum == episode) {
                                foundEpUrl = episodeElement.selectFirst("h6 a")?.attr("href")
                            }
                        }
                    }
                }
                
                if (foundEpUrl != null) {
                    epUrl = if (foundEpUrl!!.startsWith("http")) foundEpUrl!! 
                            else if (foundEpUrl!!.startsWith("/")) "$mainUrl$foundEpUrl" 
                            else "$mainUrl/$foundEpUrl"
                } else {
                    epUrl = "$mainUrl/dizi/$matchedSlug/sezon-$season/bolum-$episode"
                }
            }

            Log.d("plt-stream", "$name: Bölüm Sayfası -> $epUrl")
            
            val document = app.get(epUrl, interceptor = interceptor).document
            val timestampMillis = (System.currentTimeMillis() - 50000)
            
            val safeCallback = { link: ExtractorLink ->
                callback.invoke(
                    ExtractorLink(
                        source = "$name - ${link.source}",
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
            
            document.select("div#series-tabs a").forEach { tab ->
                val dataEid = tab.attr("data-eid")
                if (!dataEid.isNullOrEmpty()) {
                    val dataType = tab.attr("data-type")
                    val dilAd = if (dataType == "2") "Dublaj" else "Altyazı"
                    try {
                        val encodedEid = URLEncoder.encode(dataEid, "UTF-8")
                        val ajaxResp = app.post(
                            "$mainUrl/ajax/service", 
                            referer = epUrl, 
                            headers = mapOf(
                                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                                "Accept" to "application/json, text/javascript, */*; q=0.01", 
                                "Cookie" to "udys=$timestampMillis",
                                "X-Requested-With" to "XMLHttpRequest"
                            ),
                            data = mapOf("lang" to dataType, "episode" to encodedEid, "type" to "langTab"), 
                            interceptor = interceptor
                        ).text
                        
                        val parsedAjax = AppUtils.tryParseJson<YabanciDiziAjaxResponse>(ajaxResp)
                        if (parsedAjax?.data != null) {
                            val doca = Jsoup.parse(parsedAjax.data)
                            doca.select("div.item").forEach { item ->
                                val hostName = item.text()
                                val dataLink = item.attr("data-link")
                                
                                try {
                                    if (hostName.contains("Mac")) {
                                        val macUrl = "${mainUrl}/api/drive/" + dataLink.replace("/", "_").replace("+", "-")
                                        val mac = app.get(macUrl, referer = "$mainUrl/", headers = mapOf("Cookie" to "udys=$timestampMillis"), interceptor = interceptor).document
                                        var subFrame = mac.selectFirst("iframe")?.attr("src") ?: ""
                                        
                                        if (subFrame.isEmpty()) {
                                            val timestampInSeconds = System.currentTimeMillis() / 1000
                                            val drivesUrl = "${mainUrl}/api/drives/" + dataLink.replace("/", "_").replace("+", "-") + "?t=$timestampInSeconds"
                                            val drives = app.get(drivesUrl, referer = "${mainUrl}/api/drives/" + dataLink.replace("/", "_").replace("+", "-"), headers = mapOf("Cookie" to "udys=$timestampMillis"), interceptor = interceptor).document
                                            subFrame = drives.selectFirst("iframe")?.attr("src") ?: ""
                                        }
                                        
                                        if (subFrame.isNotEmpty()) {
                                            loadMac(subFrame, safeCallback, dilAd)
                                        }
                                    } else if (hostName.contains("VidMoly")) {
                                        val vdm = app.get("${mainUrl}/api/moly/" + dataLink.replace("/", "_").replace("+", "-"), referer = "$mainUrl/", headers = mapOf("Cookie" to "udys=$timestampMillis"), interceptor = interceptor).document
                                        val subFrame = vdm.selectFirst("iframe")?.attr("src") ?: ""
                                        if (subFrame.isNotEmpty()) {
                                            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, safeCallback)
                                        }
                                    } else if (hostName.contains("Okru")) {
                                        val okr = app.get("${mainUrl}/api/ruplay/" + dataLink.replace("/", "_").replace("+", "-"), referer = "$mainUrl/", headers = mapOf("Cookie" to "udys=$timestampMillis"), interceptor = interceptor).document
                                        val subFrame = okr.selectFirst("iframe")?.attr("src") ?: ""
                                        if (subFrame.isNotEmpty()) {
                                            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, safeCallback)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("plt-stream", "$name: Host $hostName error: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("plt-stream", "$name: Tab $dataEid error: ${e.message}")
                    }
                }
            }
            
            Log.d("plt-stream", "$name: Tamamlandı")
        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private suspend fun loadMac(subFrame: String, callback: (ExtractorLink) -> Unit, dilAd: String) {
        try {
            val iDoc = app.get(
                subFrame, referer = "${mainUrl}/",
                headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"),
                interceptor = interceptor
            ).text
            
            val cryptData = Regex("""CryptoJS\.AES\.decrypt\("(.*)",""").find(iDoc)?.groupValues?.get(1) ?: ""
            val cryptPass = Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: ""
            
            if (cryptData.isEmpty() || cryptPass.isEmpty()) return

            val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
            val decryptedDoc = Jsoup.parse(decryptedData)
            val vidUrl = Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1) ?: ""
            
            if (vidUrl.isEmpty()) return

            callback.invoke(
                ExtractorLink(
                    source = dilAd,
                    name = dilAd,
                    url = vidUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                        "Referer" to mainUrl
                    ),
                    extractorData = null
                )
            )

            val aa = app.get(
                vidUrl, referer = "$mainUrl/", headers =
                mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"),
                interceptor = interceptor
            ).document.body().text()
            
            val regex = """#EXT-X-STREAM-INF:.*?RESOLUTION=([^\s,]+).*?(https?://[^\s]+)(?:\s|$)""".toRegex()
            regex.findAll(aa).forEach { matchResult ->
                val resolution = matchResult.groupValues[1]
                val link = matchResult.groupValues[2]
                
                callback.invoke(
                    ExtractorLink(
                        source = "$dilAd - $resolution",
                        name = "$dilAd - $resolution",
                        url = link,
                        referer = vidUrl,
                        quality = getQualityFromName(resolution),
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                            "Referer" to vidUrl
                        ),
                        extractorData = null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("plt-stream", "$name loadMac Hata: ${e.message}")
        }
    }
}
