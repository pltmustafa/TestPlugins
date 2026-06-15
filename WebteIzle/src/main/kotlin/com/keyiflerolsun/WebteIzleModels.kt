// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class DataAlternatif(
    @JsonProperty("status") val status: String,
    @JsonProperty("data")   val data: List<EmbedData>,
)


data class EmbedData(
    @JsonProperty("id")       val id: Int,
    @JsonProperty("baslik")   val baslik: String,
    @JsonProperty("kalitesi") val kalitesi: Int,
)

data class Track(
    @JsonProperty("kind")  val kind: String?,
    @JsonProperty("file")  val file: String?,
    @JsonProperty("label") val label: String?,
)