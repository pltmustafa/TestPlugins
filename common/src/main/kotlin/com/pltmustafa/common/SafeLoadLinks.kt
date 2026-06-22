package com.pltmustafa.common

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorLink

suspend fun safeLoadLinks(
    context: Context?,
    pluginName: String,
    data: String,
    callback: (ExtractorLink) -> Unit,
    block: suspend (safeCallback: (ExtractorLink) -> Unit) -> Unit
): Boolean {
    Log.d("SafeLoadLinks", "[$pluginName] safeLoadLinks started for data: $data")
    try {
        var linksFound = 0
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            Log.d("SafeLoadLinks", "[$pluginName] Found link: ${link.url}")
            if (link.url.isNotBlank()) {
                linksFound++
                callback.invoke(link)
            }
        }

        block(safeCallback)

        Log.d("SafeLoadLinks", "[$pluginName] block execution finished. Links found: $linksFound")
        if (linksFound == 0) {
            throw Exception("Sayfada hiçbir link bulunamadı, site yapısı değişmiş olabilir.")
        }
        return true
    } catch (e: Exception) {
        Log.e("SafeLoadLinks", "[$pluginName] Exception caught: ${e.message}", e)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d("SafeLoadLinks", "[$pluginName] Triggering ErrorUtils.showPluginError")
            try {
                ErrorUtils.showPluginError(context, pluginName, "LOAD_LINKS", data)
            } catch (dialogEx: Exception) {
                Log.e("SafeLoadLinks", "[$pluginName] Error showing dialog: ${dialogEx.message}", dialogEx)
            }
        }, 500)
        throw com.lagradost.cloudstream3.ErrorLoadingException("Hata oluştu.")
    }
}
