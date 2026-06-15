import com.fasterxml.jackson.annotation.JsonProperty

data class Video(
    val id: Int,
    val name: String,
    @JsonProperty("imdb_rating") val imdbRating: String?,
    val year: Int?,
    val slug: String,
    val lang: String,
    @JsonProperty("thumb_url") val thumbUrl: String,
    @JsonProperty("thumb_webp") val thumbWebp: String,
    @JsonProperty("genres_txt") val genresTxt: String,
    val images: List<Image>,
    val genres: List<Genre>
)

data class Image(
    val id: Int,
    val url: String,
    val hash: String,
    val sizes: Sizes,
    @JsonProperty("imageable_type") val imageableType: String,
    @JsonProperty("imageable_id") val imageableId: Int,
    val type: String,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

data class Sizes(
    val thumb: SizeUrls?,
    val watch: SizeUrls?,
    @JsonProperty("thumb-lg") val thumbLg: SizeUrls?,
    @JsonProperty("watch-lg") val watchLg: SizeUrls?,
    val backdrop: SizeUrls?,
    @JsonProperty("backdrop-lg") val backdropLg: SizeUrls?
)

data class SizeUrls(
    val url: String,
    val webp: String
)

data class Genre(
    val id: Int,
    val name: String,
    val suffix: String,
    val slug: String,
    val title: String,
    val desc: String,
    val svgcode: String?,  // svgcode can be null in your data
    val status: Int,
    @JsonProperty("videos_count") val videosCount: Int,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String,
    val pivot: Pivot
)

data class Pivot(
    @JsonProperty("videoable_type") val videoableType: String,
    @JsonProperty("video_id") val videoId: Int,
    @JsonProperty("videoable_id") val videoableId: Int
)