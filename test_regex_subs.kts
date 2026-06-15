val scriptData = """{"skin":{"name":"alaska","url":"http:\\/\\/streammovie.club\\/fireplayer\\/player\\/assets\\/jwplayer\\/netflix.css"},"logo":{"file":"","link":"","position":"top-right","active":false},"tracks":[{"kind":"captions","file":"https:\\/\\/s9.photomkl.org\\/p\\/qX1h6j-_6yjERDkLsgd0OEE_ahBX4OfNrBpKvm1tTISPs2sfs1a_ZRmmpc0KreYL0PlK4Oco0VcegPbDueXyqjyiei17o1FZT4L1dGtOcyJlhTP1IBFx2fjp7hsOyo8j.jpg","label":"Turkish 1","language":"","default":true}],"captions":{"fontSize":"20","fontfamily":"Trebuchet MS"}"""

val subtitlesMatch = Regex("""tracks"\s*:\s*\[(.*?)\]""").find(scriptData)
if (subtitlesMatch != null) {
    println("Found tracks!")
} else {
    println("Failed to find tracks")
}
