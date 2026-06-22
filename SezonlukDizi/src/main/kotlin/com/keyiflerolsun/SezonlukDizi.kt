// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.pltmustafa.common.ErrorUtils
import com.pltmustafa.common.safeLoadLinks
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class SezonlukDizi : MainAPI() {
    override var mainUrl              = "https://sezonlukdizi.cc"
    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id&s="          to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=2&s="    to "Yerli Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val document = app.get("${request.data}${page}").document
            val home     = document.select("div.afis a").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(SezonlukDiziPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val finalUrl = app.get(mainUrl).url
        val ajaxUrl  = "${finalUrl.trimEnd('/')}/ajax/arama.asp"

        val searchResponse = app.post(
            ajaxUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf("q" to query)
        ).parsedSafe<SearchAjaxResponse>()

        val results = mutableListOf<SearchResponse>()
        searchResponse?.results?.diziler?.results?.forEach { item ->
            val title = item.title ?: return@forEach
            val url   = item.url ?: return@forEach
            val image = item.image ?: ""
            
            results.add(newTvSeriesSearchResponse(title, "$finalUrl$url", TvType.TvSeries) {
                this.posterUrl = "$finalUrl$image"
            })
        }
        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url).document

            val title       = document.selectFirst("div.header")?.text()?.trim() ?: return null
            val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
            val year        = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
            val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
            val tags        = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
            val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()

            val endpoint    = url.split("/").last()

            val actorsReq  = app.get("${mainUrl}/oyuncular/${endpoint}").document
            val actors     = actorsReq.select("div.doubling div.ui").map {
                Actor(
                    it.selectFirst("div.header")!!.text().trim(),
                    fixUrlNull(it.selectFirst("img")?.attr("src"))
                )
            }


            val episodesReq = app.get("${mainUrl}/bolumler/${endpoint}").document
            val episodes    = mutableListOf<Episode>()
            for (sezon in episodesReq.select("table.unstackable")) {
                for (bolum in sezon.select("tbody tr")) {
                    val epName    = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                    val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                    val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                    val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                    episodes.add(newEpisode(epHref) {
                        this.name    = epName
                        this.season  = epSeason
                        this.episode = epEpisode
                    })
                }
            }


            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
                addActors(actors)
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(SezonlukDiziPlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return safeLoadLinks(SezonlukDiziPlugin.appContext, this.name, data, callback) { safeCallback ->
            Log.d("SZD", "data » $data")
            val req = app.get(data)
            val document = req.document
            val baseUrl = Regex("""(https?://[^/]+)""").find(req.url)?.value ?: mainUrl

            val aspData = getAspData(baseUrl)
            val bid = document.selectFirst("div#dilsec")?.attr("data-id") ?: throw Exception("Gerekli veri bulunamadı")
            Log.d("SZD", "bid » $bid")

            // --- ALTYAZI KISMI ---
            val altyaziResponse = app.post(
                "${baseUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf(
                    "bid" to bid,
                    "dil" to "1"
                )
            ).parsedSafe<Kaynak>()

            if (altyaziResponse?.status == "success" && altyaziResponse.data != null) {
                for (veri in altyaziResponse.data) {
                    Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

                    val veriResponse = app.post(
                        "${baseUrl}/ajax/dataEmbed${aspData.embed}.asp",
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf("id" to "${veri.id}")
                    ).document

                    val iframeSrc = veriResponse.selectFirst("iframe")?.attr("src")
                    val iframe = fixUrlNull(iframeSrc) ?: continue
                    loadExtractor(iframe, "${baseUrl}/", subtitleCallback) { link ->
                        safeCallback.invoke(
                            ExtractorLink(
                                source = "AltYazı - ${veri.baslik}",
                                name = "AltYazı - ${veri.baslik}",
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers,
                                extractorData = link.extractorData
                            )
                        )
                    }
                }
            }

            // --- DUBLAJ KISMI ---
            val dublajResponse = app.post(
                "${baseUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf(
                    "bid" to bid,
                    "dil" to "0"
                )
            ).parsedSafe<Kaynak>()

            if (dublajResponse?.status == "success" && dublajResponse.data != null) {
                for (veri in dublajResponse.data) {
                    Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

                    val veriResponse = app.post(
                        "${baseUrl}/ajax/dataEmbed${aspData.embed}.asp",
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf("id" to "${veri.id}")
                    ).document

                    val iframeSrc = veriResponse.selectFirst("iframe")?.attr("src")
                    val iframe = fixUrlNull(iframeSrc) ?: continue
                    Log.d("SZD", "dil»0 | iframe » $iframe")

                    loadExtractor(iframe, "${baseUrl}/", subtitleCallback) { link ->
                        safeCallback.invoke(
                            ExtractorLink(
                                source = "Dublaj - ${veri.baslik}",
                                name = "Dublaj - ${veri.baslik}",
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers,
                                extractorData = link.extractorData
                            )
                        )
                    }
                }
            }
        }
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData(baseUrl: String) : AspData{
        val websiteCustomJavascript = app.get("${baseUrl}/js/site.min.js")
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        return AspData(dataAlternatifAsp,dataEmbedAsp)
    }
}
