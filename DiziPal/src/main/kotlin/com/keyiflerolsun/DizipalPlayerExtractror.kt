package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.newExtractorLink

// DiziPal sınıfının dışına (altına) ekle
class DizipalPlayer : ExtractorApi() {
    override var name = "DizipalPlayer"
    override var mainUrl = "dplayer82.site"
    override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val response = app.get(url, referer = referer).text
            val openPlayerRegex = """window\.openPlayer\s*\(\s*['"]([^'"]+)['"]""".toRegex()

            val subUrls = mutableSetOf<String>()
            Regex(""""file":"((?:\\\\\"|[^"])+)","label":"((?:\\\\\"|[^"])+)"""").findAll(response).forEach {
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
            val playlistId = openPlayerRegex.find(response)?.groupValues?.get(1)
            Log.d("DiziPal", "--> playlistId: $playlistId")
            if (playlistId != null) {
                val domainRegex = """https?://[^/]+""".toRegex()
                val domain = domainRegex.find(url)?.value ?: "https://dplayer82.site"
                val apiUrl = "$domain/source2.php?v=$playlistId"
                Log.d("DiziPal", "--> apiUrl: $apiUrl")
                val apiResponse = app.get(apiUrl, referer = url).text

                try {
                    val fileRegex = """"file"\s*:\s*"([^"]+)"""".toRegex()
                    val fileMatches = fileRegex.findAll(apiResponse)

                    fileMatches.forEach { matchResult ->
                        var fileUrl = matchResult.groupValues[1].replace("\\/", "/")

                        if (fileUrl.startsWith("//")) {
                            fileUrl = "https:$fileUrl"
                        } else if (!fileUrl.startsWith("http")) {
                            fileUrl = "https://$fileUrl"
                            Log.d("DiziPal", "--> fileUrl: $fileUrl")
                        }

                        if (fileUrl.contains("m.php")) {
                            fileUrl = fileUrl.replace("m.php", "master.m3u8")
                        }

                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "DPlayer (Auto)",
                                    url = fileUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    headers = mapOf("Origin" to domain, "Referer" to url)
                                    Qualities.Unknown.value
                                }
                            )
                        }
                } catch (e: Exception) {
                    Log.e("DiziPal", "--> DPlayer Extractor Hata: ${e.message}")
                }
            }
        }
    }
