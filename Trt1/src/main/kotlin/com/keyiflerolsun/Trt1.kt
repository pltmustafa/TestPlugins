package com.keyiflerolsun

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import android.util.Log
import com.lagradost.cloudstream3.app

class Trt1 : MainAPI() {
    override var mainUrl = "https://www.tabii.com"
    override var name = "TRT 1"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "TRT 1 Canlı"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val show = newLiveSearchResponse(
            name = "TRT 1 Canlı Yayın",
            url = "https://www.tabii.com/tr/watch/live/trt1?trackId=150002",
            type = TvType.Live
        ) {
            this.posterUrl = "https://cms-tabii-public-image.tabii.com/int/webp/w600/q84/23846_1-0-465-262.jpeg"
        }
        return newHomePageResponse(request.name, listOf(show))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(
            name = "TRT 1 Canlı Yayın",
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = "https://cms-tabii-public-image.tabii.com/int/webp/w600/q84/23846_1-0-465-262.jpeg"
            this.plot = "TRT 1 Canlı Yayın."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Trt1", "loadLinks called with data: $data")
        
        try {
            val response = app.get(data)
            Log.d("Trt1", "Response URL: ${response.url}")
            Log.d("Trt1", "Response code: ${response.code}")
            
            val html = response.text
            Log.d("Trt1", "HTML length: ${html.length}")
            
            val m3u8Regex = Regex("""(https?://[^"']+\.m3u8[^"']*)""")
            val m3u8Links = m3u8Regex.findAll(html).map { it.value }.toList()
            Log.d("Trt1", "Found m3u8 links count: ${m3u8Links.size}")
            
            val trt1Links = m3u8Links.filter { 
                it.contains("trt1", ignoreCase = true) || it.contains("trt-1", ignoreCase = true)
            }.distinct()
            Log.d("Trt1", "Filtered TRT 1 links count: ${trt1Links.size}")
            
            trt1Links.forEachIndexed { index, link ->
                val serverName = if (link.contains("daion")) "Alternatif" else "Ana Yayın"
                Log.d("Trt1", "m3u8 link $index: $link")
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "TRT 1 ($serverName)",
                        url = link,
                        type = ExtractorLinkType.M3U8
                    ) {
                        headers = mapOf("Referer" to mainUrl)
                        quality = Qualities.Unknown.value
                    }
                )
            }
            
            if (trt1Links.isEmpty()) {
                Log.d("Trt1", "No m3u8 found for TRT1, invoking with original data")
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "TRT 1",
                        url = data,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        headers = mapOf("Referer" to mainUrl)
                        quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            Log.d("Trt1", "Error in loadLinks: ${e.message}")
            e.printStackTrace()
        }
        
        Log.d("Trt1", "loadLinks finished")
        return true
    }
}
