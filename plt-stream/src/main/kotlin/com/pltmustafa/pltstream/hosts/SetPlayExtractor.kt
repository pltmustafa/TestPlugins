package com.pltmustafa.pltstream.hosts

import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

open class SetPlay : ExtractorApi() {
    override val name            = "SetPlay"
    override val mainUrl         = "https://setplay.shop"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val iSource = app.get(url, referer = referer).text
        Log.d("STF_Extractor", "SetPlay URL: $url | Referer: $referer")
        Log.d("STF_Extractor", "iSource length: ${iSource.length}")

        val jsonString = Regex("""FirePlayer\([^,]+,\s*(\{.*?\})\s*,\s*(?:true|false)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(iSource)?.groupValues?.get(1)
            
        if (jsonString == null) {
            Log.e("STF_Extractor", "Regex failed. iSource preview: ${iSource.take(500)}")
            throw ErrorLoadingException("Player konfigurasyonu bulunamadı")
        }
        
        Log.d("STF_Extractor", "Found jsonString: $jsonString")

        val json = JSONObject(jsonString)

        val videoServer = json.optString("videoServer", "1")
        val videoUrl = json.optString("videoUrl", "").replace("\\/", "/")

        val uri = Uri.parse(url)
        val partKey = uri.getQueryParameter("partKey") ?: ""
        
        val suffix = when {
            partKey.contains("turkcedublaj", ignoreCase = true) -> "Dublaj"
            partKey.contains("turkcealtyazi", ignoreCase = true) -> "Altyazı"
            partKey.isNotEmpty() -> partKey
            else -> {
                val title = json.optString("title", "Bilinmeyen")
                title.substringAfterLast(".", "Bilinmeyen")
            }
        }

        val hostList = json.optJSONObject("hostList")
        val hostsForServer = hostList?.optJSONArray(videoServer)
        val actualHost = if (hostsForServer != null && hostsForServer.length() > 0) {
            hostsForServer.getString(0)
        } else {
            "setplay.shop" // Fallback
        }

        // Orijinal M3U8 dosyasının barındığı gerçek dizin
        val cleanVideoUrl = videoUrl.replace("/cdn/hls/", "/cdn/down/")
        val m3uLink = "https://$actualHost$cleanVideoUrl"

        Log.d("Kekik_${this.name}", "Setplay Clean Direct Link » $m3uLink")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = "${this.name} - $suffix",
                url     = m3uLink,
                type    = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to "https://setplay.shop/")
            }
        )
    }
}
