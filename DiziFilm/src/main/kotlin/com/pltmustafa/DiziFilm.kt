package com.pltmustafa

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class DiziFilm : MainAPI() {
    override var mainUrl = "https://dizifilm.life"
    override var name = "DiziFilm"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override val mainPage = mainPageOf(
        "/api/movies?limit=20&page=" to "Filmler",
        "/api/series/latest?limit=20&page=" to "Diziler"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl${request.data}$page"
        val response = app.get(url).text
        val parsed = mapper.readValue<MoviesApiResponse>(response)
        
        val itemsList = (parsed.movies ?: emptyList()) + (parsed.series ?: emptyList())
        val homeItems = itemsList.mapNotNull { item ->
            val isSeries = request.name == "Diziler" || url.contains("/series")
            val link = if (isSeries) {
                "$mainUrl/dizi/${item.slug}"
            } else {
                "$mainUrl/film/${item.slug}"
            }
            
            newMovieSearchResponse(
                name = item.title ?: return@mapNotNull null,
                url = link,
                type = if (isSeries) TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = item.poster_url?.replace(".avif", ".jpg")
            }
        }

        return newHomePageResponse(request.name, homeItems, hasNext = (parsed.totalPages ?: 1) > page)
    }

    private fun MovieItem.toSearchResponse(): SearchResponse? {
        val title = this.title ?: return null
        val href = "$mainUrl/film/${this.slug}"
        val poster = this.poster_url

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = this@toSearchResponse.year
            if (this@toSearchResponse.imdb_rating != null) {
                this.score = Score.from10(this@toSearchResponse.imdb_rating.toString())
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search?q=$query"
        val response = app.get(url, headers = mapOf("Accept" to "application/json")).text
        
        val searchRes = mapper.readValue<SearchApiResponse>(response)
        
        return searchRes.results?.mapNotNull { item ->
            val isMovie = item.content_type == "movie"
            val href = if (isMovie) "$mainUrl/film/${item.slug}" else "$mainUrl/dizi/${item.slug}"
            val title = item.title ?: return@mapNotNull null
            
            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = item.poster_url?.replace(".avif", ".jpg")
                    this.year = item.year
                    if (item.imdb_rating != null) {
                        this.score = Score.from10(item.imdb_rating.toString())
                    }
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = item.poster_url?.replace(".avif", ".jpg")
                    this.year = item.year
                    if (item.imdb_rating != null) {
                        this.score = Score.from10(item.imdb_rating.toString())
                    }
                }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d("DiziFilm", "load called with url: $url")
        val html = app.get(url).text
        Log.d("DiziFilm", "load html length: ${html.length}")
        val isMovie = url.contains("/film/")
        
        if (isMovie) {
            val movieJson = extractJsonFromNextJs(html, "movie")
            Log.d("DiziFilm", "movieJson extracted: ${movieJson?.take(100)}...")
            if (movieJson == null) return null
            
            val movie = try { mapper.readValue<MoviePayload>(movieJson) } catch(e:Exception) { Log.e("DiziFilm", "movie JSON parse error", e); null } ?: return null
            
            val title = movie.title
            Log.d("DiziFilm", "movie title: $title")
            if (title == null) return null
            
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = movie.poster_url?.replace(".avif", ".jpg")
                this.year = movie.year
                this.plot = movie.description
                if (movie.imdb_rating != null) {
                    this.score = Score.from10(movie.imdb_rating.toString())
                }
                this.duration = movie.duration
            }
        } else {
            val seriesJson = extractJsonFromNextJs(html, "series")
            Log.d("DiziFilm", "seriesJson extracted: ${seriesJson?.take(100)}...")
            if (seriesJson == null) return null
            
            val series = try { mapper.readValue<SeriesPayload>(seriesJson) } catch(e:Exception) { Log.e("DiziFilm", "series JSON parse error", e); null } ?: return null
            
            val seasonsJson = extractJsonArrayFromNextJs(html, "seasonsWithEpisodes")
            Log.d("DiziFilm", "seasonsJson extracted: ${seasonsJson?.take(100)}...")
            val episodesList = mutableListOf<Episode>()
            
            if (seasonsJson != null) {
                try {
                    val seasons = mapper.readValue<List<SeasonWithEpisodes>>(seasonsJson)
                    for (season in seasons) {
                        val sNum = season.season_number ?: continue
                        season.episodes?.forEach { ep ->
                            val epNum = ep.episode_number ?: return@forEach
                            val epUrl = "$url/sezon-$sNum/bolum-$epNum"
                            episodesList.add(newEpisode(epUrl) {
                                this.name = ep.title ?: "$sNum. Sezon $epNum. Bölüm"
                                this.season = sNum
                                this.episode = epNum
                            })
                        }
                    }
                } catch(e: Exception) {
                    Log.e("DiziFilm", "seasons JSON parse error", e)
                }
            }
            
            val title = series.title
            Log.d("DiziFilm", "series title: $title")
            if (title == null) return null
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = series.poster_url?.replace(".avif", ".jpg")
                this.year = series.start_year
                this.plot = series.description
                if (series.imdb_rating != null) {
                    this.score = Score.from10(series.imdb_rating.toString())
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.e("DiziFilm", "loadLinks called with data: $data")
        val html = app.get(data).text
        Log.e("DiziFilm", "loadLinks html length: ${html.length}")
        val vidlopUrls = mutableListOf<Pair<String, String>>() // URL to Name
        
        if (data.contains("/film/")) {
            val movieJson = extractJsonFromNextJs(html, "movie")
            Log.d("DiziFilm", "loadLinks movieJson extracted: ${movieJson != null}")
            if (movieJson != null) {
                try {
                    val movie = mapper.readValue<MoviePayload>(movieJson)
                    Log.d("DiziFilm", "loadLinks movie parsed, parts count: ${movie.parts?.size}")
                    movie.parts?.forEach { part ->
                        if (!part.url.isNullOrEmpty()) {
                            if (part.url.contains("vidlop.com")) {
                                val name = "Vidlop ${part.language ?: ""} ${part.quality ?: ""}".trim()
                                Log.d("DiziFilm", "Found Vidlop URL for movie: ${part.url} -> $name")
                                vidlopUrls.add(part.url to name)
                            } else {
                                val name = "Server ${part.language ?: ""} ${part.quality ?: ""}".trim()
                                Log.d("DiziFilm", "Found other URL for movie: ${part.url}")
                                loadExtractor(part.url, "$mainUrl/", subtitleCallback, callback)
                            }
                        }
                    }
                } catch(e: Exception) {
                    Log.e("DiziFilm", "loadLinks movie parse error", e)
                }
            }
        } else if (data.contains("/sezon-")) {
            // Episode page
            val episodeJson = extractJsonFromNextJs(html, "episode")
            Log.d("DiziFilm", "loadLinks episodeJson extracted: ${episodeJson != null}")
            if (episodeJson != null) {
                try {
                    val ep = mapper.readValue<EpisodeItem>(episodeJson)
                    Log.d("DiziFilm", "loadLinks episode parsed")
                    listOfNotNull(ep.embed_player_url_1, ep.embed_player_url_2, ep.embed_player_url_3)
                        .forEachIndexed { index, url ->
                            if (url.contains("vidlop.com")) {
                                Log.d("DiziFilm", "Found Vidlop URL for episode: $url")
                                vidlopUrls.add(url to "Vidlop Server ${index + 1}")
                            } else {
                                Log.d("DiziFilm", "Found other URL for episode: $url")
                                loadExtractor(url, "$mainUrl/", subtitleCallback, callback)
                            }
                        }
                } catch(e: Exception) {
                    Log.e("DiziFilm", "loadLinks episode parse error", e)
                }
            }
        }

        Log.e("DiziFilm", "Total Vidlop URLs to process: ${vidlopUrls.size}")

        // Extract Vidlop Links
        for ((vidlopUrl, sourceName) in vidlopUrls) {
            try {
                Log.e("DiziFilm", "Fetching Vidlop URL: $vidlopUrl")
                val vidlopHtml = app.get(vidlopUrl, referer = mainUrl).text
                Log.e("DiziFilm", "Vidlop HTML length: ${vidlopHtml.length}")
                
                val scriptData = getAndUnpack(vidlopHtml)
                Log.e("DiziFilm", "Unpacked script length: ${scriptData.length}")
                
                // Parse unpacked script for m3u8
                var m3u8Url = Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(scriptData)?.groupValues?.get(1)
                
                if (m3u8Url == null) {
                    val vidlopId = Regex("""video/([a-zA-Z0-9]+)""").find(vidlopUrl)?.groupValues?.get(1)
                    if (vidlopId != null) {
                        Log.e("DiziFilm", "Fetching Vidlop API for ID: $vidlopId")
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
                        Log.e("DiziFilm", "Vidlop API response: $apiResponse")
                        
                        val secureLinkMatch = Regex("""\"securedLink\"\:\"([^\"]+)\"""").find(apiResponse)
                        if (secureLinkMatch != null) {
                            m3u8Url = secureLinkMatch.groupValues[1].replace("\\/", "/")
                        }
                    }
                }
                
                if (m3u8Url != null) {
                    Log.e("DiziFilm", "Found m3u8 URL: $m3u8Url")
                    callback.invoke(
                        newExtractorLink(
                            source = sourceName,
                            name = sourceName,
                            url = m3u8Url,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = vidlopUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                } else {
                    Log.e("DiziFilm", "m3u8 URL NOT found in unpacked script!")
                }
                
                // Try to find subtitles
                val subtitlesMatch = Regex("""tracks["']?\s*:\s*\[(.*?)\]""").find(scriptData)
                if (subtitlesMatch != null) {
                    val tracks = subtitlesMatch.groupValues[1]
                    Log.e("DiziFilm", "Found subtitles tracks array length: ${tracks.length}")
                    val fileMatch = Regex("""file["']?\s*:\s*["']([^"']+)["']""").findAll(tracks)
                    val labelMatch = Regex("""label["']?\s*:\s*["']([^"']+)["']""").findAll(tracks)
                    
                    val files = fileMatch.map { it.groupValues[1] }.toList()
                    val labels = labelMatch.map { it.groupValues[1] }.toList()
                    
                    for (i in files.indices) {
                        val subUrl = files[i]
                        val subLang = labels.getOrNull(i) ?: "Türkçe"
                        Log.e("DiziFilm", "Found subtitle: $subLang -> $subUrl")
                        // Sometimes Vidlop subtitles are images (thumbnails) masked in the 'tracks' array. We only want .vtt or .srt, or perhaps any valid subtitle URL. Let's add them all but log them.
                        // Wait, looking at the unpacked script, the subtitle file URL is .jpg because it's a thumbnail sprite! Vidlop puts thumbnails in tracks too!
                        // We must only extract subtitles that are text files, or just check the 'kind' field.
                        if (subUrl.endsWith(".vtt") || subUrl.endsWith(".srt") || subUrl.endsWith(".txt")) {
                            subtitleCallback.invoke(SubtitleFile(subLang, subUrl.replace("\\/", "/")))
                        }
                    }
                } else {
                    Log.e("DiziFilm", "Subtitles tracks NOT found")
                }
                
            } catch (e: Exception) {
                Log.e("DiziFilm", "Vidlop extractor error for $vidlopUrl", e)
            }
        }

        return true
    }
    
    // Utilities to extract JSON from Next.js RSC Push payload
    private fun extractJsonFromNextJs(html: String, key: String): String? {
        return extractJsonByKey(html, key, false)
    }
    
    private fun extractJsonArrayFromNextJs(html: String, key: String): String? {
        return extractJsonByKey(html, key, true)
    }

    private fun extractJsonByKey(html: String, key: String, isArray: Boolean): String? {
        val searchStr = "\\\"$key\\\":${if (isArray) "[" else "{"}"
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
                if (c == (if (isArray) '[' else '{')) depth++
                else if (c == (if (isArray) ']' else '}')) depth--
                
                if (depth == 0) {
                    val jsonStr = html.substring(jsonStart, i + 1)
                    return jsonStr.replace("\\\"", "\"").replace("\\\\", "\\")
                }
            }
        }
        return null
    }
}
