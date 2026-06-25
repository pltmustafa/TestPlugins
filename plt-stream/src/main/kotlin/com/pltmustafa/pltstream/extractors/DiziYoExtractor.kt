package com.pltmustafa.pltstream.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class DiziYoExtractor : SiteExtractor {
    override val name = "DiziYo"
    private val mainUrl = "https://www.diziyo.so"
    private val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"

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
        if (imdbId.isNullOrEmpty()) {
            Log.d("plt-stream", "$name: IMDB ID bulunamadı, atlanıyor")
            return
        }

        val isTv = season != null && episode != null

        try {
            Log.d("plt-stream", "$name: IMDB ID ile aranıyor -> $imdbId")

            val searchResponse = app.post(
                ajaxUrl,
                data = mapOf(
                    "action" to "dzn_ajax_search",
                    "term" to imdbId
                ),
                referer = "$mainUrl/"
            ).text

            val showUrlMatch = Regex("""data-url="([^"]+)"""").find(searchResponse)
            val showUrl = showUrlMatch?.groupValues?.get(1)

            if (showUrl.isNullOrEmpty()) {
                Log.d("plt-stream", "$name: İçerik bulunamadı -> $imdbId")
                return
            }

            Log.d("plt-stream", "$name: Sayfa bulundu -> $showUrl")

            if (!isTv) {
                Log.d("plt-stream", "$name: Film desteği henüz yok, atlanıyor")
                return
            }

            val showPage = app.get(showUrl, referer = "$mainUrl/").text

            val episodeUrlPattern = Regex("""href="([^"]*${season}-sezon-${episode}-bolum-izle[^"]*)"""")
            val episodeUrlMatch = episodeUrlPattern.find(showPage)
            val episodeUrl = episodeUrlMatch?.groupValues?.get(1)

            if (episodeUrl.isNullOrEmpty()) {
                Log.d("plt-stream", "$name: S${season}E${episode} bölüm linki bulunamadı")
                return
            }

            Log.d("plt-stream", "$name: Bölüm sayfası -> $episodeUrl")

            val langVariants = mutableListOf<Pair<String, String>>()

            val episodePage = app.get(episodeUrl, referer = showUrl).text

            val tabsRegex = Regex("""<a\s+href="([^"]*)"[^>]*class="dzn-tab[^"]*"[^>]*>.*?<span\s+class="dzn-tab-txt">([^<]+)</span>""")
            val tabs = tabsRegex.findAll(episodePage).toList()

            if (tabs.isNotEmpty()) {
                for (tab in tabs) {
                    val tabUrl = tab.groupValues[1]
                    val tabName = tab.groupValues[2].trim()
                    langVariants.add(Pair(tabName, tabUrl))
                }
            } else {
                langVariants.add(Pair("Türkçe", episodeUrl))
            }

            for ((langName, langUrl) in langVariants) {
                try {
                    val page = if (langUrl == episodeUrl) episodePage else app.get(langUrl, referer = episodeUrl).text

                    val iframeRegex = Regex("""(?:data-wpfc-original-src|src)="(https://[^"]*dzyhd[^"]*video/[^"]*)"""")
                    val iframeMatch = iframeRegex.find(page)
                    val embedPageUrl = iframeMatch?.groupValues?.get(1)

                    if (embedPageUrl.isNullOrEmpty()) {
                        Log.d("plt-stream", "$name: [$langName] iframe bulunamadı")
                        continue
                    }

                    Log.d("plt-stream", "$name: [$langName] Embed -> $embedPageUrl")

                    val embedPage = app.get(embedPageUrl, referer = langUrl).text

                    val apiUrlRegex = Regex("""var\s+apiUrl\s*=\s*"([^"]+)"""")
                    val apiUrlMatch = apiUrlRegex.find(embedPage)
                    val videoApiUrl = apiUrlMatch?.groupValues?.get(1)

                    if (videoApiUrl.isNullOrEmpty()) {
                        Log.d("plt-stream", "$name: [$langName] API URL bulunamadı")
                        continue
                    }

                    Log.d("plt-stream", "$name: [$langName] API -> $videoApiUrl")

                    val apiResponse = app.get(
                        videoApiUrl,
                        referer = embedPageUrl
                    ).text

                    val apiJson = tryParseJson<DzyApiResponse>(apiResponse)

                    if (apiJson?.ok != true || apiJson.embed.isNullOrEmpty()) {
                        Log.d("plt-stream", "$name: [$langName] API'den embed URL alınamadı")
                        continue
                    }

                    val finalEmbedUrl = apiJson.embed
                    Log.d("plt-stream", "$name: [$langName] Final embed -> $finalEmbedUrl")

                    loadExtractor(finalEmbedUrl, embedPageUrl, subtitleCallback) { link ->
                        callback.invoke(
                            ExtractorLink(
                                source = "$name - $langName",
                                name = "$name - $langName",
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers
                            )
                        )
                    }

                } catch (e: Exception) {
                    Log.e("plt-stream", "$name: [$langName] Hata -> ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e("plt-stream", "$name: Genel hata -> ${e.message}")
        }
    }

    data class DzyApiResponse(
        val ok: Boolean?,
        val blocked: Boolean?,
        val embed: String?,
        val verified: Boolean?
    )
}
