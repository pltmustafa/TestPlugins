package com.pltmustafa.pltstream.extractors

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DizillaSearchResponse(
    @JsonProperty("response") val response: String?
)

data class DizillaSearchResultItem(
    @JsonProperty("object_id") val object_id: Int?,
    @JsonProperty("object_related_imdb_id") val object_related_imdb_id: String?,
    @JsonProperty("used_slug") val used_slug: String?,
    @JsonProperty("object_name") val object_name: String?,
    @JsonProperty("original_title") val original_title: String?,
    @JsonProperty("culture_title") val culture_title: String?
)

data class DizillaSearchData(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<DizillaSearchResultItem>?
)

class DizillaExtractor : SiteExtractor {
    override val name = "Dizilla"
    private val mainUrl = "https://dizillahd.com"
    private val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(10 * 1024).string())

            if (response.code == 503 || doc.html().contains("Cloudflare", ignoreCase = true) || doc.selectFirst("meta[name='cloudflare']") != null || doc.html().contains("Just a moment", ignoreCase = true)) {
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
            val searchUrl = "$mainUrl/api/bg/searchContent?searchterm=${URLEncoder.encode(searchQuery, "UTF-8")}"
            val searchRes = app.post(
                searchUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                    "Accept" to "application/json, text/plain, */*",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "$mainUrl/"
                ),
                interceptor = interceptor
            ).text

            val jsonResponse = AppUtils.tryParseJson<DizillaSearchResponse>(searchRes)
            val encryptedBlob = jsonResponse?.response ?: return

            val decryptedJson = decryptDizillaResponse(encryptedBlob) ?: return
            
            val fixedJson = if (!decryptedJson.startsWith("{") && decryptedJson.contains("\"essage\"")) {
                "{m\"$decryptedJson"
            } else {
                decryptedJson
            }

            val searchData = AppUtils.tryParseJson<DizillaSearchData>(fixedJson)
            val results = searchData?.result ?: return
            
            var matchedSlug: String? = null
            
            for (item in results) {
                val itemTitle = item.object_name ?: item.original_title ?: item.culture_title ?: continue
                if (item.object_related_imdb_id == imdbId || 
                    itemTitle.equals(searchQuery, ignoreCase = true) || 
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    matchedSlug = item.used_slug
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    break
                }
            }

            if (matchedSlug == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            val bareSlug = matchedSlug.replace("dizi/", "").replace("film/", "")
            val epUrl = if (season != null && episode != null) {
                "$mainUrl/$bareSlug-$season-sezon-$episode-bolum"
            } else {
                "$mainUrl/$bareSlug"
            }

            Log.d("plt-stream", "$name: URL -> $epUrl")
            
            val epHtml = app.get(epUrl, interceptor = interceptor).text
            
            val secureDataMatch = Regex("""\"secureData\":\"([^\"]+)\"""").find(epHtml)
            val secureData = secureDataMatch?.groupValues?.get(1)
            
            if (secureData == null) {
                Log.d("plt-stream", "$name: secureData bulunamadı")
                return
            }
            
            val decodedData = decryptDizillaResponse(secureData) ?: return
            
            val contentRegex = Regex(""""source_content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            val matches = contentRegex.findAll(decodedData)
            
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
            
            val uniqueIframes = mutableSetOf<String>()
            
            for (match in matches) {
                val rawHtml = match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\")
                
                val iframeUrlMatch = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(rawHtml)
                var iframeUrl = iframeUrlMatch?.groupValues?.get(1)
                
                if (iframeUrl != null) {
                    if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
                    
                    if (uniqueIframes.add(iframeUrl)) {
                        Log.d("plt-stream", "$name: iframe bulundu -> $iframeUrl")
                        
                        if (iframeUrl.contains("pichive.online")) {
                            Log.d("plt-stream", "$name: Özel ContentX (Pichive) çözücü kullanılıyor")
                            com.pltmustafa.pltstream.hosts.ContentX().getUrl(iframeUrl, "$mainUrl/", subtitleCallback, safeCallback)
                        } else {
                            loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, safeCallback)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private fun decryptDizillaResponse(response: String): String? {
        try {
            val encryptedData = Base64.decode(response, Base64.DEFAULT)
            val ivSpec = IvParameterSpec(ByteArray(16))
            val keySpec = SecretKeySpec(privateAESKey.toByteArray(Charsets.UTF_8), "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            return String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("plt-stream", "Dizilla Decryption error: ${e.message}")
            return null
        }
    }
}
