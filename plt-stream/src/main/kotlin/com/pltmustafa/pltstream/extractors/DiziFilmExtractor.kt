package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

data class DiziFilmSearchResponse(
    @JsonProperty("success") val success: Boolean?,
    @JsonProperty("results") val results: List<DiziFilmSearchResult>?
)

data class DiziFilmSearchResult(
    @JsonProperty("id") val id: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("slug") val slug: String?,
    @JsonProperty("year") val year: Int?,
    @JsonProperty("content_type") val content_type: String? // "movie" or "series"
)

data class DiziFilmMoviePayload(
    @JsonProperty("parts") val parts: List<DiziFilmPartItem>?
)

data class DiziFilmPartItem(
    @JsonProperty("url") val url: String?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("quality") val quality: String?
)

data class DiziFilmEpisodeItem(
    @JsonProperty("embed_player_url_1") val embed_player_url_1: String?,
    @JsonProperty("embed_player_url_2") val embed_player_url_2: String?,
    @JsonProperty("embed_player_url_3") val embed_player_url_3: String?
)

class DiziFilmExtractor : SiteExtractor {
    override val name = "DiziFilm"
    private val mainUrl = "https://dizifilm.life"

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
            val searchUrl = "$mainUrl/api/search?q=$encodedQuery&type=all&limit=20"

