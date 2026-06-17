package com.pltmustafa

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DiziBalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziBal())
    }
}
