// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class Kaynak(
    @JsonProperty("status") val status: String,
    @JsonProperty("data") val data: List<Veri>,
)

data class Veri(
    @JsonProperty("baslik") val baslik: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("kalite") val kalite: Int,
)

data class AspData(
    val alternatif : String,
    val embed : String
)

data class SearchAjaxResponse(
    @JsonProperty("status") val status: String?,
    @JsonProperty("results") val results: SearchResultsData?
)

data class SearchResultsData(
    @JsonProperty("diziler") val diziler: SearchCategoryData?,
    @JsonProperty("filmler") val filmler: SearchCategoryData?
)

data class SearchCategoryData(
    @JsonProperty("results") val results: List<SearchItemData>?
)

data class SearchItemData(
    @JsonProperty("title") val title: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("image") val image: String?
)