package com.nikyokki

import com.fasterxml.jackson.annotation.JsonProperty

data class Cipher(
    @JsonProperty("ct") val ct: String,
    @JsonProperty("iv") val iv: String,
    @JsonProperty("s") val s: String,
)

data class Schedule(
    @JsonProperty("client") val client: String,
    @JsonProperty("schedule") val schedule: List<AdSchedule>
)

data class AdSchedule(
    @JsonProperty("offset") val offset: String,
    @JsonProperty("tag") val tag: String,
    @JsonProperty("skipoffset") val skipOffset: String,
    @JsonProperty("skipmessage") val skipMessage: String
)

data class JsonData(
    @JsonProperty("schedule") val schedule: Schedule,
    @JsonProperty("adsecond") val adSecond: Int,
    @JsonProperty("bannerad") val bannerAd: List<String>,
    @JsonProperty("title") val title: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("video_location") val videoLocation: String,
    @JsonProperty("images") val images: String,
    @JsonProperty("watermark") val watermark: String,
    @JsonProperty("link") val link: String,
    @JsonProperty("vast") val vast: String,
    @JsonProperty("dwlink") val dwLink: String,
    @JsonProperty("exit") val exit: Boolean,
    @JsonProperty("intro") val intro: String?,
    @JsonProperty("outro") val outro: String?,
    @JsonProperty("video_id") val videoId: Int,
    @JsonProperty("siteurl") val siteUrl: String?,
    @JsonProperty("urls") val urls: String?,
    @JsonProperty("referer") val referer: String,
    @JsonProperty("sitex") val siteX: List<String>,
    @JsonProperty("dws") val dws: Boolean,
    @JsonProperty("download") val download: Boolean
)

data class TVSeries(
    @JsonProperty("@context") val context: String,
    @JsonProperty("@type") val type: String,
    val name: String,
    val image: String,
    val datePublished: String,
    val actor: List<Any>,
    val description: String,
    val potentialAction: WatchAction,
    val countryOfOrigin: Country,
    val trailer: VideoObject,
    val timeRequired: String,
    val containsSeason: List<TVSeason>,
    val aggregateRating: AggregateRating,
    val director: Person,
    val review: Review
)

data class WatchAction(
    @JsonProperty("@type") val type: String,
    val target: String
)

data class Country(
    @JsonProperty("@type") val type: String,
    val name: String
)

data class VideoObject(
    @JsonProperty("@type") val type: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val thumbnail: ImageObject,
    val timeRequired: String,
    val uploadDate: String,
    val embedUrl: String,
    val duration: String,
    val publisher: Organization,
    val interactionCount: String
)

data class ImageObject(
    @JsonProperty("@type") val type: String,
    @JsonProperty("contentUrl")
    var contentUrl: String? = null,
    @JsonProperty("url")
    var url: String? = null
) {
    init {
        if (url != null && contentUrl == null) {
            contentUrl = url
        }
    }
}

data class Organization(
    @JsonProperty("@type") val type: String,
    val name: String,
    val logo: ImageObject
)

data class TVSeason(
    @JsonProperty("@type") val type: String,
    val seasonNumber: String,
    val episode: List<TVEpisode>
)


data class TVEpisode(
    @JsonProperty("@type") val type: String,
    val episodeNumber: String,
    val name: String,
    val datePublished: String,
    val url: String
)

data class AggregateRating(
    @JsonProperty("@type") val type: String,
    val ratingValue: String,
    val bestRating: String,
    val worstRating: String,
    val ratingCount: String
)

data class Person(
    @JsonProperty("@type") val type: String,
    val name: String
)

data class Review(
    @JsonProperty("@type") val type: String,
    val author: Person,
    val datePublished: String,
    val reviewBody: String
)

data class ResultItem(
    val s_type: String,
    val s_link: String,
    val s_name: String,
    val s_image: String,
    val s_year: String
)

data class Data(
    val result: List<ResultItem>,
    val type: String?
)

data class JsonResponse(
    val success: Int,
    val data: Data,
    val type: String?
)

data class Series(
    @JsonProperty("data") val data: String,
    @JsonProperty("success") val success: Int,
)