package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse

class Sinewix : MainAPI() {
    override var mainUrl = "https://ydfvfdizipanel.ru"
    override var name = "Sinewix"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Cartoon
    )

    private val apiToken = "9iQNC5HQwPlaFuJDkhncJ5XTJ8feGXOJatAA"

    private val sineHeaders = mapOf(
        "hash256" to "711bff4afeb47f07ab08a0b07e85d3835e739295e8a6361db77eebd93d96306b",
        "signature" to "3082058830820370a00302010202145bbfbba9791db758ad12295636e094ab4b07dc24300d06092a864886f70d01010b05003074310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e060355040b1307416e64726f69643110300e06035504031307416e64726f69643020170d3231313231353232303433335a180f32303531313231353232303433335a3074310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e060355040b1307416e64726f69643110300e06035504031307416e64726f696430820222300d06092a864886f70d01010105000382020f003082020a0282020100a5106a24bb3f9c0aaf3a2b228f794b5eaf1757ba758b19736a39d1bdc73fc983a7237b8d5ca5156cfa999c1dab3418bbc2be0920e0ee001c8aa4812d1dae75d080f09e91e0abda83ff9a76e8384a4429f4849248069a59505b12ac2c14ba2e4d1a13afcdaf54e508697ff928a9f738e6f4a6fc27409c55329eb149b5ff89c5a2d7c06bf9e62086f955cad17d7be2623ee9d5ec56068eadc23cb0965a13ff97d49fe10ef41afc6eeca36b4ace9582097faff89f590bc831cdb3a69eec5d15b67c3f2cad49e37ed053733e3d2d400c47755b932bdbe15d749fd6ad1dce30ba5e66094dfb6ee6f64cafb807e11b19a990c5d078c6d6701cda0bdeb21e99404ff166074f4c89b04c418f4e7940db5c78647c475bcfb85d4c4e836ee7d7c1d53e9e736b5d96d4b4d8b98209064b729ac6a682d55a6a930e518d849898bb28329ca0aaa133b5e5270a9d5940cac6af4802a57fd971efda91abb602882dd6aa6ce2b236b57b52ee2481498f0cacbcc2c36c238bc84becad7eaaf1125b9a1ca9ded6c79f3f283a52050377809b2a9995d66e1636b0ed426fdd8685c47cb18e82077f4aefcc07887e1dc58b4d64be1632f0e7b4625da6f40c65a8512a6454a4b96963e7f876136e6c0069a519a79ad632078ed965aa12482458060c030ed50db706d854f88cb004630b49285d8af8b471ff8f6070687826412287b50049bcb7d1b6b62ef90203010001a310300e300c0603551d13040530030101ff300d06092a864886f70d01010b0500038202010051c0b7bd793181dc29ca777d3773f928a366c8469ecf2fa3cfb076e8831970d19bb2b96e44e8ccc647cf0696bb824ac61c23d958525d283cab26037b04d58aa79bf92192db843adf5c26a980f081d2f0e14f759fc5ff4c5bb3dce0860299bfe7b349a8155a2efaf731ba25ce796a80c1442c7bf80f8c1a7912ff0b6f6592264315337251a846460194fa594f81f38f9e5233a63201e931ad9cab5bf119f24025613f307194eaa6eb39a83f3c05a49ba34455b1aff7c6839bbb657d9392ffdf397432af6e56ba9534a8b07d7060fe09691c6cf07cb5324f67b3cc0871a8c621d81fe71d71085c55206a4f57e25f774fd4b979b299e8bb076b50fca42fa57da2d519fd35a4a7c0137babaed4345f8031b63b6a71f5e8268f709d658ccd7c2a58849379d25bfa598c3f4a2c3d9b7d89285fefeb7f0ec65137d38b08ce432a15688b624a179e6a4a505ebc3bcdfbc4d4330508ee2d8d0f016924dcec21a6838ef7d834c6f43bde4a5201ed0b3bb4e9bd377b470e36bcf5bc3d56169dbd8e39567aa7dce4d1a8a8a54a5e1aa6fb1a8aab0062669a966f96e15ccce6fe12ea5e6a8b8c8823bdc94988ca39759fd1cc8fd8ae5c3d74db50b174cf7d77655016c075c91d439ed01cc0a9f695c99fad3b5495fb6cb1e01a5fa020cc6022a85c07ec55f9eba89719f86e49d34ab5bd208c5f70cced2b7b7963c014f8404432979b506de29e",
        "User-Agent" to "EasyPlex (Android 14; SM-A546B; Samsung Galaxy A54 5G; tr)",
        "Accept" to "application/json"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/public/api/media/seriesEpisodesAll/$apiToken" to "Yeni Bölümler",
        //"$mainUrl/public/api/genres/latestmovies/all/$apiToken" to "Son Filmler",
        "$mainUrl/public/api/genres/latestseries/all/$apiToken" to "Son Diziler",
        "$mainUrl/public/api/genres/latestanimes/all/$apiToken" to "Son Animeler",
        //"$mainUrl/public/api/genres/mediaLibrary/show/878/movie/$apiToken" to "Bilim Kurgu Filmleri",
        //"$mainUrl/public/api/genres/mediaLibrary/show/10770/movie/$apiToken" to "Tv Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/serie/$apiToken" to "Suç Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/movie/$apiToken" to "Suç Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/serie/$apiToken" to "Gizem Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/movie/$apiToken" to "Gizem Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/10769/serie/$apiToken" to "Kore Diziler",
        "$mainUrl/public/api/genres/mediaLibrary/show/16/movie/$apiToken" to "Animasyonlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("${request.data}?page=$page", headers = sineHeaders).text
        
        val items = if (request.name == "Yeni Bölümler") {
            parseJson<SineWixResponseHash>(response).data?.mapNotNull { it.toSearchResponse() }
        } else {
            parseJson<SineWixYeniBolumResponse>(response).data?.mapNotNull { it.toSearchResponse() }
        }

        return newHomePageResponse(request.name, items ?: emptyList())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/public/api/search/$query/$apiToken", headers = sineHeaders).text
        val data = parseJson<SineWixResponseHash>(response)
        return data.searchResponse?.mapNotNull { it.toSearchResponse() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val responseText = app.get(url, headers = sineHeaders).text
        val it = parseJson<SineWixIcerikler>(responseText)
        
        val title = it.name ?: it.title ?: return null
        val poster = it.posterPath ?: it.backdropPath ?: it.backdropPathTv ?: ""
        val type = if (url.contains("serie") || it.type == "serie") TvType.TvSeries 
                   else if (url.contains("anime") || it.type == "anime") TvType.Anime
                   else TvType.Movie
        
        return if (type == TvType.TvSeries || type == TvType.Anime) {
            val episodes = it.seasons?.flatMap { season ->
                season.episodes?.map { episode ->
                    val videoLink = episode.videos?.firstOrNull()?.link
                    newEpisode(videoLink ?: "") {
                        this.name = episode.name ?: "Bölüm ${episode.episodeNumber}"
                        this.season = season.seasonNumber
                        this.episode = episode.episodeNumber
                        this.posterUrl = episode.stillPath ?: episode.stillPathTv
                    }
                } ?: emptyList()
            } ?: emptyList()
            
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.plot = it.overview
                this.year = it.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            }
        } else {
            val videoLink = it.videos?.firstOrNull()?.link ?: ""
            newMovieLoadResponse(title, url, type, videoLink) {
                this.posterUrl = poster
                this.plot = it.overview
                this.year = it.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        loadExtractor(data, subtitleCallback, callback)
        return true
    }

    private fun SineWixIcerikler.toSearchResponse(): SearchResponse? {
        val title = name ?: title ?: return null
        val poster = posterPath ?: backdropPath ?: backdropPathTv ?: ""
        val type = if (this.type == "serie") TvType.TvSeries else if (this.type == "anime") TvType.Anime else TvType.Movie
        val href = if (type == TvType.TvSeries || type == TvType.Anime) {
            "$mainUrl/public/api/series/show/$id/$apiToken"
        } else {
            "$mainUrl/public/api/media/movie/info/$id/$apiToken"
        }
        
        return newTvSeriesSearchResponse(title, href, type) {
            this.posterUrl = poster
        }
    }

    private fun SineWixYeniBolum.toSearchResponse(): SearchResponse? {
        val title = showName ?: return null
        val poster = posterPath ?: ""
        val displayTitle = "$title ${seasonNumber}x${episodeNumber.toString().padStart(2, '0')}"
        val href = "$mainUrl/public/api/series/show/$id/$apiToken"
        
        return newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    
    data class SineWixResponseHash(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("data") val data: List<SineWixIcerikler>? = null,
        @JsonProperty("search") val searchResponse: List<SineWixIcerikler>? = null
    )

    data class SineWixYeniBolumResponse(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("data") val data: List<SineWixYeniBolum>? = null
    )

    data class SineWixIcerikler(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("backdrop_path_tv") val backdropPathTv: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("seasons") val seasons: List<SineWixSeason>? = null,
        @JsonProperty("videos") val videos: List<SineWixVideo>? = null
    )

    data class SineWixYeniBolum(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val showName: String? = null,
        @JsonProperty("episode_name") val episodeName: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("type") val type: String? = null
    )

    data class SineWixSeason(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episodes") val episodes: List<SineWixEpisode>? = null
    )

    data class SineWixEpisode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("still_path_tv") val stillPathTv: String? = null,
        @JsonProperty("videos") val videos: List<SineWixVideo>? = null
    )

    data class SineWixVideo(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("server") val server: String? = null
    )
}
