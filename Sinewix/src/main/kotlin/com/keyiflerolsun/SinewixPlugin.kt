package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SinewixPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added here in this manner:
        registerMainAPI(Sinewix())
    }
}
