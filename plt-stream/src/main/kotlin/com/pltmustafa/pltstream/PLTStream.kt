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
        "discover/movie?with_watch_providers=8&watch_region=TR" to "Netflix Filmleri",
        "discover/tv?with_watch_providers=8&watch_region=TR" to "Netflix Dizileri",
        "discover/movie?with_watch_providers=337&watch_region=TR" to "Disney+ Filmleri",
        "discover/tv?with_watch_providers=337&watch_region=TR" to "Disney+ Dizileri",
        "discover/movie?with_watch_providers=119&watch_region=TR" to "Amazon Prime Filmleri",
        "discover/tv?with_watch_providers=119&watch_region=TR" to "Amazon Prime Dizileri",
        "discover/movie?with_watch_providers=2&watch_region=TR" to "Apple TV Filmleri",
        "discover/movie?with_watch_providers=1899&watch_region=TR" to "HBO Max Filmleri",
        "discover/tv?with_watch_providers=1899&watch_region=TR" to "HBO Max Dizileri",
        "discover/movie?with_watch_providers=11&watch_region=TR" to "MUBI Filmleri",
        "discover/tv?with_watch_providers=11&watch_region=TR" to "MUBI Dizileri"
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
        var plot = item.overview
        if (plot.isNullOrBlank()) {
            val enUrl = "$mainUrl/${meta.type}/${meta.id}?api_key=$apiKey&language=en-US"
            val enResponse = app.get(enUrl).text
            val enItem = parseJson<TmdbDetails>(enResponse)
            plot = enItem.overview
        }
        val year = (item.release_date ?: item.first_air_date)?.split("-")?.firstOrNull()?.toIntOrNull()
        val imdbId = item.external_ids?.imdb_id ?: item.imdb_id

        val actors = item.credits?.cast?.mapNotNull {
            if (it.name != null) {
                Actor(it.name, if (it.profile_path != null) "$imageBaseUrl${it.profile_path}" else null)
            } else null
        }
        Log.d("plt-stream", "TMDB API URL: $detailsUrl")

        val duration = item.runtime ?: item.episode_run_time?.firstOrNull()
        val scoreObj = item.vote_average?.toString()?.let { Score.from10(it) }
        
        val statusStr = item.status
        android.util.Log.d("plt-stream", "TMDB Status for $title: '$statusStr'")
        val statusTranslated = when (statusStr?.lowercase()) {
            "returning series", "ongoing" -> "Devam Ediyor"
            "ended", "canceled", "released" -> "Tamamlandı"
            "rumored", "planned", "in production", "post production", "pilot", "upcoming" -> "Yakında"
            else -> statusStr
        }
        val tags = item.genres?.mapNotNull { it.name }
        
        val showStatus = when (statusStr?.lowercase()) {
            "returning series", "ongoing" -> ShowStatus.Ongoing
            "ended", "canceled", "released" -> ShowStatus.Completed
            else -> null
        }
        val isComingSoon = statusStr?.lowercase() in listOf("rumored", "planned", "in production", "post production", "pilot", "upcoming")
        


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
                this.comingSoon = isComingSoon
                this.recommendations = recs
                if (!actors.isNullOrEmpty()) addActors(actors)
            }
        } else {
            val episodes = item.seasons?.filter { it.season_number != null && it.season_number > 0 }
                ?.amap { season ->
                    val seasonUrlTr = "$mainUrl/tv/${meta.id}/season/${season.season_number}?api_key=$apiKey&language=tr-TR"
                    val seasonUrlEn = "$mainUrl/tv/${meta.id}/season/${season.season_number}?api_key=$apiKey&language=en-US"
                    
                    var trResponse = ""
                    var enResponse = ""
                    kotlinx.coroutines.coroutineScope {
                        val trDef = async { app.get(seasonUrlTr).text }
                        val enDef = async { app.get(seasonUrlEn).text }
                        trResponse = trDef.await()
                        enResponse = enDef.await()
                    }
                    
                    val seasonDataTr = parseJson<TmdbSeasonDetails>(trResponse)
                    val seasonDataEn = parseJson<TmdbSeasonDetails>(enResponse)

                    seasonDataTr.episodes?.mapIndexed { index, epTr ->
                        val epEn = seasonDataEn.episodes?.getOrNull(index)
                        
                        val defaultNameRegex = Regex("^(?:(?:\\d+\\.\\s*)*Bölüm(?:\\s*\\d+)?|(?:\\d+\\.\\s*)*Episode(?:\\s*\\d+)?)$", RegexOption.IGNORE_CASE)
                        val trNameIsGeneric = epTr.name != null && epTr.name.matches(defaultNameRegex)
                        val enNameIsGeneric = epEn?.name != null && epEn.name.matches(defaultNameRegex)
                                var finalName = if (trNameIsGeneric || epTr.name.isNullOrBlank()) {
                            if (enNameIsGeneric) "Bölüm" else epEn?.name ?: "Bölüm"
                        } else {
                            epTr.name
                        }
                        
                        val prefixRegex = Regex("^(?:\\d+\\.\\s*)+")
                        if (finalName != null) {
                            finalName = finalName.replace(prefixRegex, "").trim()
                        }
                        
                        val finalOverview = if (epTr.overview.isNullOrBlank()) epEn?.overview else epTr.overview

                        val loadData = LoadData(meta.id, meta.type, imdbId, title, year, season.season_number, epTr.episode_number ?: 1).toJson()
                        newEpisode(loadData) {
                            this.name = finalName
                            this.season = season.season_number
                            this.episode = epTr.episode_number
                            this.posterUrl = if (epTr.still_path != null) "$imageBaseUrl${epTr.still_path}" else if (epEn?.still_path != null) "$imageBaseUrl${epEn.still_path}" else null
                            this.description = finalOverview
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
                this.comingSoon = isComingSoon
                this.showStatus = showStatus
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
        val recommendations: TmdbRecommendations?,
        val status: String?
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
