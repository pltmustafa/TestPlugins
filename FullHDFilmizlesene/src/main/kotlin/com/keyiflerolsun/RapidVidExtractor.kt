// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


open class RapidVid : ExtractorApi() {
    override val name            = "RapidVid"
    override val mainUrl         = "https://rapidvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoReq = app.get(url, referer=extRef).document

        val script = videoReq.select("script").find { it.data().contains("jwSetup.sources") }?.data() ?: ""

        if (script.isNotEmpty()) {
            val jwTrack = script.substringAfter("jwSetup.tracks =").substringBefore(";")
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val tracks: List<CaptionData> = objectMapper.readValue(jwTrack)
            tracks.forEach { track ->
                val lang = track.label?.replace("\\u0131", "ı")?.replace("\\u0130", "İ")?.replace("\\u00fc", "ü")?.replace("\\u00e7", "ç")
                val url  = (track.file.replace("\\", ""))
                if (lang != null){
                    subtitleCallback.invoke(
                        newSubtitleFile(lang, url)
                    )
                }
            }
            val jwSetup = script.substringAfter("jwSetup.sources =").substringBefore(";")
            println(jwSetup)
            val regex = Regex("""av\('([^']+)'\)""")
            val match = regex.find(jwSetup)
            if (match != null) {
                val encodedValue = match.groupValues[1]
                val m3Url = av(encodedValue)
                callback.invoke(
                    newExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = m3Url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                Log.d("Rapid", "No match found.")
            }
        }

    }

    fun av(o: String): String {
        return decodeSecret(o)
    }


    fun decodeSecret(encodedString: String): String {
        val reversedBase64Input = encodedString.reversed()
        val tString = base64Decode(reversedBase64Input)
        val oBuilder = StringBuilder()
        val key = "K9L"
        for (index in tString.indices) {
            val keyChar = key[index % key.length]
            val offset = keyChar.code % 5 + 1

            val originalCharCode = tString[index].code
            val transformedCharCode = originalCharCode - offset
            oBuilder.append(transformedCharCode.toChar())
        }

        val finalResultBytes = base64Decode(oBuilder.toString())
        return finalResultBytes
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CaptionData(
    @JsonProperty("kind") var kind: String,
    @JsonProperty("file") var file: String,
    @JsonProperty("label") var label: String? = null,
)