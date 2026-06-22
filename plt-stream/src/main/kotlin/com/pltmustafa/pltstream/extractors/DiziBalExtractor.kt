package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class DiziBalExtractor : SiteExtractor {
    override val name = "DiziBal"
    private val mainUrl = "https://dizibal.com"
    private val apiUrl = "$mainUrl/api"

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
        val sourceName = this.name

        Log.d("plt-stream", "$sourceName: Aranıyor -> $searchQuery (tmdb: $tmdbId)")

        try {
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val isTv = season != null && episode != null

            val endpoints = if (isTv) {
                listOf(
                    "$apiUrl/series?search=$encodedQuery&page=1&limit=20&siteMode=full",
                    "$apiUrl/anime?search=$encodedQuery&page=1&limit=20&siteMode=full"
                )
            } else {
                listOf(
                    "$apiUrl/movies?search=$encodedQuery&page=1&limit=20&siteMode=full",
                    "$apiUrl/anime?search=$encodedQuery&page=1&limit=20&siteMode=full"
                )
            }

            var matchedItem: WItem? = null

            for (url in endpoints) {
                val response = app.get(url, referer = "$mainUrl/").text
                val json = AppUtils.tryParseJson<WListResponse>(response)
                
                val results = json?.data ?: continue
                Log.d("plt-stream", "$sourceName: ${url.substringBefore("?")} -> ${results.size} sonuç")

                for (item in results) {
                    val itemTmdb = item.id?.toString()
                    val itemTitle = item.name_tr ?: item.name_en ?: item.name ?: item.title_tr ?: item.title_en ?: item.title

                    Log.d("plt-stream", "$sourceName: İnceleniyor -> Başlık: $itemTitle, TMDB: $itemTmdb")

                    if (tmdbId != null && itemTmdb != null && itemTmdb == tmdbId) {
                        Log.d("plt-stream", "$sourceName: TMDB eşleşti! -> $itemTitle")
                        matchedItem = item
                        break
                    }
                }
                if (matchedItem != null) break
            }

            if (matchedItem == null) {
                Log.d("plt-stream", "$sourceName: TMDB eşleşmesi bulunamadı")
                return
            }

            val dbId = matchedItem._id ?: return
            val slug = matchedItem.slug ?: return

            var streamUrl: String? = null

            if (isTv) {
                val epUrl = "$apiUrl/series/$dbId/seasons/$season/episodes/$episode/stream"
                val epRes = app.get(epUrl, referer = "$mainUrl/").text
                val epJson = AppUtils.tryParseJson<WStreamResponse>(epRes)
                streamUrl = epJson?.data?.streamUrl
            } else {
                val detailUrl = "$apiUrl/movies/$slug"
                val detailRes = app.get(detailUrl, referer = "$mainUrl/").text
                val detailJson = AppUtils.tryParseJson<WDetailResponse>(detailRes)
                streamUrl = detailJson?.data?.streamUrl
            }

            if (streamUrl.isNullOrEmpty()) {
                Log.d("plt-stream", "$sourceName: Stream URL bulunamadı")
                return
            }

            Log.d("plt-stream", "$sourceName: Link çıkarılıyor -> $streamUrl")

            if (streamUrl.contains("/embed-")) {
                val embedRes = app.get(streamUrl, referer = apiUrl)
                val html = embedRes.text
                val fetchPath = Regex("""fetch\(['"](/dl\?op=get_stream.*?)['"]\)""").find(html)?.groupValues?.get(1)
                
                if (fetchPath != null) {
                    val host = streamUrl.substringBefore("/embed-")
                    val jsonUrl = "$host$fetchPath"
                    val fileId = Regex("""cookie\('file_id',\s*'(\d+)'""").find(html)?.groupValues?.get(1)
                    
                    val customCookies = mutableMapOf<String, String>()
                    if (fileId != null) customCookies["file_id"] = fileId
                    customCookies["aff"] = "1"
                    customCookies["ref_url"] = "dizibal.com"
                    
                    val headers = mapOf(
                        "Accept" to "application/json, text/plain, */*",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "same-origin",
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                    val streamRes = app.get(jsonUrl, referer = streamUrl, headers = headers, cookies = customCookies)
                    
                    val urlToStream = Regex(""""url":"([^"]+)"""").find(streamRes.text)?.groupValues?.get(1)?.replace("\\/", "/")
                    
                    if (urlToStream != null && urlToStream.isNotBlank()) {
                        callback.invoke(newExtractorLink(
                            source = "$sourceName - DiziBal HD",
                            name = "$sourceName - DiziBal HD",
                            url = urlToStream,
                            type = if(urlToStream.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.P1080.value
                            this.referer = streamUrl
                        })
                        
                        val subs = Regex(""""subtitle":"([^"]+)"""").find(html)?.groupValues?.get(1)
                        if (subs != null) {
                            subs.split(",").forEach { subEntry ->
                                val subParts = subEntry.split("]")
                                if (subParts.size > 1) {
                                    val lang = subParts[0].replace("[", "").trim()
                                    val subUrlPart = subParts[1].trim()
                                    val subUrl = if (subUrlPart.startsWith("http")) subUrlPart else host + subUrlPart
                                    subtitleCallback(SubtitleFile(lang, subUrl))
                                }
                            }
                        }
                    }
                }
            } else {
                loadExtractor(streamUrl, subtitleCallback, callback)
            }

        } catch (e: Exception) {
            Log.e("plt-stream", "$sourceName hata: ${e.message}")
        }
    }

    data class WListResponse(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("data") val data: List<WItem>?
    )

    data class WItem(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("_id") val _id: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("title_tr") val title_tr: String?,
        @JsonProperty("title_en") val title_en: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("name_tr") val name_tr: String?,
        @JsonProperty("name_en") val name_en: String?
    )

    data class WDetailResponse(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("data") val data: WItemDetail?
    )

    data class WItemDetail(
        @JsonProperty("streamUrl") val streamUrl: String?
    )

    data class WStreamResponse(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("data") val data: WStreamData?
    )

    data class WStreamData(
        @JsonProperty("streamUrl") val streamUrl: String?
    )
}
