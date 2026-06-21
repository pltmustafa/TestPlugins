// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DiziPal : MainAPI() {
    override var mainUrl              = "https://dizipal1556.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    // ! CloudFlare bypass
// ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/yabanci-dizi-izle"                 to "Yeni Diziler",
        "${mainUrl}/hd-film-izle"                                  to "Yeni Filmler",
        "${mainUrl}/kanal/netflix"                                 to "Netflix",
        "${mainUrl}/kanal/exxen"                                   to "Exxen",
        "${mainUrl}/kanal/max"                                     to "Max",
        "${mainUrl}/kanal/disney"                                  to "Disney+",
        "${mainUrl}/kanal/amazon"                                  to "Amazon Prime",
        "${mainUrl}/kanal/tod"                                     to "TOD (beIN)",
        "${mainUrl}/kanal/tabii"                                   to "Tabii",
        "${mainUrl}/kanal/hulu"                                    to "Hulu",
        //"${mainUrl}/diziler?kelime=&durum=&tur=26&type=&siralama=" to "Anime",
        //"${mainUrl}/diziler?kelime=&durum=&tur=5&type=&siralama="  to "Bilimkurgu Dizileri",
        //"${mainUrl}/tur/bilimkurgu"                                to "Bilimkurgu Filmleri",
        //"${mainUrl}/diziler?kelime=&durum=&tur=11&type=&siralama=" to "Komedi Dizileri",
        //"${mainUrl}/tur/komedi"                                    to "Komedi Filmleri",
        //"${mainUrl}/diziler?kelime=&durum=&tur=4&type=&siralama="  to "Belgesel Dizileri",
        //"${mainUrl}/tur/belgesel"                                  to "Belgesel Filmleri",
        //"${mainUrl}/diziler?kelime=&durum=&tur=25&type=&siralama=" to "Erotik Diziler",
        //"${mainUrl}/tur/erotik"                                    to "Erotik Filmler",
        // "${mainUrl}/diziler?kelime=&durum=&tur=1&type=&siralama="  to "Aile",
        // "${mainUrl}/diziler?kelime=&durum=&tur=2&type=&siralama="  to "Aksiyon",
        // "${mainUrl}/diziler?kelime=&durum=&tur=3&type=&siralama="  to "Animasyon",
        // "${mainUrl}/diziler?kelime=&durum=&tur=4&type=&siralama="  to "Belgesel",
        // "${mainUrl}/diziler?kelime=&durum=&tur=6&type=&siralama="  to "Biyografi",
        // "${mainUrl}/diziler?kelime=&durum=&tur=7&type=&siralama="  to "Dram",
        // "${mainUrl}/diziler?kelime=&durum=&tur=8&type=&siralama="  to "Fantastik",
        // "${mainUrl}/diziler?kelime=&durum=&tur=9&type=&siralama="  to "Gerilim",
        // "${mainUrl}/diziler?kelime=&durum=&tur=10&type=&siralama=" to "Gizem",
        // "${mainUrl}/diziler?kelime=&durum=&tur=12&type=&siralama=" to "Korku",
        // "${mainUrl}/diziler?kelime=&durum=&tur=13&type=&siralama=" to "Macera",
        // "${mainUrl}/diziler?kelime=&durum=&tur=14&type=&siralama=" to "Müzik",
        // "${mainUrl}/diziler?kelime=&durum=&tur=16&type=&siralama=" to "Romantik",
        // "${mainUrl}/diziler?kelime=&durum=&tur=17&type=&siralama=" to "Savaş",
        // "${mainUrl}/diziler?kelime=&durum=&tur=24&type=&siralama=" to "Yerli",
        // "${mainUrl}/diziler?kelime=&durum=&tur=18&type=&siralama=" to "Spor",
        // "${mainUrl}/diziler?kelime=&durum=&tur=19&type=&siralama=" to "Suç",
        // "${mainUrl}/diziler?kelime=&durum=&tur=20&type=&siralama=" to "Tarih",
        // "${mainUrl}/diziler?kelime=&durum=&tur=21&type=&siralama=" to "Western",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val document = app.get(
                request.data, timeout = 10000, interceptor = interceptor, headers = getHeaders(mainUrl)
            ).document
            //Log.d("DZP", "Ana sayfa HTML içeriği:\n${document.outerHtml()}")
            val home     = if (request.data.contains("/yabanci-dizi-izle") || request.data.contains("/hd-film-izle")) {
                document.select("div.new-added-list div.bg-\\[\\#22232a\\]").mapNotNull { it.sonBolumler() }
            } else {
                document.select("div.new-added-list div.bg-\\[\\#22232a\\]").mapNotNull { it.diziler() }
            }

            return newHomePageResponse(request.name, home, hasNext=true)
        } catch (e: Exception) {
            ErrorUtils.showPluginError(DiziPalPlugin.appContext, this.name, "MAIN_PAGE", mainUrl)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    private fun Element.sonBolumler(): SearchResponse? {
        val name      = this.selectFirst("img")?.attr("alt") ?: return null
        val episode   = this.selectFirst("div.episode")?.text()?.trim()
            ?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: ""
        val title     = if (episode.isNotEmpty()) "$name $episode" else name

        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title     = this.title
        val href      = "${mainUrl}/${this.slug}"
        val posterUrl = this.poster

        return if (this.type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
    val responseRaw = app.post(
        "${mainUrl}/bg/searchcontent",
        headers = mapOf(
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest"
        ),
        referer = "${mainUrl}/",
        data = mapOf(
            "cKey" to "ca1d4a53d0f4761a949b85e51e18f096",
            "cValue" to "MTc3NTI1MTgwMDg3ODNkODBiMDM2MTk1YTkxMWU5ZTYyYjE4NzQyMjJlMzMwNjAxNGVjMWQzMzliNzY5NzFlZmViMzRhMGVmNjgwODU3MGIyZA==",
            "type" to "hepsi", 
            "searchterm" to query
        )
    )

    val mapper = jacksonObjectMapper()
    val rootNode = mapper.readTree(responseRaw.text)
    
    // JSON Pointer ile doğrudan "data" içindeki "result" array'ine iniyoruz
    val resultArrayNode = rootNode.at("/data/result")

    // Fail-safe: Eğer API değişirse, state false gelirse veya boş dönerse provider'ın crash olmasını engelliyoruz
    if (resultArrayNode.isMissingNode || !resultArrayNode.isArray) {
        return emptyList()
    }

    // Sadece hedefteki result array'ini SearchItem listesine dönüştürüyoruz
    val searchItems: List<SearchItem> = mapper.readValue(resultArrayNode.traverse())

    // Idiomatic Kotlin: for döngüsü ve mutable liste yerine "map" ile fonksiyonel ve temiz dönüşüm
    return searchItems.map { it.toPostSearchResult() }
}
    
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url, interceptor = interceptor, headers = getHeaders(mainUrl)).document

            val poster      = document.selectFirst("div.page-top img[alt]")?.attr("src")
            val year        = document.selectXpath("//div[text()='Yıl']//following-sibling::div").text().trim().toIntOrNull()
            val description = document.selectFirst("#router-view > div.w-full.pt-5.pr-10 > div > div.w-full > p")?.text()?.trim() 
                ?: document.selectFirst("div.summary p")?.text()?.trim()
            val tags        = document.selectXpath("//div[text()='Kategoriler']//following-sibling::div").text().trim().split(" ").map { it.trim() }
            val duration    = Regex("(\\d+)").find(document.selectXpath("//div[text()='Süre']//following-sibling::div").text())?.value?.toIntOrNull()

            if (url.contains("/series/")) {
                val title       = document.selectFirst("div.flex h2")?.text() ?: return null
                val episodeElements = document.select("div.relative.w-full.flex.items-start.gap-4")
                val episodes = episodeElements.mapNotNull { element ->
                    // 1. Link ve İsim Bilgisi
                    val linkElement = element.selectFirst("a[data-dizipal-pageloader]") ?: return@mapNotNull null
                    val epHref = fixUrlNull(linkElement.attr("href")) ?: return@mapNotNull null
                    val epName = linkElement.selectFirst("h2")?.text()?.trim() ?: "Bölüm"

                    // 2. Sezon ve Bölüm Metni (Örn: "1. Sezon 1. Bölüm")
                    val infoText = linkElement.selectFirst("div.text-white.text-sm.opacity-80")?.text()?.trim() ?: ""

                    // 3. Regex ile Sayıları Ayıklama (Daha güvenli yöntem)
                    // Bu pattern "1. Sezon 5. Bölüm" gibi bir metinden sayıları çeker.
                    val epSeason = Regex("""(\d+)\.\s*Sezon""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                    val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()

                    newEpisode(epHref) {
                        this.name    = epName
                        this.episode = epEpisode
                        this.season  = epSeason
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.duration  = duration
                }
            } else { 
                val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim()

                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.duration  = duration
                }
            }
        } catch (e: Exception) {
            ErrorUtils.showPluginError(DiziPalPlugin.appContext, this.name, "LOAD_DETAILS", url)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    // 2. LOAD LINKS: Asıl şifre çözme ve Iframe yakalama işleminin yapıldığı yer
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
        var _linksFound = 0
        val _callback: (ExtractorLink) -> Unit = { link ->
            _linksFound++
            callback.invoke(link)
        }
            Log.d("DiziPal", "--> loadLinks ÇAĞRILDI. Gelen URL: $data")
            val doc = app.get(data).document
            
            // Şifreli div'i bul
            val encryptedText = doc.selectFirst("div[data-rm-k=true]")?.text() ?: ""
            Log.d("DiziPal", "--> Şifreli metin uzunluğu: ${encryptedText.length}")
            
            var iframeUrl = if (encryptedText.isNotEmpty()) {
                Log.d("DiziPal", "--> Şifreli veri bulundu, decrypt işlemine geçiliyor...")
                decryptDizipalData(encryptedText)
            } else {
                Log.w("DiziPal", "--> DİKKAT: Şifreli veri DOM'da YOK! Fallback iframe aranıyor...")
                doc.selectFirst("iframe")?.attr("src") ?: ""
            }

            Log.d("DiziPal", "--> Elde edilen Ham Iframe URL: $iframeUrl")

            if (iframeUrl.isNotEmpty()) {
                if (iframeUrl.startsWith("//")) {
                    iframeUrl = "https:$iframeUrl"
                }
                Log.d("DiziPal", "--> Extractor'a gönderilen Final URL: $iframeUrl")
                
                // Extractor'ı tetikle
                DizipalPlayer().getUrl(
                    url = iframeUrl,
                    referer = data, // Videonun bulunduğu sayfa
                    subtitleCallback = subtitleCallback,
                    callback = _callback
                )
            } else {
                Log.e("DiziPal", "--> HATA: iframeUrl tamamen BOŞ. Video linki bulunamadı!")
            }
            if (_linksFound == 0) throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        return true
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ErrorUtils.showPluginError(DiziPalPlugin.appContext, this.name, "LOAD_LINKS", data)
            }, 500)
            throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
        }
    }

    // 3. DECRYPT VE YARDIMCI FONKSİYON: Şifreyi çözen business logic
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Hex string çift uzunlukta olmalıdır" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun decryptDizipalData(rawJsonText: String): String {
        return try {
            val passphrase = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"

            val ctMatch = """"ciphertext"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'ciphertext' değerini bulamadı!") }
                
            val ivMatch = """"iv"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'iv' değerini bulamadı!") }
                
            val saltMatch = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'salt' değerini bulamadı!") }

            Log.d("DiziPal", "--> Regex başarılı. Key türetiliyor...")

            val salt = saltMatch.decodeHex()
            val iv = ivMatch.decodeHex()
            val ciphertext = android.util.Base64.decode(ctMatch, android.util.Base64.DEFAULT)

            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
            val secretKey = factory.generateSecret(spec)
            val secret = javax.crypto.spec.SecretKeySpec(secretKey.encoded, "AES")

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secret, javax.crypto.spec.IvParameterSpec(iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            var finalUrl = String(decryptedBytes, Charsets.UTF_8).replace("\\/", "/")

            Log.d("DiziPal", "--> AES Çözümleme Başarılı. İlk Çıktı: $finalUrl")

            if (finalUrl.startsWith("://")) {
                finalUrl = "https$finalUrl"
            } else if (finalUrl.startsWith("//")) {
                finalUrl = "https:$finalUrl"
            } else if (!finalUrl.startsWith("http")) {
                finalUrl = "https://$finalUrl"
            }

            finalUrl
        } catch (e: Exception) {
            Log.e("DiziPal", "--> HATA: Decryption sırasında Exception fırlatıldı! Mesaj: ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    private fun getHeaders(baseUrl: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
            "Referer" to baseUrl
        )
    }
}

