package com.pltmustafa.pltstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.pltmustafa.pltstream.extractors.HDFilmCehennemiExtractor
import com.pltmustafa.pltstream.extractors.FilmModuExtractor
import com.pltmustafa.pltstream.extractors.FilmekseniExtractor
import com.pltmustafa.pltstream.extractors.FullHDFilmizleseneExtractor
import com.pltmustafa.pltstream.extractors.DiziBalExtractor
import com.pltmustafa.pltstream.extractors.DiziBoxExtractor
import com.pltmustafa.pltstream.extractors.YabanciDiziExtractor
import com.pltmustafa.pltstream.extractors.DiziFilmExtractor
import com.pltmustafa.pltstream.extractors.DizillaExtractor
import com.pltmustafa.pltstream.extractors.DiziPalExtractor
import com.pltmustafa.pltstream.extractors.DiziYouExtractor
import com.pltmustafa.pltstream.extractors.SelcukFlixExtractor
import com.pltmustafa.pltstream.extractors.SiteExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PLTStream : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "plt-stream"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private val apiKey = "8c598c9af9b0badc281e95b1890834bc"
    private val imageBaseUrl = "https://image.tmdb.org/t/p/w500"

    private val extractors: List<SiteExtractor> = listOf(
        FilmModuExtractor(),
        FilmekseniExtractor(),
        FullHDFilmizleseneExtractor(),
        HDFilmCehennemiExtractor(),
        DiziBalExtractor(),
        DiziBoxExtractor(),
        DiziFilmExtractor(),
        DizillaExtractor(),
        DiziPalExtractor(),
        DiziYouExtractor(),
        SelcukFlixExtractor(),
        YabanciDiziExtractor()
    )

    override val mainPage = mainPageOf(
        "trending/movie/day" to "Trend Filmler",
        "trending/tv/day" to "Trend Diziler",
        "movie/popular" to "Popüler Filmler",
        "tv/popular" to "Popüler Diziler",
        "movie/top_rated" to "En Çok Oy Alan Filmler",
        "tv/top_rated" to "En Çok Oy Alan Diziler",
        "movie/now_playing" to "Vizyondaki Filmler",
        "movie/upcoming" to "Pek Yakında",
        "tv/airing_today" to "Bugün Yayınlanan Diziler",
        "tv/on_the_air" to "Yayında Olan Diziler",
        "discover/movie?with_genres=28" to "Aksiyon Filmleri",
        "discover/movie?with_genres=12" to "Macera Filmleri",
        "discover/movie?with_genres=16" to "Animasyon Filmleri",
        "discover/movie?with_genres=35" to "Komedi Filmleri",
        "discover/movie?with_genres=80" to "Suç Filmleri",
        "discover/movie?with_genres=99" to "Belgeseller",
        "discover/movie?with_genres=18" to "Dram Filmleri",
        "discover/movie?with_genres=10751" to "Aile Filmleri",
        "discover/movie?with_genres=14" to "Fantastik Filmler",
        "discover/movie?with_genres=36" to "Tarih Filmleri",
        "discover/movie?with_genres=27" to "Korku Filmleri",
        "discover/movie?with_genres=10402" to "Müzik Filmleri",
        "discover/movie?with_genres=9648" to "Gizem Filmleri",
        "discover/movie?with_genres=10749" to "Romantik Filmler",
        "discover/movie?with_genres=878" to "Bilim Kurgu Filmleri",
        "discover/movie?with_genres=10770" to "TV Filmleri",
        "discover/movie?with_genres=53" to "Gerilim Filmleri",
        "discover/movie?with_genres=10752" to "Savaş Filmleri",
        "discover/movie?with_genres=37" to "Vahşi Batı Filmleri"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val sep = if (request.data.contains("?")) "&" else "?"
        val url = "$mainUrl/${request.data}${sep}api_key=$apiKey&language=tr-TR&page=$page"
        val response = app.get(url).text
        val parsed = parseJson<TmdbSearchResponse>(response)

        val homeList = parsed.results?.mapNotNull { item ->
            val title = item.title ?: item.name ?: return@mapNotNull null
            val id = item.id ?: return@mapNotNull null
            val isMovie = item.media_type == "movie" || request.data.contains("movie")

            val dataUrl = MetaData(id.toString(), if(isMovie) "movie" else "tv").toJson()

            newTvSeriesSearchResponse(title, dataUrl, if(isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = if (item.poster_path != null) "$imageBaseUrl${item.poster_path}" else null
            }
        } ?: emptyList()

        return newHomePageResponse(request.name, homeList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/multi?api_key=$apiKey&language=tr-TR&query=$query&page=1"
        val response = app.get(url).text
        val parsed = parseJson<TmdbSearchResponse>(response)

        return parsed.results?.filter { it.media_type == "movie" || it.media_type == "tv" }?.mapNotNull { item ->
            val title = item.title ?: item.name ?: return@mapNotNull null
            val id = item.id ?: return@mapNotNull null
            val isMovie = item.media_type == "movie"
            val dataUrl = MetaData(id.toString(), if(isMovie) "movie" else "tv").toJson()

            newTvSeriesSearchResponse(title, dataUrl, if(isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = if (item.poster_path != null) "$imageBaseUrl${item.poster_path}" else null
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val meta = parseJson<MetaData>(url)
        val detailsUrl = "$mainUrl/${meta.type}/${meta.id}?api_key=$apiKey&language=tr-TR&include_video_language=tr,en&append_to_response=external_ids,credits,videos,recommendations"
        val response = app.get(detailsUrl).text
        val item = parseJson<TmdbDetails>(response)

        val title = item.title ?: item.name ?: return null
        val poster = if (item.poster_path != null) "$imageBaseUrl${item.poster_path}" else null
        val background = if (item.backdrop_path != null) "https://image.tmdb.org/t/p/original${item.backdrop_path}" else null
        val plot = item.overview
        val year = (item.release_date ?: item.first_air_date)?.split("-")?.firstOrNull()?.toIntOrNull()
        val imdbId = item.external_ids?.imdb_id ?: item.imdb_id

        val actors = item.credits?.cast?.mapNotNull {
            if (it.name != null) {
                Actor(it.name, if (it.profile_path != null) "$imageBaseUrl${it.profile_path}" else null)
            } else null
        }
        Log.d("plt-stream", "TMDB API URL: $detailsUrl")

        val tags = item.genres?.mapNotNull { it.name }
        val duration = item.runtime ?: item.episode_run_time?.firstOrNull()
        val scoreObj = item.vote_average?.toString()?.let { Score.from10(it) }

        val recs = item.recommendations?.results?.mapNotNull { rec ->
            val recTitle = rec.title ?: rec.name ?: return@mapNotNull null
            val recId = rec.id ?: return@mapNotNull null
            val recIsMovie = rec.media_type == "movie"
            val recUrl = MetaData(recId.toString(), if (recIsMovie) "movie" else "tv").toJson()

            if (recIsMovie) {
                newMovieSearchResponse(recTitle, recUrl, TvType.Movie) {
                    this.posterUrl = if (rec.poster_path != null) "$imageBaseUrl${rec.poster_path}" else null
                }
            } else {
                newTvSeriesSearchResponse(recTitle, recUrl, TvType.TvSeries) {
                    this.posterUrl = if (rec.poster_path != null) "$imageBaseUrl${rec.poster_path}" else null
                }
            }
        }

        if (meta.type == "movie") {
            val loadData = LoadData(meta.id, meta.type, imdbId, title, year, null, null).toJson()
            return newMovieLoadResponse(title, url, TvType.Movie, loadData) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = plot
                this.tags = tags
                this.duration = duration
                this.score = scoreObj
                this.recommendations = recs
                if (!actors.isNullOrEmpty()) addActors(actors)
            }
        } else {
            val defaultNameRegex = Regex("^(?:\\d+\\.\\s*Bölüm|Bölüm\\s*\\d+|Episode\\s*\\d+)$", RegexOption.IGNORE_CASE)
            val episodes = item.seasons?.filter { it.season_number != null && it.season_number > 0 }
                ?.amap { season ->
                    val seasonUrl = "$mainUrl/tv/${meta.id}/season/${season.season_number}?api_key=$apiKey&language=tr-TR"
                    val seasonResponse = app.get(seasonUrl).text
                    val seasonData = parseJson<TmdbSeasonDetails>(seasonResponse)

                    seasonData.episodes?.map { ep ->
                        val loadData = LoadData(meta.id, meta.type, imdbId, title, year, season.season_number, ep.episode_number ?: 1).toJson()
                        newEpisode(loadData) {
                            this.name = if (ep.name != null && ep.name.matches(defaultNameRegex)) null else ep.name
                            this.season = season.season_number
                            this.episode = ep.episode_number
                            this.posterUrl = if (ep.still_path != null) "$imageBaseUrl${ep.still_path}" else null
                            this.description = ep.overview
                        }
                    } ?: emptyList()
                }?.flatten() ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = plot
                this.tags = tags
                this.duration = duration
                this.score = scoreObj
                this.recommendations = recs
                if (!actors.isNullOrEmpty()) addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)

        Log.d("plt-stream", "loadLinks -> title: ${loadData.title}, imdbId: ${loadData.imdbId}, S${loadData.season}E${loadData.episode}")

        coroutineScope {
            extractors.map { extractor ->
                async {
                    try {
                        Log.d("plt-stream", "${extractor.name}: Başlatılıyor...")
                        extractor.extract(
                            tmdbId = loadData.id,
                            imdbId = loadData.imdbId,
                            title = loadData.title,
                            year = loadData.year,
                            season = loadData.season,
                            episode = loadData.episode,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                        Log.d("plt-stream", "${extractor.name}: Tamamlandı")
                    } catch (e: Exception) {
                        Log.e("plt-stream", "${extractor.name}: Hata -> ${e.message}")
                    }
                }
            }.awaitAll()
        }

        return true
    }

    data class MetaData(
        val id: String,
        val type: String
    )

    data class LoadData(
        val id: String,
        val type: String,
        val imdbId: String?,
        val title: String?,
        val year: Int?,
        val season: Int?,
        val episode: Int?
    )

    data class TmdbSearchResponse(
        val results: List<TmdbItem>?
    )

    data class TmdbItem(
        val id: Int?,
        val title: String?,
        val name: String?,
        val media_type: String?,
        val poster_path: String?
    )

    data class TmdbDetails(
        val title: String?,
        val name: String?,
        val poster_path: String?,
        val backdrop_path: String?,
        val overview: String?,
        val release_date: String?,
        val first_air_date: String?,
        val vote_average: Double?,
        val seasons: List<TmdbSeason>?,
        val external_ids: ExternalIds?,
        val imdb_id: String?,
        val credits: TmdbCredits?,
        val videos: TmdbVideos?,
        val genres: List<TmdbGenre>?,
        val runtime: Int?,
        val episode_run_time: List<Int>?,
        val recommendations: TmdbRecommendations?
    )

    data class TmdbGenre(
        val name: String?
    )

    data class TmdbRecommendations(
        val results: List<TmdbItem>?
    )

    data class TmdbCredits(
        val cast: List<TmdbCast>?
    )

    data class TmdbCast(
        val name: String?,
        val profile_path: String?
    )

    data class TmdbVideos(
        val results: List<TmdbVideo>?
    )

    data class TmdbVideo(
        val key: String?,
        val site: String?,
        val type: String?
    )

    data class ExternalIds(
        val imdb_id: String?
    )

    data class TmdbSeason(
        val season_number: Int?
    )

    data class TmdbSeasonDetails(
        val episodes: List<TmdbEpisode>?
    )

    data class TmdbEpisode(
        val name: String?,
        val episode_number: Int?,
        val still_path: String?,
        val overview: String?
    )
}
