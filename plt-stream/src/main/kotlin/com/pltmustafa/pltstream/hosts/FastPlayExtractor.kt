
package com.pltmustafa.pltstream.hosts

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class FastPlay : ExtractorApi() {
    override val name            = "FastPlay"
    override val mainUrl         = "https://fastplay.mom"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("FST_Extractor", "FastPlay URL: $url | Referer: $referer")
        
        val partKey = if (url.contains("partKey=")) url.substringAfter("partKey=").substringBefore("&") else ""
        val suffix = when {
            partKey.contains("turkcedublaj", ignoreCase = true) -> "Dublaj"
            partKey.contains("turkcealtyazi", ignoreCase = true) -> "Altyazı"
            partKey.isNotEmpty() -> partKey
            else -> ""
        }
        val nameSuffix = if (suffix.isNotEmpty()) " - $suffix" else ""

        val baseUrl = url.substringBefore("?")
        val m3uLink = baseUrl.replace("/video/", "/manifests/") + "/master.txt"

        Log.d("FST_Extractor", "Converted m3uLink » $m3uLink")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = "${this.name}$nameSuffix",
                url     = m3uLink,
                type    = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to url)
            }
        )
    }
}
