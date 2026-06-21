package com.pltmustafa

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziFilmPlugin: Plugin() {
    companion object {
        var appContext: Context? = null
    }

    override fun load(context: Context) {
        appContext = context
        registerMainAPI(DiziFilm())
    }
}
