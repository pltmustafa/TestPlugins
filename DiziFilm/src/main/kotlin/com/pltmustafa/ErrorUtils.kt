package com.pltmustafa

import android.content.Context
import android.os.Handler
import android.os.Looper

object ErrorUtils {
    private var isDialogShowing = false

    fun showErrorPopup(context: Context?, title: String, desc: String, pluginName: String = "Bilinmeyen", errorType: String = "Bilinmeyen", errorUrl: String = "") {
        if (context == null || isDialogShowing) return
        
        isDialogShowing = true
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = android.webkit.WebView(context).apply {
                    settings.javaScriptEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }

                var dialog: android.app.AlertDialog? = null

                webView.addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun closeDialog() {
                        Handler(Looper.getMainLooper()).post { dialog?.dismiss() }
                    }

                    @android.webkit.JavascriptInterface
                    fun reportToDeveloper() {
                        Thread {
                            try {
                                val url = java.net.URL("https://cloudstream-feed.vercel.app/api/notify")
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.setRequestProperty("Content-Type", "application/json")
                                conn.doOutput = true
                                val jsonPayload = org.json.JSONObject().apply {
                                    put("pluginName", pluginName)
                                    put("errorType", errorType)
                                    put("url", errorUrl)
                                }.toString()
                                
                                conn.outputStream.use { os ->
                                    val input = jsonPayload.toByteArray(Charsets.UTF_8)
                                    os.write(input, 0, input.size)
                                }
                                
                                val responseCode = conn.responseCode
                                android.util.Log.d("Webhook", "Response code: $responseCode")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }.start()

                        Handler(Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Hata raporu geliştiriciye iletildi. Bildiriminiz için teşekkürler!", android.widget.Toast.LENGTH_LONG).show()
                            dialog?.dismiss()
                        }
                    }
                }, "Android")

                val htmlTemplate = """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        * {
                            -webkit-tap-highlight-color: transparent;
                            outline: none;
                        }
                        :root {
                            --bg: rgba(25, 25, 28, 0.85);
                            --border: rgba(255, 255, 255, 0.08);
                            --text-main: #ffffff;
                            --text-sub: #98989f;
                            --accent: #ff453a;
                        }
                        body {
                            margin: 0;
                            padding: 25px;
                            font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", Roboto, Helvetica, Arial, sans-serif;
                            background: var(--bg);
                            backdrop-filter: blur(25px);
                            -webkit-backdrop-filter: blur(25px);
                            border: 1px solid var(--border);
                            border-radius: 28px;
                            color: var(--text-main);
                            text-align: center;
                        }
                        .icon-wrapper {
                            width: 64px;
                            height: 64px;
                            margin: 0 auto 16px;
                            background: linear-gradient(135deg, rgba(255, 69, 58, 0.15), rgba(255, 69, 58, 0.02));
                            border-radius: 20px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            box-shadow: 0 10px 30px rgba(255, 69, 58, 0.12);
                        }
                        .icon-wrapper svg {
                            width: 32px;
                            height: 32px;
                            fill: none;
                            stroke: var(--accent);
                            stroke-width: 2.5;
                            stroke-linecap: round;
                            stroke-linejoin: round;
                        }
                        h2 {
                            margin: 0 0 8px 0;
                            font-size: 20px;
                            font-weight: 700;
                            letter-spacing: -0.4px;
                        }
                        p {
                            margin: 0 0 24px 0;
                            font-size: 14px;
                            color: var(--text-sub);
                            line-height: 1.45;
                        }
                        button {
                            background: var(--accent);
                            color: white;
                            border: none;
                            padding: 14px;
                            width: 100%;
                            border-radius: 14px;
                            font-size: 16px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: opacity 0.2s, transform 0.1s;
                        }
                        button:active {
                            opacity: 0.8;
                            transform: scale(0.96);
                        }
                        .btn-secondary {
                            background: transparent;
                            color: var(--text-sub);
                            border: 1px solid var(--border);
                            margin-top: 10px;
                        }
                        .btn-secondary:active {
                            background: rgba(255, 255, 255, 0.05);
                        }
                    </style>
                </head>
                <body>
                    <div class="icon-wrapper">
                        <svg viewBox="0 0 24 24">
                            <circle cx="12" cy="12" r="10"></circle>
                            <line x1="12" y1="8" x2="12" y2="12"></line>
                            <line x1="12" y1="16" x2="12.01" y2="16"></line>
                        </svg>
                    </div>
                    <h2>${title}</h2>
                    <p>${desc}</p>
                    <button onclick="Android.closeDialog()">Tamam</button>
                    <button class="btn-secondary" onclick="Android.reportToDeveloper()">Geliştiriciye Bildir</button>
                </body>
                </html>
                """.trimIndent()

                webView.loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)

                dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                    .setView(webView)
                    .setCancelable(true)
                    .create()

                dialog.setOnDismissListener {
                    isDialogShowing = false
                }

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()

            } catch (e: Exception) {
                isDialogShowing = false
                android.widget.Toast.makeText(context, "Bilinmeyen bir hata oluştu.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showPluginError(context: Context?, pluginName: String, errorType: String = "GENERIC", errorUrl: String = "") {
        val title = when (errorType) {
            "MAIN_PAGE" -> "$pluginName Yanıt Vermiyor"
            "LOAD_DETAILS" -> "İçerik Detayları Yüklenemedi"
            "LOAD_LINKS" -> "İçerik Yüklenemedi"
            else -> "$pluginName Yanıt Vermiyor"
        }

        val desc = when (errorType) {
            "MAIN_PAGE" -> "<br>Lütfen öncelikle <b>$pluginName</b> sitesini ziyaret edip sitenin çalışıp çalışmadığını kontrol edin.<br><br>Eğer site çalışıyor fakat eklentide hata veriyorsa, aşağıdaki butonu kullanarak durumu bildirebilirsiniz."
            "LOAD_DETAILS" -> "<br>Lütfen öncelikle kaynak siteyi ziyaret edip bu içeriğe ulaşıp ulaşamadığınızı kontrol edin.<br><br>Eğer site çalışıyor fakat eklentide hata veriyorsa, aşağıdaki butonu kullanarak durumu bildirebilirsiniz."
            "LOAD_LINKS" -> "<br>Lütfen öncelikle kaynak siteyi ziyaret edip içeriğin çalışıp çalışmadığını kontrol edin.<br><br>Eğer içerik sitede açılıyor fakat eklentide hata veriyorsa, aşağıdaki butonu kullanarak durumu bildirebilirsiniz."
            else -> "<br>Lütfen öncelikle <b>$pluginName</b> kaynağının (sitesinin) sorunsuz çalıştığından emin olun.<br><br>Eğer kaynak çalışıyor fakat eklenti içerisinde hata alıyorsanız, aşağıdaki butonu kullanarak durumu bize bildirebilirsiniz."
        }
        
        showErrorPopup(context, title, desc, pluginName, errorType, errorUrl)
    }
}
