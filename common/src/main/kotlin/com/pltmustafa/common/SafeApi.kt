package com.pltmustafa.common

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.ErrorLoadingException

inline suspend fun safeGetMainPage(
    context: Context?,
    pluginName: String,
    data: String,
    block: suspend () -> HomePageResponse?
): HomePageResponse? {
    try {
        Log.d("SafeApi", "[$pluginName] safeGetMainPage started for url: $data")
        return block()
    } catch (e: Exception) {
        if (e.javaClass.simpleName.contains("CancellationException")) throw e
        
        Log.e("SafeApi", "[$pluginName] safeGetMainPage error: ${e.message}", e)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                ErrorUtils.showPluginError(context, pluginName, "MAIN_PAGE", data)
            } catch (dialogEx: Exception) {
                Log.e("SafeApi", "[$pluginName] Error showing dialog (MAIN_PAGE): ${dialogEx.message}", dialogEx)
            }
        }, 500)
        throw ErrorLoadingException("Hata oluştu.")
    }
}

inline suspend fun safeLoad(
    context: Context?,
    pluginName: String,
    data: String,
    block: suspend () -> LoadResponse?
): LoadResponse? {
    try {
        Log.d("SafeApi", "[$pluginName] safeLoad started for url: $data")
        return block()
    } catch (e: Exception) {
        if (e.javaClass.simpleName.contains("CancellationException")) throw e
        
        Log.e("SafeApi", "[$pluginName] safeLoad error: ${e.message}", e)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                ErrorUtils.showPluginError(context, pluginName, "LOAD_DETAILS", data)
            } catch (dialogEx: Exception) {
                Log.e("SafeApi", "[$pluginName] Error showing dialog (LOAD_DETAILS): ${dialogEx.message}", dialogEx)
            }
        }, 500)
        throw ErrorLoadingException("Hata oluştu.")
    }
}
