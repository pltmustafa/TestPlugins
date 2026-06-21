package com.nikyokki

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HDFilmIzlePlugin: Plugin() {
    companion object {
        var appContext: Context? = null
    }

    override fun load(context: Context) {
        appContext = context
        registerMainAPI(HDFilmIzle())
        registerExtractorAPI(VidRameExtractor())
    }
}