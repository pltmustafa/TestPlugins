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
            val webView = android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun setPluginEnabled(name: String, enabled: Boolean) {
                        KekikStreamPlugin.setPluginEnabled(name, enabled)
                    }
                }, "Android")
            }

            val htmlTemplate = """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>KekikStream Ayarları</title>
                <style>
                    :root {
                        --bg: #0f0f1a;
                        --surface: rgba(255, 255, 255, 0.05);
                        --surface-hover: rgba(255, 255, 255, 0.1);
                        --primary: #FF2A54;
                        --primary-gradient: linear-gradient(135deg, #FF2A54, #FF7B00);
                        --text: #ffffff;
                        --text-muted: #8e8e9f;
                    }
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg);
                        color: var(--text);
                        overflow-x: hidden;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 25px;
                        padding-bottom: 15px;
                        border-bottom: 1px solid rgba(255,255,255,0.1);
                    }
                    .title {
                        font-size: 26px;
                        font-weight: 800;
                        background: var(--primary-gradient);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        letter-spacing: 0.5px;
                    }
                    .subtitle {
                        font-size: 13px;
                        color: var(--text-muted);
                        margin-top: 5px;
                    }
                    .actions {
                        display: flex;
                        gap: 10px;
                        margin-bottom: 20px;
                    }
                    .btn {
                        flex: 1;
                        padding: 12px;
                        border: none;
                        border-radius: 12px;
                        font-size: 14px;
                        font-weight: 600;
                        color: white;
                        background: var(--surface);
                        backdrop-filter: blur(10px);
                        cursor: pointer;
                        transition: all 0.3s ease;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.2);
                    }
                    .btn:active {
                        transform: scale(0.96);
                    }
                    .btn-select-all { background: var(--primary-gradient); }
                    .btn-deselect-all { background: rgba(255, 255, 255, 0.08); }
                    
                    .plugin-list {
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                        padding-bottom: 20px;
                    }
                    .plugin-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        background: var(--surface);
                        padding: 16px 18px;
                        border-radius: 14px;
                        backdrop-filter: blur(10px);
                        border: 1px solid rgba(255,255,255,0.02);
                        transition: transform 0.2s ease, background 0.2s ease;
                    }
                    .plugin-item:active {
                        transform: scale(0.98);
                        background: var(--surface-hover);
                    }
                    .plugin-name {
                        font-size: 16px;
                        font-weight: 500;
                    }
                    
                    /* Modern Toggle Switch */
                    .switch {
                        position: relative;
                        display: inline-block;
                        width: 50px;
                        height: 28px;
                    }
                    .switch input {
                        opacity: 0;
                        width: 0;
                        height: 0;
                    }
                    .slider {
                        position: absolute;
                        cursor: pointer;
                        top: 0; left: 0; right: 0; bottom: 0;
                        background-color: rgba(255,255,255,0.2);
                        transition: .4s;
                        border-radius: 34px;
                    }
                    .slider:before {
                        position: absolute;
                        content: "";
                        height: 20px;
                        width: 20px;
                        left: 4px;
                        bottom: 4px;
                        background-color: white;
                        transition: .4s;
                        border-radius: 50%;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    input:checked + .slider {
                        background: var(--primary-gradient);
                    }
                    input:checked + .slider:before {
                        transform: translateX(22px);
                    }

                    /* CSS Loader */
                    .loader-container {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        padding: 40px 0;
                    }
                    .spinner {
                        width: 40px;
                        height: 40px;
                        border: 4px solid rgba(255,255,255,0.1);
                        border-top: 4px solid var(--primary);
                        border-radius: 50%;
                        animation: spin 1s linear infinite;
                        margin-bottom: 15px;
                    }
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="title">KekikStream</div>
                    <div class="subtitle">Kaynak Yönetimi</div>
                </div>
                
                <div class="actions">
                    <button class="btn btn-select-all" onclick="selectAll(true)">Tümünü Seç</button>
                    <button class="btn btn-deselect-all" onclick="selectAll(false)">Kaldır</button>
                </div>

                <div id="loader" class="loader-container">
                    <div class="spinner"></div>
                    <div style="color: var(--text-muted); font-size: 14px;">Kaynaklar sunucudan alınıyor...</div>
                </div>

                <div class="plugin-list" id="pluginList">
                </div>

                <script>
                    function togglePlugin(checkbox, name) {
                        Android.setPluginEnabled(name, checkbox.checked);
                    }
                    
                    function selectAll(check) {
                        const checkboxes = document.querySelectorAll('.plugin-switch');
                        checkboxes.forEach(cb => {
                            if(cb.checked !== check) {
                                cb.checked = check;
                                Android.setPluginEnabled(cb.dataset.name, check);
                            }
                        });
                    }
                    
                    function setListData(base64Html) {
                        document.getElementById('loader').style.display = 'none';
                        document.getElementById('pluginList').innerHTML = decodeURIComponent(escape(window.atob(base64Html)));
                    }
                    
                    function showError(msg) {
                        document.getElementById('loader').style.display = 'none';
                        document.getElementById('pluginList').innerHTML = '<div style="text-align:center; color:#FF2A54; padding:20px;">' + msg + '</div>';
                    }
                </script>
            </body>
            </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)

            val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(webView)
                .show()

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
                            val encodedError = android.util.Base64.encodeToString("Kaynak listesi alınamadı.".toByteArray(), android.util.Base64.NO_WRAP)
                            webView.evaluateJavascript("showError(decodeURIComponent(escape(window.atob('$encodedError'))))", null)
                            return@post
                        }

                        val itemsHtml = pluginNames.joinToString("\n") { name ->
                            val isChecked = isPluginEnabled(name)
                            val checkedAttr = if (isChecked) "checked" else ""
                            """
                            <div class="plugin-item" onclick="document.getElementById('switch_$name').click()">
                                <div class="plugin-name">$name</div>
                                <label class="switch" onclick="event.stopPropagation()">
                                    <input type="checkbox" id="switch_$name" class="plugin-switch" data-name="$name" onchange="togglePlugin(this, '$name')" $checkedAttr>
                                    <span class="slider"></span>
                                </label>
                            </div>
                            """.trimIndent()
                        }
                        
                        val encodedHtml = android.util.Base64.encodeToString(itemsHtml.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("setListData('$encodedHtml')", null)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        val errorMsg = e.message ?: "Bilinmeyen Hata"
                        val encodedError = android.util.Base64.encodeToString("Hata: $errorMsg".toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("showError(decodeURIComponent(escape(window.atob('$encodedError'))))", null)
                    }
                }
            }.start()
        }
    }
}
