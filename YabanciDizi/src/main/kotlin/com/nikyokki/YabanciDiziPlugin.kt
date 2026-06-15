package com.nikyokki

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YabanciDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YabanciDizi())
    }
}