            val response = app.get(
                searchUrl,
                headers = mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:152.0) Gecko/20100101 Firefox/152.0",
                    "Referer" to "$mainUrl/"
                )
            ).text

            val json = AppUtils.tryParseJson<DiziFilmSearchResponse>(response)
            val results = json?.results ?: return
            
            var matchedItem: DiziFilmSearchResult? = null
            
            for (item in results) {
                val itemTitle = item.title ?: continue
                if (itemTitle.equals(searchQuery, ignoreCase = true) || 
                    itemTitle.lowercase().replace(" ", "") == searchQuery.lowercase().replace(" ", "")) {
                    matchedItem = item
                    Log.d("plt-stream", "$name: Eşleşti -> $itemTitle")
                    break
                }
            }

            if (matchedItem == null) {
                Log.d("plt-stream", "$name: Eşleşme bulunamadı")
                return
            }

            val isMovie = matchedItem.content_type == "movie"
            val slug = matchedItem.slug ?: return

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

            if (isMovie && season == null && episode == null) {
                val movieUrl = "$mainUrl/film/$slug"
                val html = app.get(movieUrl).text
                val movieJson = extractJsonFromNextJs(html, "movie")
                if (movieJson != null) {
                    val movie = AppUtils.tryParseJson<DiziFilmMoviePayload>(movieJson)
                    movie?.parts?.forEach { part ->
                        val url = part.url
                        if (!url.isNullOrEmpty()) {
                            if (url.contains("vidlop.com")) {
                                extractVidlop(url, "Vidlop ${part.language ?: ""} ${part.quality ?: ""}", subtitleCallback, callback)
                            } else {
                                loadExtractor(url, "$mainUrl/", subtitleCallback, safeCallback)
                            }
                        }
                    }
                }
            } else if (!isMovie && season != null && episode != null) {
                val episodeUrl = "$mainUrl/dizi/$slug/sezon-$season/bolum-$episode"
                val html = app.get(episodeUrl).text
                val episodeJson = extractJsonFromNextJs(html, "episode")
                if (episodeJson != null) {
                    val ep = AppUtils.tryParseJson<DiziFilmEpisodeItem>(episodeJson)
                    if (ep != null) {
                        listOfNotNull(ep.embed_player_url_1, ep.embed_player_url_2, ep.embed_player_url_3)
                            .forEachIndexed { index, url ->
                                if (url.contains("vidlop.com")) {
                                    extractVidlop(url, "Vidlop Server ${index + 1}", subtitleCallback, callback)
                                } else {
                                    loadExtractor(url, "$mainUrl/", subtitleCallback, safeCallback)
                                }
                            }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("plt-stream", "$name hata: ${e.message}")
        }
    }

    private suspend fun extractVidlop(vidlopUrl: String, sourceName: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val vidlopHtml = app.get(vidlopUrl, referer = mainUrl).text
            val scriptData = getAndUnpack(vidlopHtml)
            
            var m3u8Url = Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(scriptData)?.groupValues?.get(1)
            
            if (m3u8Url == null) {
                val vidlopId = Regex("""video/([a-zA-Z0-9]+)""").find(vidlopUrl)?.groupValues?.get(1)
                if (vidlopId != null) {
                    val apiUrl = "https://vidlop.com/player/index.php?data=$vidlopId&do=getVideo"
                    val apiResponse = app.post(
                        apiUrl,
                        headers = mapOf(
                            "Referer" to vidlopUrl,
                            "X-Requested-With" to "XMLHttpRequest"
                        ),
                        data = mapOf(
                            "hash" to vidlopId,
                            "r" to mainUrl
                        )
                    ).text
                    
                    val secureLinkMatch = Regex("""\"securedLink\"\:\"([^\"]+)\"""").find(apiResponse)
                    if (secureLinkMatch != null) {
                        m3u8Url = secureLinkMatch.groupValues[1].replace("\\/", "/")
                    }
                }
            }
            
            if (m3u8Url != null) {
                callback.invoke(
                    ExtractorLink(
                        source = "$name - $sourceName",
                        name = "$name - $sourceName",
                        url = m3u8Url,
                        referer = vidlopUrl,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(),
                        extractorData = null
                    )
                )
            }
            
            val subtitlesMatch = Regex("""(?s)tracks["']?\s*:\s*\[(.*?)\]""").find(scriptData)
            Log.d("plt-stream", "Vidlop scriptData length: ${scriptData.length}")
            if (subtitlesMatch != null) {
                val tracksStr = subtitlesMatch.groupValues[1]
                Log.d("plt-stream", "Vidlop tracks array content: $tracksStr")
                val trackObjects = Regex("""\{([^}]+)\}""").findAll(tracksStr)
                
                var subtitleCount = 0
                for (trackMatch in trackObjects) {
                    val trackStr = trackMatch.groupValues[1]
                    Log.d("plt-stream", "Vidlop processing track object: $trackStr")
                    
                    val file = Regex("""file["']?\s*:\s*["']([^"']+)["']""").find(trackStr)?.groupValues?.get(1)
                    val label = Regex("""label["']?\s*:\s*["']([^"']+)["']""").find(trackStr)?.groupValues?.get(1) ?: "Türkçe"
                    val kind = Regex("""kind["']?\s*:\s*["']([^"']+)["']""").find(trackStr)?.groupValues?.get(1)
                    
                    Log.d("plt-stream", "Vidlop parsed -> file: $file, label: $label, kind: $kind")
                    
                    if (kind == "thumbnails" || kind == "sprite") {
                        Log.d("plt-stream", "Vidlop skipping thumbnail/sprite track")
                        continue
                    }
                    
                    if (file != null) {
                        var fixedSubUrl = file.replace("\\/", "/")
                        Log.d("plt-stream", "Vidlop raw subUrl: $fixedSubUrl")
                        
                        if (fixedSubUrl.startsWith("//")) {
                            fixedSubUrl = "https:$fixedSubUrl"
                        } else if (fixedSubUrl.startsWith("/")) {
                            val uri = java.net.URI(vidlopUrl)
                            fixedSubUrl = "${uri.scheme}://${uri.host}$fixedSubUrl"
                        } else if (!fixedSubUrl.startsWith("http")) {
                            fixedSubUrl = "https://vidlop.com/$fixedSubUrl"
                        }
                        
                        Log.d("plt-stream", "Vidlop final fixed subUrl: $fixedSubUrl")
                        subtitleCallback.invoke(SubtitleFile(label, fixedSubUrl))
                        subtitleCount++
                    }
                }
                Log.d("plt-stream", "Vidlop added $subtitleCount subtitles")
            } else {
                Log.d("plt-stream", "Vidlop subtitles tracks regex NOT matched!")
            }
        } catch (e: Exception) {
            Log.e("plt-stream", "Vidlop extractor error for $vidlopUrl", e)
        }
    }

    private fun extractJsonFromNextJs(html: String, key: String): String? {
        val searchStr = "\\\"$key\\\":{"
        val startIndex = html.indexOf(searchStr)
        if (startIndex == -1) return null
        
        val jsonStart = startIndex + searchStr.length - 1
        var depth = 0
        var inQuotes = false
        var isEscaped = false
        
        for (i in jsonStart until html.length) {
            val c = html[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                if (c == '{') depth++
                else if (c == '}') depth--
                
                if (depth == 0) {
                    val jsonStr = html.substring(jsonStart, i + 1)
                    return jsonStr.replace("\\\"", "\"").replace("\\\\", "\\")
                }
            }
        }
        return null
    }
}
