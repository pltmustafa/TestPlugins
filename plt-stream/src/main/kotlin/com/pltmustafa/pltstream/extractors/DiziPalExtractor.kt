package com.pltmustafa.pltstream.extractors

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.pltmustafa.pltstream.hosts.DizipalPlayer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DiziPalExtractor : SiteExtractor {
    override val name = "DiziPal"
    private val mainUrl = "https://dizipal1557.com"

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
            val searchUrl = "$mainUrl/bg/searchcontent"
            val payload = mapOf(
                "cKey" to "ca1d4a53d0f4761a949b85e51e18f096",
                "cValue" to "MTc3NTI1MTgwMDg3ODNkODBiMDM2MTk1YTkxMWU5ZTYyYjE4NzQyMjJlMzMwNjAxNGVjMWQzMzliNzY5NzFlZmViMzRhMGVmNjgwODU3MGIyZA==",
                "type" to "hepsi",
                "searchterm" to searchQuery
            )

            val headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            )

            val searchRes = app.post(
                searchUrl,
                headers = headers,
                data = payload,
                interceptor = interceptor
            ).text

            Log.d("plt-stream", "$name: Arama API Yanıtı: ${searchRes.take(200)}")

            val mapper = jacksonObjectMapper()
            val rootNode = mapper.readTree(searchRes)
            val resultArrayNode = rootNode.at("/data/result")

            if (resultArrayNode.isMissingNode || !resultArrayNode.isArray) {
                Log.d("plt-stream", "$name: Arama sonucu bulunamadı veya parse edilemedi.")
                return
            }

            var matchedSlug: String? = null

            resultArrayNode.forEach { item ->
                val objectName = item.get("object_name")?.asText()
                val originalTitle = item.get("original_title")?.asText()
                val cultureTitle = item.get("culture_title")?.asText()
                val itemImdbId = item.get("object_related_imdb_id")?.asText()
                
                val itemTitle = objectName ?: originalTitle ?: cultureTitle ?: return@forEach
                
                if (itemImdbId == imdbId || 
                    itemTitle.equals(searchQuery, ignoreCase = true) || 
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    
                    matchedSlug = item.get("used_slug")?.asText()
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    return@forEach
                }
            }

            if (matchedSlug == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            val epUrl = if (season != null && episode != null) {
                val seriesUrl = "$mainUrl/$matchedSlug"
                val seriesHtml = app.get(seriesUrl, interceptor = interceptor, headers = headers).document
                
                var foundEpUrl: String? = null
                val episodeElements = seriesHtml.select("div.relative.w-full.flex.items-start.gap-4")
                for (element in episodeElements) {
                    val linkElement = element.selectFirst("a[data-dizipal-pageloader]") ?: continue
                    val infoText = linkElement.selectFirst("div.text-white.text-sm.opacity-80")?.text()?.trim() ?: ""
                    
                    val epSeason = Regex("""(\d+)\.\s*Sezon""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                    val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                    
                    if (epSeason == season && epEpisode == episode) {
                        foundEpUrl = linkElement.attr("href")
                        break
                    }
                }
                
                if (foundEpUrl == null) {
                    Log.d("plt-stream", "$name: Bölüm URL'si sayfada bulunamadı.")
                    return
                }
                if (foundEpUrl.startsWith("/")) foundEpUrl = "$mainUrl$foundEpUrl"
                foundEpUrl
            } else {
                "$mainUrl/$matchedSlug"
            }

            Log.d("plt-stream", "$name: URL -> $epUrl")
            val epHtml = app.get(epUrl, interceptor = interceptor, headers = headers).document

            val encryptedText = epHtml.selectFirst("div[data-rm-k=true]")?.text() ?: ""
            var iframeUrl = if (encryptedText.isNotEmpty()) {
                decryptDizipalData(encryptedText)
            } else {
                epHtml.selectFirst("iframe")?.attr("src") ?: ""
            }

            if (iframeUrl.isNotEmpty()) {
                if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
                Log.d("plt-stream", "$name: iframe bulundu -> $iframeUrl")
                
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

                DizipalPlayer().getUrl(iframeUrl, epUrl, subtitleCallback, safeCallback)
            } else {
                Log.e("plt-stream", "$name: iframe bulunamadı.")
            }

        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Hex string çift uzunlukta olmalıdır" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun decryptDizipalData(rawJsonText: String): String {
        return try {
            val passphrase = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"

            val ctMatch = """"ciphertext"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val ivMatch = """"iv"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val saltMatch = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""

            val salt = saltMatch.decodeHex()
            val iv = ivMatch.decodeHex()
            val ciphertext = Base64.decode(ctMatch, Base64.DEFAULT)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
            val secretKey = factory.generateSecret(spec)
            val secret = SecretKeySpec(secretKey.encoded, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            var finalUrl = String(decryptedBytes, Charsets.UTF_8).replace("\\/", "/")

            if (finalUrl.startsWith("://")) finalUrl = "https$finalUrl"
            else if (finalUrl.startsWith("//")) finalUrl = "https:$finalUrl"
            else if (!finalUrl.startsWith("http")) finalUrl = "https://$finalUrl"

            finalUrl
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
