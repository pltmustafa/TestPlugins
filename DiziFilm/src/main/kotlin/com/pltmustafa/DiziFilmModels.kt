package com.pltmustafa

import com.fasterxml.jackson.annotation.JsonProperty

// API Search Responses
data class SearchApiResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("results") val results: List<SearchResult>? = null
)

data class SearchResult(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("poster_url") val poster_url: String? = null,
    @JsonProperty("imdb_rating") val imdb_rating: Double? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("content_type") val content_type: String? = null // "movie" veya "series"
)

// API Movies/Series Pagination Responses
data class MoviesApiResponse(
    @JsonProperty("movies") val movies: List<MovieItem>? = null,
    @JsonProperty("series") val series: List<MovieItem>? = null,
    @JsonProperty("totalPages") val totalPages: Int? = null
)

data class MovieItem(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("poster_url") val poster_url: String? = null,
    @JsonProperty("imdb_rating") val imdb_rating: Double? = null,
    @JsonProperty("year") val year: Int? = null
)

// In-Page JSON Data Models (from RSC Payload)
data class MoviePayload(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("duration") val duration: Int? = null,
    @JsonProperty("poster_url") val poster_url: String? = null,
    @JsonProperty("imdb_rating") val imdb_rating: Double? = null,
    @JsonProperty("parts") val parts: List<PartItem>? = null
)

data class PartItem(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("quality") val quality: String? = null
)

data class SeriesPayload(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("poster_url") val poster_url: String? = null,
    @JsonProperty("imdb_rating") val imdb_rating: Double? = null,
    @JsonProperty("start_year") val start_year: Int? = null
)

data class SeasonWithEpisodes(
    @JsonProperty("season_number") val season_number: Int? = null,
    @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null
)

data class EpisodeItem(
    @JsonProperty("episode_number") val episode_number: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("embed_player_url_1") val embed_player_url_1: String? = null,
    @JsonProperty("embed_player_url_2") val embed_player_url_2: String? = null,
    @JsonProperty("embed_player_url_3") val embed_player_url_3: String? = null
)
