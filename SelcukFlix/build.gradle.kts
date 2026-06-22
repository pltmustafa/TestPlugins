version = 8

cloudstream {
    authors     = listOf("nikyokki, pltmustafa")
    language    = "tr"
    description = "Selcukflix'te her türden en yeni ve en popüler dizi ve filmleri izlemenin keyfini çıkarın. Aksiyondan romantiğe, bilim kurgudan dramaya, geniş kütüphanemizde herkes için bir şey var."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=selcukflix.com&sz=%size%"
}