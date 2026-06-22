
package com.pltmustafa.pltstream.hosts

import android.util.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*

open class ContentX : ExtractorApi() {
    override val name            = "ContentX"
    override val mainUrl         = "https://contentx.me"
    override val requiresReferer = true

    private val cloudflareKiller by lazy { com.lagradost.cloudstream3.network.CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: com.lagradost.cloudstream3.network.CloudflareKiller): okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = org.jsoup.Jsoup.parse(response.peekBody(10 * 1024).string())

            if (response.code == 503 || doc.html().contains("Cloudflare", ignoreCase = true) || doc.selectFirst("meta[name='cloudflare']") != null || doc.html().contains("Just a moment", ignoreCase = true)) {
                response.close()
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override suspend fun getUrl(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    val extRef = referer ?: "https://dizilla.to"
    Log.d("Kekik_${this.name}", "url » $url")

    val iSource = app.get(url, referer = extRef, interceptor = interceptor).text
    val iExtract = Regex("""window\.openPlayer\('([^']+)'""").find(iSource)!!.groups[1]?.value ?: throw ErrorLoadingException("iExtract is null")

    val subUrls = mutableSetOf<String>()
    Regex(""""file":"((?:\\\\\"|[^"])+)","label":"((?:\\\\\"|[^"])+)"""").findAll(iSource).forEach {
        val (subUrlExt, subLangExt) = it.destructured

            val subUrl = subUrlExt.replace("\\/", "/").replace("\\u0026", "&").replace("\\", "")
            val subLang = subLangExt.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç").replace("\\u011f", "ğ").replace("\\u015f", "ş")

        if (subUrl in subUrls) return@forEach
        subUrls.add(subUrl)

        subtitleCallback.invoke(
            newSubtitleFile(
                lang = subLang,
                url = fixUrl(subUrl)
            ) {
                headers = mapOf(
                    "Referer" to url,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0"
                )
            }
        )
    }

    val hostUrl = "https://" + java.net.URI(url).host
    val vidSource = app.get("${hostUrl}/source2.php?v=${iExtract}", referer = extRef, interceptor = interceptor).text
    val vidExtract = Regex("""file":"([^"]+)""").find(vidSource)?.groups?.get(1)?.value ?: throw ErrorLoadingException("vidExtract is null")
    val m3uLink = vidExtract.replace("\\", "").let {
    if (it.contains("hotlinger") || it.contains("dplayer82.site")) {
        it.replace("m.php", "master.m3u8")
    } else {
        it
    }
}

    callback.invoke(
        newExtractorLink(
            source = this.name,
            name   = this.name,
            url    = m3uLink,
            type   = ExtractorLinkType.M3U8
        ) {
            headers = mapOf("Referer" to url,
			"User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0")
            quality = Qualities.Unknown.value
        }
    )

    val iDublaj = Regex(""","([^']+)","Türkçe""").find(iSource)?.groups?.get(1)?.value
    if (iDublaj != null) {
        val dublajSource = app.get("${hostUrl}/source2.php?v=${iDublaj}", referer = extRef, interceptor = interceptor).text
        val dublajExtract = Regex("""file":"([^"]+)""").find(dublajSource)!!.groups[1]?.value ?: throw ErrorLoadingException("dublajExtract is null")
        val dublajLink = dublajExtract.replace("\\", "").let {
            if (it.contains("hotlinger")) it.replace("m.php", "master.m3u8") else it
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = dublajLink,
                type   = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to url,
				"User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0")
                quality = Qualities.Unknown.value
            }
        )
    }
 }
}
