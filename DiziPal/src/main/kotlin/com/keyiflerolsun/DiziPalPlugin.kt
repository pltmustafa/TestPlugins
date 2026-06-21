package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziPalPlugin: Plugin() {
    companion object {
        var appContext: Context? = null
    }

    override fun load(context: Context) {
        appContext = context
        registerMainAPI(DiziPal())
        registerExtractorAPI(DizipalPlayer())
    }
}