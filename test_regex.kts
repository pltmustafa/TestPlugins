import java.io.File

val html = File("/tmp/movie.html").readText()

fun extractJsonFromNextJs(html: String, key: String): String? {
    val regex1 = Regex("""\"$key\":(\{.*?\}(?=\,\"|\}\]))""")
    val match1 = regex1.find(html)
    println("Regex1 matched: " + (match1 != null))

    val regex2 = Regex("""\\\"$key\\\":(\{.*?\}(?=\,\\\"|\}\]))""")
    val match2 = regex2.find(html)
    println("Regex2 matched: " + (match2 != null))
    if (match2 != null) {
        println("Regex2 group 1: " + match2.groupValues[1].take(100))
    }
}

extractJsonFromNextJs(html, "movie")
