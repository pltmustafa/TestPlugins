// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.nikyokki

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResult(
    @JsonProperty("response") val response: String?
)

data class SearchData(
    @JsonProperty("state")   val state: Boolean?           = null,
    @JsonProperty("result")  val result: List<SearchItem>? = arrayListOf(),
    @JsonProperty("message") val message: String?          = null,
    @JsonProperty("html")    val html: String?             = null
)

data class SearchItem(
    @JsonProperty("used_slug")         val slug: String?   = null,
    @JsonProperty("object_name")       val title: String?  = null,
    @JsonProperty("object_poster_url") val poster: String? = null,
    @JsonProperty("used_type")         val type: String? = null
)

data class Root(
    @JsonProperty("contentItem") val contentItem: ContentItem,
    @JsonProperty("RelatedResults") val relatedResults: RelatedResults
)

data class ContentItem(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("original_title") val originalTitle: String?,
    @JsonProperty("culture_title") val cultureTitle: String?,
    @JsonProperty("release_year") val releaseYear: Int?,
    @JsonProperty("total_minutes") val totalMinutes: Int?,
    @JsonProperty("poster_url") val posterUrl: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("categories") val categories: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("imdb_point") val imdbPoint: Double?
)

data class RelatedResults(
    @JsonProperty("getContentTrailers") val getContentTrailers: ContentTrailers?,
    @JsonProperty("getMovieCastsById") val getMovieCastsById: MovieCast?,
    @JsonProperty("getMoviePartsById") val getMoviePartsById: MovieParts?,
    @JsonProperty("getMoviePartSourcesById_") val getMoviePartSourcesById: MoviePartsSource?,
    @JsonProperty("getSerieSeasonAndEpisodes") val getSerieSeasonAndEpisodes: Seasons?,
    @JsonProperty("getEpisodeSources") val getEpisodeSources: EpisodeSources?,
)

data class ContentTrailers(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<TrailerResult>?
)

data class TrailerResult(
    @JsonProperty("raw_url") val rawUrl: String?,
    @JsonProperty("raw") val raw: String?
)

data class MovieCast(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<MovieResult>?
)

data class MovieResult(
    @JsonProperty("name") val name: String?,
    @JsonProperty("cast_image") val castImage: String?
)

data class MovieParts(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<MoviePartsResult>?
)

data class MoviePartsResult(
    @JsonProperty("id") val id: Int?,
)

data class MoviePartsSource(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<MoviePartsSourceResult>?
)

data class MoviePartsSourceResult(
    @JsonProperty("source_content") val sourceContent: String?,
    @JsonProperty("quality_name") val qualityName: String?,
)

data class SourceItem(
    val sourceContent: String,
    val quality: String
)

data class ListItems(
    @JsonProperty("result") val result: List<ContentItem>
)

data class Episode(
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("episode_no") val episodeNo: Int?,
    @JsonProperty("episode_text") val epText: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
)

data class Season(
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("season_text") val seText: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("episodes") val episodes: List<Episode>?,
)

data class Seasons(
    @JsonProperty("result") val seasons: List<Season>?
)

data class EpisodeSources(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<MoviePartsSourceResult>?
)




