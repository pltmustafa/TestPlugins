package com.pltmustafa

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import okhttp3.OkHttpClient
import okhttp3.Request

@CloudstreamPlugin
class KekikStreamPlugin : Plugin() {
    companion object {
        private const val PREFS_NAME = "KekikStreamPrefs"
        private const val PLUGIN_PREFIX = "plugin_enabled_"
        var sharedPrefs: SharedPreferences? = null

        fun isPluginEnabled(pluginName: String): Boolean {
            return sharedPrefs?.getBoolean("$PLUGIN_PREFIX$pluginName", true) ?: true
        }

        fun setPluginEnabled(pluginName: String, enabled: Boolean) {
            sharedPrefs?.edit()?.putBoolean("$PLUGIN_PREFIX$pluginName", enabled)?.apply()
        }
    }

    override fun load(context: Context) {
        val activity = context as? Activity
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        registerMainAPI(KekikStream())

        openSettings = {
            Thread {
                try {
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url("https://stream.watchbuddy.tv/api/v1/get_all_plugins")
                        .header("User-Agent", "Dart/3.11 (dart:io)")
                        .build()

                    val responseText = client.newCall(req).execute().body?.string() ?: ""

                    val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                    val typeRef = com.fasterxml.jackson.module.kotlin.jacksonTypeRef<KekikStream.WBResponse<List<KekikStream.WBPlugin>>>()
                    val response = mapper.readValue(responseText, typeRef)
                    val plugins = response?.result ?: emptyList()
                    val pluginNames = plugins.mapNotNull { it.name }.sorted()

                    Handler(Looper.getMainLooper()).post {
                        if (pluginNames.isEmpty()) {
                            Toast.makeText(context, "Kaynak listesi alınamadı", Toast.LENGTH_SHORT).show()
                            return@post
                        }

                        val names = pluginNames.toTypedArray()
                        val checkedItems = BooleanArray(names.size) { isPluginEnabled(names[it]) }

                        AlertDialog.Builder(context)
                            .setTitle("Kaynak Ayarları")
                            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                                setPluginEnabled(names[which], isChecked)
                            }
                            .setPositiveButton("Tamam", null)
                            .setNeutralButton("Tümünü Seç") { _, _ ->
                                names.forEach { setPluginEnabled(it, true) }
                                Toast.makeText(context, "Tüm kaynaklar aktif edildi", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Tümünü Kaldır") { _, _ ->
                                names.forEach { setPluginEnabled(it, false) }
                                Toast.makeText(context, "Tüm kaynaklar devre dışı bırakıldı", Toast.LENGTH_SHORT).show()
                            }
                            .show()
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }
}
