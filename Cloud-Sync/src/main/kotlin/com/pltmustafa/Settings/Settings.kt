package com.pltmustafa

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.net.toUri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CloudSyncSettings {

    fun show(context: Context, plugin: CloudSyncPlugin) {
        var alertDialog: AlertDialog? = null
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            val mapper = jacksonObjectMapper()
            val sm = CloudSyncStorageManager

            addJavascriptInterface(object {
                @JavascriptInterface
                fun getSettings(): String {
                    val creds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()
                    val map = mutableMapOf<String, Any>(
                        "deviceName" to (creds.deviceName ?: android.os.Build.MODEL),
                        "firebaseUrl" to (creds.firebaseUrl ?: ""),
                        "backupDevice" to creds.backupDevice,
                        "restoreDevice" to creds.restoreDevice,
                        "syncExtensions" to creds.backupExtensions,
                        "syncBookmarks" to creds.backupBookmarks,
                        "syncResumeWatching" to creds.backupResumeWatching,
                        "syncSearchHistory" to creds.backupSearchHistory,
                        "syncSettings" to creds.backupGeneral
                    )
                    return mapper.writeValueAsString(map)
                }

                @JavascriptInterface
                fun saveSettings(jsonString: String) {
                    try {
                        val data = mapper.readValue<Map<String, Any>>(jsonString)
                        val currentCreds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

                        val fbUrl = data["firebaseUrl"] as? String ?: ""

                        val newCreds = currentCreds.copy(
                            syncKey = "default",
                            deviceName = (data["deviceName"] as? String)?.takeIf { it.isNotEmpty() } ?: android.os.Build.MODEL,
                            useCustomDatabase = true,
                            firebaseUrl = if (fbUrl.isEmpty()) null else fbUrl,
                            
                            backupDevice = data["backupDevice"] as? Boolean ?: false,
                            restoreDevice = data["restoreDevice"] as? Boolean ?: false,
                            
                            backupExtensions = data["syncExtensions"] as? Boolean ?: true,
                            restoreExtensions = data["syncExtensions"] as? Boolean ?: true,
                            
                            backupBookmarks = data["syncBookmarks"] as? Boolean ?: true,
                            restoreBookmarks = data["syncBookmarks"] as? Boolean ?: true,
                            
                            backupResumeWatching = data["syncResumeWatching"] as? Boolean ?: true,
                            restoreResumeWatching = data["syncResumeWatching"] as? Boolean ?: true,
                            
                            backupSearchHistory = data["syncSearchHistory"] as? Boolean ?: true,
                            restoreSearchHistory = data["syncSearchHistory"] as? Boolean ?: true,
                            
                            backupGeneral = data["syncSettings"] as? Boolean ?: true,
                            restoreGeneral = data["syncSettings"] as? Boolean ?: true,
                            backupPlayer = data["syncSettings"] as? Boolean ?: true,
                            restorePlayer = data["syncSettings"] as? Boolean ?: true,
                            backupTheme = data["syncSettings"] as? Boolean ?: true,
                            restoreTheme = data["syncSettings"] as? Boolean ?: true,
                            backupLayout = data["syncSettings"] as? Boolean ?: true,
                            restoreLayout = data["syncSettings"] as? Boolean ?: true,
                            backupDownloads = data["syncSettings"] as? Boolean ?: true,
                            restoreDownloads = data["syncSettings"] as? Boolean ?: true,
                            backupSubtitles = data["syncSettings"] as? Boolean ?: true,
                            restoreSubtitles = data["syncSettings"] as? Boolean ?: true,
                            
                            deviceId = currentCreds.deviceId ?: CloudSyncSettingsSyncUtils.getDeviceId(context.packageName, context)
                        )

                        sm.appSettingsSyncCreds = newCreds

                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                @JavascriptInterface
                fun fetchCloudPreview(categoryKey: String) {
                    val creds = sm.appSettingsSyncCreds
                    if (creds == null || !creds.isLoggedIn()) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Configure credentials first", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val cat = SyncCategory.entries.find { it.key == categoryKey }
                            if (cat != null) {
                                val payload = CloudSyncSettingsSyncUtils.fetchCategory(cat)
                                val json = payload?.data ?: "{\"status\":\"No data found for this category on cloud.\"}"
                                val encoded = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
                                Handler(Looper.getMainLooper()).post {
                                    evaluateJavascript("showCloudPreview('$categoryKey', decodeURIComponent(escape(window.atob('$encoded'))))", null)
                                }
                            }
                        } catch (e: Exception) {
                            val encoded = android.util.Base64.encodeToString("{\"error\":\"${e.message}\"}".toByteArray(), android.util.Base64.NO_WRAP)
                            Handler(Looper.getMainLooper()).post {
                                evaluateJavascript("showCloudPreview('$categoryKey', decodeURIComponent(escape(window.atob('$encoded'))))", null)
                            }
                        }
                    }
                }

                @JavascriptInterface
                fun getClipboardText(): String {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        return clip.getItemAt(0).text?.toString() ?: ""
                    }
                    return ""
                }

                @JavascriptInterface
                fun syncNow() {
                    val creds = sm.appSettingsSyncCreds
                    if (creds == null || !creds.isLoggedIn()) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Configure credentials first", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Force syncing...", Toast.LENGTH_SHORT).show()
                            }
                            plugin.mergeAndSyncAllCategories(context)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Sync complete!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                @JavascriptInterface
                fun resetData() {
                    sm.deleteAllData()
                    plugin.reload()
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Sync data cleared and plugin reloaded", Toast.LENGTH_SHORT).show()
                    }
                }
                @JavascriptInterface
                fun setCancelable(cancelable: Boolean) {
                    Handler(Looper.getMainLooper()).post {
                        alertDialog?.setCanceledOnTouchOutside(cancelable)
                    }
                }

            }, "Android")
        }

        val htmlTemplate = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>CloudSync Settings</title>
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            <style>
                * { -webkit-tap-highlight-color: transparent; box-sizing: border-box; }
                :root {
                    --bg: #0f0f1a;
                    --surface: rgba(255, 255, 255, 0.05);
                    --surface-hover: rgba(255, 255, 255, 0.1);
                    --primary: #FF2A54;
                    --primary-gradient: linear-gradient(135deg, #FF2A54, #FF7B00);
                    --text: #ffffff;
                    --text-muted: #8e8e9f;
                    --danger: #ff3b30;
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
                    margin-bottom: 20px;
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

                .tabs {
                    display: flex;
                    background: var(--surface);
                    border-radius: 12px;
                    padding: 4px;
                    margin-bottom: 20px;
                }
                .tab {
                    flex: 1;
                    text-align: center;
                    padding: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    color: var(--text-muted);
                    cursor: pointer;
                    border-radius: 8px;
                    transition: all 0.3s;
                }
                .tab.active {
                    background: var(--primary-gradient);
                    color: white;
                    box-shadow: 0 2px 10px rgba(255, 42, 84, 0.3);
                }
                .tab-content {
                    display: none;
                }
                .tab-content.active {
                    display: block;
                    animation: fadeIn 0.3s ease;
                }
                @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

                .section {
                    background: var(--surface);
                    border-radius: 14px;
                    padding: 16px;
                    margin-bottom: 15px;
                    border: 1px solid rgba(255,255,255,0.02);
                }
                .section-title {
                    font-size: 14px;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    color: var(--text-muted);
                    margin-bottom: 15px;
                    font-weight: 600;
                }
                
                .input-group {
                    margin-bottom: 15px;
                }
                .input-group label {
                    display: block;
                    font-size: 13px;
                    color: var(--text-muted);
                    margin-bottom: 6px;
                }
                .input-group input[type="text"] {
                    width: 100%;
                    background: rgba(0,0,0,0.2);
                    border: 1px solid rgba(255,255,255,0.1);
                    color: white;
                    padding: 12px;
                    border-radius: 8px;
                    font-size: 15px;
                    outline: none;
                    transition: border-color 0.2s;
                }
                .input-group input[type="text"]:focus {
                    border-color: var(--primary);
                }
                select {
                    width: 100%;
                    background: rgba(0,0,0,0.2);
                    border: 1px solid rgba(255,255,255,0.1);
                    color: white;
                    padding: 12px;
                    border-radius: 8px;
                    font-size: 15px;
                    outline: none;
                    appearance: none;
                }
                
                .setting-item {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 12px 0;
                    border-bottom: 1px solid rgba(255,255,255,0.05);
                }
                .setting-item:last-child { border-bottom: none; padding-bottom: 0; }
                .setting-item:first-of-type { padding-top: 0; }
                .setting-label { font-size: 15px; font-weight: 500; }

                .switch { position: relative; display: inline-block; width: 50px; height: 28px; }
                .switch input { opacity: 0; width: 0; height: 0; }
                .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: rgba(255,255,255,0.2); transition: .4s; border-radius: 34px; }
                .slider:before { position: absolute; content: ""; height: 20px; width: 20px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
                input:checked + .slider { background: var(--primary-gradient); }
                input:checked + .slider:before { transform: translateX(22px); }
                
                .btn {
                    width: 100%;
                    padding: 14px;
                    border: none;
                    border-radius: 12px;
                    font-size: 15px;
                    font-weight: 600;
                    color: white;
                    background: var(--surface);
                    cursor: pointer;
                    transition: all 0.3s ease;
                    margin-bottom: 10px;
                }
                .btn:active { transform: scale(0.98); }
                .btn-primary { background: var(--primary-gradient); box-shadow: 0 4px 15px rgba(255, 42, 84, 0.3); }
                .btn-danger { background: rgba(255, 59, 48, 0.1); color: var(--danger); border: 1px solid rgba(255, 59, 48, 0.3); }
                .btn-secondary { background: rgba(255,255,255,0.08); }
                
                .flex-row { display: flex; gap: 10px; }

                pre {
                    background: rgba(0,0,0,0.3);
                    padding: 15px;
                    border-radius: 8px;
                    overflow-x: auto;
                    font-size: 12px;
                    color: #a5d6ff;
                    border: 1px solid rgba(255,255,255,0.05);
                    max-height: 300px;
                    overflow-y: auto;
                }

                .loader {
                    display: none;
                    width: 24px;
                    height: 24px;
                    border: 3px solid rgba(255,255,255,0.3);
                    border-radius: 50%;
                    border-top-color: #fff;
                    animation: spin 1s ease-in-out infinite;
                    margin: 20px auto;
                }
                @keyframes spin { to { transform: rotate(360deg); } }
                #guideContent img { max-width: 100%; border-radius: 8px; margin-top: 10px; }
                #guideContent code { background: rgba(0,0,0,0.3); padding: 2px 5px; border-radius: 4px; }
                #guideContent pre { background: rgba(0,0,0,0.3); padding: 10px; border-radius: 8px; overflow-x: auto; }
                .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.85); backdrop-filter: blur(4px); }
                .modal-content { background-color: var(--bg); margin: 10% auto; padding: 20px; border: 1px solid rgba(255,255,255,0.1); border-radius: 14px; width: 90%; max-width: 500px; position: relative; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                .close-btn { position: absolute; right: 15px; top: 15px; color: var(--text-muted); font-size: 24px; font-weight: bold; cursor: pointer; line-height: 1; }
                details > summary { list-style: none; }
                details > summary::-webkit-details-marker { display: none; }
                details > summary::before { content: '▶ '; font-size: 10px; display: inline-block; transition: transform 0.2s; color: var(--text-muted); margin-right: 4px; }
                details[open] > summary::before { transform: rotate(90deg); }
            </style>
        </head>
        <body>
            <div class="header">
                <div class="title">Cloud-Sync</div>
                <div class="subtitle">Cloudstream Data Synchronization</div>
            </div>

            <div class="tabs">
                <div class="tab active" onclick="switchTab('settings')">Settings</div>
                <div class="tab" onclick="switchTab('cloud')">Cloud Preview</div>
            </div>
            
            <div id="tab-settings" class="tab-content active">
                <div class="section">
                    <div class="section-title">Credentials</div>
                    <div class="input-group">
                        <label>Device Name</label>
                        <input type="text" id="deviceName" placeholder="e.g. My Android Phone">
                    </div>
                    
                    <div class="input-group" style="margin-top:10px;">
                        <label>Firebase Realtime DB URL</label>
                        <div class="flex-row">
                            <input type="text" id="firebaseUrl" placeholder="https://your-project.firebaseio.com/">
                            <button class="btn btn-secondary" style="width:auto; margin:0;" onclick="pasteUrl()">Yapıştır</button>
                        </div>
                    </div>
                </div>
                
                <div class="section">
                    <div class="section-title">Sync Actions</div>
                    <div class="setting-item">
                        <div class="setting-label">Upload to Cloud (Backup)</div>
                        <label class="switch">
                            <input type="checkbox" id="backupDevice">
                            <span class="slider"></span>
                        </label>
                    </div>
                    <div class="setting-item">
                        <div class="setting-label">Download from Cloud (Restore)</div>
                        <label class="switch">
                            <input type="checkbox" id="restoreDevice">
                            <span class="slider"></span>
                        </label>
                    </div>
                </div>
                
                <div class="section">
                    <div class="section-title">What to Sync</div>
                    <div class="setting-item">
                        <div class="setting-label">Bookmarks</div>
                        <label class="switch">
                            <input type="checkbox" id="syncBookmarks">
                            <span class="slider"></span>
                        </label>
                    </div>
                    <div class="setting-item">
                        <div class="setting-label">Resume Watching</div>
                        <label class="switch">
                            <input type="checkbox" id="syncResumeWatching">
                            <span class="slider"></span>
                        </label>
                    </div>
                    <div class="setting-item">
                        <div class="setting-label">Search History</div>
                        <label class="switch">
                            <input type="checkbox" id="syncSearchHistory">
                            <span class="slider"></span>
                        </label>
                    </div>
                    <div class="setting-item">
                        <div class="setting-label">Extensions & Repos</div>
                        <label class="switch">
                            <input type="checkbox" id="syncExtensions">
                            <span class="slider"></span>
                        </label>
                    </div>
                    <div class="setting-item">
                        <div class="setting-label">App Settings</div>
                        <label class="switch">
                            <input type="checkbox" id="syncSettings">
                            <span class="slider"></span>
                        </label>
                    </div>
                </div>
                
                <button class="btn btn-primary" onclick="saveData()">Save Settings</button>
                <button class="btn btn-secondary" onclick="Android.syncNow()">Force Sync Now</button>
                <button class="btn btn-secondary" onclick="openSetupGuide()">Setup Guide</button>
                <button class="btn btn-danger" onclick="Android.resetData()" style="margin-top:15px;">Reset All Sync Data</button>
            </div>

            <div id="tab-cloud" class="tab-content">
                <div class="section">
                    <div class="section-title">Cloud Data Explorer</div>
                    <div class="input-group">
                        <label>Select Category to Preview</label>
                        <select id="previewCategory">
                            <option value="bookmarks">Bookmarks</option>
                            <option value="resume_watching">Resume Watching</option>
                            <option value="search_history">Search History</option>
                            <option value="extensions">Extensions</option>
                            <option value="settings">Settings</option>
                        </select>
                    </div>
                    <button class="btn btn-primary" onclick="fetchPreview()">Fetch from Cloud</button>
                    
                    <div id="previewLoader" class="loader"></div>
                    
                    <div id="visualPreviewContainer" style="display:none; margin-top:15px;">
                        <label style="font-size:13px; color:var(--text-muted);">Visual Preview:</label>
                        <div id="visualPreviewItems" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(90px, 1fr)); gap:10px; margin-top:8px; max-height:300px; overflow-y:auto; padding-right:5px;"></div>
                    </div>
                    
                    <div id="previewResultContainer" style="display:none; margin-top:15px;">
                        <label style="font-size:13px; color:var(--text-muted);">Formatted Data:</label>
                        <div id="previewJsonData" style="background: rgba(0,0,0,0.3); padding: 15px; border-radius: 8px; overflow-x: auto; font-size: 13px; color: var(--text); border: 1px solid rgba(255,255,255,0.05); max-height: 400px; overflow-y: auto; font-family: monospace; line-height: 1.5; margin-top: 8px;"></div>
                    </div>
                </div>
            </div>

            <script>
                function switchTab(tabId) {
                    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                    
                    event.target.classList.add('active');
                    document.getElementById('tab-' + tabId).classList.add('active');
                }
                
                function pasteUrl() {
                    const txt = Android.getClipboardText();
                    if(txt) {
                        document.getElementById('firebaseUrl').value = txt;
                    }
                }

                function loadData() {
                    try {
                        const data = JSON.parse(Android.getSettings());
                        document.getElementById('deviceName').value = data.deviceName || '';
                        document.getElementById('firebaseUrl').value = data.firebaseUrl || '';
                        
                        document.getElementById('backupDevice').checked = data.backupDevice === true;
                        document.getElementById('restoreDevice').checked = data.restoreDevice === true;
                        
                        document.getElementById('syncBookmarks').checked = data.syncBookmarks !== false;
                        document.getElementById('syncResumeWatching').checked = data.syncResumeWatching !== false;
                        document.getElementById('syncSearchHistory').checked = data.syncSearchHistory !== false;
                        document.getElementById('syncExtensions').checked = data.syncExtensions !== false;
                        document.getElementById('syncSettings').checked = data.syncSettings !== false;
                    } catch(e) {}
                }
                
                function saveData() {
                    const data = {
                        deviceName: document.getElementById('deviceName').value,
                        firebaseUrl: document.getElementById('firebaseUrl').value,
                        backupDevice: document.getElementById('backupDevice').checked,
                        restoreDevice: document.getElementById('restoreDevice').checked,
                        syncBookmarks: document.getElementById('syncBookmarks').checked,
                        syncResumeWatching: document.getElementById('syncResumeWatching').checked,
                        syncSearchHistory: document.getElementById('syncSearchHistory').checked,
                        syncExtensions: document.getElementById('syncExtensions').checked,
                        syncSettings: document.getElementById('syncSettings').checked
                    };
                    Android.saveSettings(JSON.stringify(data));
                }
                
                function fetchPreview() {
                    const cat = document.getElementById('previewCategory').value;
                    document.getElementById('previewResultContainer').style.display = 'none';
                    document.getElementById('previewLoader').style.display = 'block';
                    Android.fetchCloudPreview(cat);
                }

                function renderJson(data, isRoot) {
                    if (data === null) return '<span style="color:var(--danger)">null</span>';
                    if (typeof data === "boolean") return '<span style="color:#ffcc00">' + data + '</span>';
                    if (typeof data === "number") return '<span style="color:#32d74b">' + data + '</span>';
                    if (typeof data === "string") {
                        const escaped = data.replace(/&/g, "&amp;").replace(/</g, "&lt;");
                        return '<span style="color:#0a84ff">"' + escaped + '"</span>';
                    }
                    if (Array.isArray(data)) {
                        if (data.length === 0) return '<span>[]</span>';
                        let html = '<details ' + (isRoot ? 'open' : '') + ' style="display:inline;"><summary style="cursor:pointer; color:var(--text-muted); user-select:none; display:inline-block;">[ ' + data.length + ' items ]</summary><div style="margin-left:15px; border-left:1px solid rgba(255,255,255,0.1); padding-left:10px; margin-top:4px; margin-bottom:4px; display:block;">';
                        for (let i = 0; i < data.length; i++) {
                            html += '<div style="margin-bottom:2px;">' + renderJson(data[i], false) + (i < data.length - 1 ? ',' : '') + '</div>';
                        }
                        html += '</div><span style="color:var(--text-muted);">]</span></details>';
                        return html;
                    }
                    if (typeof data === "object") {
                        const keys = Object.keys(data);
                        if (keys.length === 0) return '<span>{}</span>';
                        let html = '<details ' + (isRoot ? 'open' : '') + ' style="display:inline;"><summary style="cursor:pointer; color:var(--text-muted); user-select:none; display:inline-block;">{ ' + keys.length + ' keys }</summary><div style="margin-left:15px; border-left:1px solid rgba(255,255,255,0.1); padding-left:10px; margin-top:4px; margin-bottom:4px; display:block;">';
                        for (let i = 0; i < keys.length; i++) {
                            const key = keys[i];
                            html += '<div style="margin-bottom:2px;"><span style="color:#bf5af2">"' + key + '"</span>: ' + renderJson(data[key], false) + (i < keys.length - 1 ? ',' : '') + '</div>';
                        }
                        html += '</div><span style="color:var(--text-muted);">}</span></details>';
                        return html;
                    }
                    return String(data);
                }

                function renderVisual(category, parsed) {
                    const container = document.getElementById('visualPreviewContainer');
                    const itemsDiv = document.getElementById('visualPreviewItems');
                    itemsDiv.innerHTML = '';
                    
                    if (!parsed || (!parsed.datastore && !parsed.settings)) {
                        container.style.display = 'none';
                        return;
                    }
                    
                    let items = [];
                    
                    if (category === 'bookmarks' || category === 'resume_watching') {
                        const strMap = parsed.datastore ? (parsed.datastore._String || parsed.datastore.string || {}) : {};
                        
                        // Extract video positions and resume states
                        const posMap = {};
                        const resumeMap = {};
                        for (let key in strMap) {
                            try {
                                if (key.includes('video_pos_dur')) {
                                    const posObj = JSON.parse(strMap[key]);
                                    if (posObj && posObj.duration && posObj.position) {
                                        const id = key.split('/').pop();
                                        posMap[id] = (posObj.position / posObj.duration) * 100;
                                    }
                                } else if (key.includes('result_resume_watching')) {
                                    const resObj = JSON.parse(strMap[key]);
                                    if (resObj && resObj.parentId && resObj.episodeId) {
                                        resumeMap[resObj.parentId] = resObj.episodeId;
                                    }
                                }
                            } catch(e) {}
                        }
                        
                        for (let key in strMap) {
                            try {
                                const val = strMap[key];
                                if (typeof val === 'string' && val.startsWith('{')) {
                                    const obj = JSON.parse(val);
                                    if (obj && (obj.name || obj.title) && (obj.posterUrl || obj.poster || obj.image)) {
                                        let progress = null;
                                        if (obj.duration && obj.pos) {
                                            progress = (obj.pos / obj.duration) * 100;
                                        } else if (obj.duration && obj.position) {
                                            progress = (obj.position / obj.duration) * 100;
                                        } else if (obj.duration && obj.watchPos) {
                                            progress = (obj.watchPos / obj.duration) * 100;
                                        }
                                        
                                        if (progress === null && obj.id) {
                                            const epId = resumeMap[obj.id] || obj.id;
                                            if (posMap[epId] !== undefined) {
                                                progress = posMap[epId];
                                            }
                                        }
                                        
                                        if (progress === null) {
                                            const url = obj.url || obj.sourceUrl || '';
                                            for (let pk in posMap) {
                                                if (pk && url && (url.includes(pk) || pk.includes(url))) {
                                                    progress = posMap[pk];
                                                    break;
                                                }
                                            }
                                        }
                                        
                                        items.push({
                                            type: 'poster',
                                            title: obj.name || obj.title,
                                            poster: obj.posterUrl || obj.poster || obj.image,
                                            progress: progress
                                        });
                                    }
                                }
                            } catch(e) {}
                        }
                    } else if (category === 'search_history') {
                        const ds = parsed.datastore || {};
                        const strMap = ds._String || ds.string || {};
                        const setMap = ds._StringSet || ds.stringSet || {};
                        
                        // Check _String
                        for (let key in strMap) {
                            if (key.toUpperCase().includes('CLOUDSYNC_') || key.toUpperCase().includes('ULTIMA_')) continue;
                            if (key.toLowerCase().includes('search_history')) {
                                try {
                                    const val = strMap[key];
                                    if (typeof val === 'string') {
                                        if (val.startsWith('[')) {
                                            const arr = JSON.parse(val);
                                            if (Array.isArray(arr)) {
                                                arr.forEach(s => {
                                                    if (typeof s === 'string' && s.length > 0) items.push({ type: 'pill', title: s });
                                                    else if (typeof s === 'object' && s && (s.searchText || s.query || s.name || s.title)) {
                                                        items.push({ type: 'pill', title: s.searchText || s.query || s.name || s.title });
                                                    }
                                                });
                                            }
                                        } else if (val.startsWith('{')) {
                                            const obj = JSON.parse(val);
                                            if (obj && (obj.searchText || obj.query || obj.name || obj.title)) {
                                                items.push({ type: 'pill', title: obj.searchText || obj.query || obj.name || obj.title });
                                            }
                                        } else {
                                            if (isNaN(Number(val)) && val.length > 0) {
                                                items.push({ type: 'pill', title: val });
                                            }
                                        }
                                    }
                                } catch(e) {}
                            }
                        }
                        
                        // Check _StringSet
                        for (let key in setMap) {
                            if (key.toUpperCase().includes('CLOUDSYNC_') || key.toUpperCase().includes('ULTIMA_')) continue;
                            if (key.toLowerCase().includes('search_history')) {
                                try {
                                    const val = setMap[key];
                                    if (Array.isArray(val)) {
                                        val.forEach(s => {
                                            if (typeof s === 'string' && s.length > 0) items.push({ type: 'pill', title: s });
                                        });
                                    }
                                } catch(e) {}
                            }
                        }
                    } else if (category === 'extensions') {
                        const strMap = parsed.datastore ? (parsed.datastore._String || parsed.datastore.string || {}) : {};
                        for (let key in strMap) {
                            try {
                                const val = strMap[key];
                                if (typeof val === 'string' && val.startsWith('[')) {
                                    const arr = JSON.parse(val);
                                    if (Array.isArray(arr)) {
                                        arr.forEach(obj => {
                                            if (obj && (obj.name || obj.internalName)) {
                                                items.push({
                                                    type: 'card',
                                                    title: obj.name || obj.internalName,
                                                    poster: obj.iconUrl || obj.icon || null
                                                });
                                            }
                                        });
                                    }
                                }
                            } catch(e) {}
                        }
                    } else if (category === 'settings') {
                        const ds = parsed.datastore || {};
                        const st = parsed.settings || {};
                        const allPrefs = Object.assign({}, ds._Bool, ds._Int, ds._String, st._Bool, st._Int, st._String);
                        for (let key in allPrefs) {
                            if (!key || key.trim() === '') continue;
                            const k = key.toLowerCase();
                            if (k.startsWith('ultima_') || k.startsWith('cloudsync_')) continue;
                            if (k.includes('sync_hash') || k.includes('sync_ts') || k.includes('synced_keys')) continue;
                            if (k.includes('search_history') || k.includes('result_') || k.includes('video_pos')) continue;
                            items.push({ type: 'setting', title: key, value: String(allPrefs[key]) });
                        }
                    }
                    
                    if (items.length === 0) {
                        container.style.display = 'none';
                        return;
                    }
                    
                    container.style.display = 'block';
                    
                    let html = '';
                    const fallbackImg = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTUwIiBmaWxsPSIjMmEyYTM1Ij48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjE1MCIvPjx0ZXh0IHg9IjUwIiB5PSI3NSIgZmlsbD0iIzg4OCIgZm9udC1mYW1pbHk9InNhbnMtc2VyaWYiIGZvbnQtc2l6ZT0iMTIiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGRvbWluYW50LWJhc2VsaW5lPSJtaWRkbGUiPk5vIEltYWdlPC90ZXh0Pjwvc3ZnPg==';
                    
                    if (items[0].type === 'poster') {
                        itemsDiv.style.display = 'grid';
                        itemsDiv.style.gridTemplateColumns = 'repeat(auto-fill, minmax(90px, 1fr))';
                        items.forEach(item => {
                            const poster = item.poster || fallbackImg;
                            const title = item.title || 'Unknown';
                            html += '<div style="background:rgba(255,255,255,0.05); border-radius:8px; overflow:hidden; text-align:center; position:relative; border:1px solid rgba(255,255,255,0.1);">';
                            html += '<div style="width:100%; padding-top:150%; position:relative;">';
                            html += '<img src="' + poster + '" style="position:absolute; top:0; left:0; width:100%; height:100%; object-fit:cover;" onerror="this.src=\'' + fallbackImg + '\'">';
                            html += '</div>';
                            html += '<div style="padding:5px; font-size:11px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; background:rgba(0,0,0,0.8); position:absolute; bottom:0; width:100%; display:flex; flex-direction:column; gap:4px;">';
                            html += '<span>' + title + '</span>';
                            if (item.progress !== null && item.progress !== undefined && !isNaN(item.progress)) {
                                html += '<div style="width:100%; height:3px; background:rgba(255,255,255,0.2); border-radius:2px; overflow:hidden;">';
                                html += '<div style="height:100%; background:#e50914; width:' + Math.min(100, Math.max(0, item.progress)) + '%;"></div>';
                                html += '</div>';
                            }
                            html += '</div></div>';
                        });
                    } else if (items[0].type === 'pill') {
                        itemsDiv.style.display = 'flex';
                        itemsDiv.style.flexWrap = 'wrap';
                        itemsDiv.style.gap = '8px';
                        items.forEach(item => {
                            html += '<div style="background:rgba(255,255,255,0.1); padding:6px 12px; border-radius:16px; font-size:12px; border:1px solid rgba(255,255,255,0.05);">' + item.title + '</div>';
                        });
                    } else if (items[0].type === 'card') {
                        itemsDiv.style.display = 'flex';
                        itemsDiv.style.flexDirection = 'column';
                        itemsDiv.style.gap = '8px';
                        items.forEach(item => {
                            html += '<div style="display:flex; align-items:center; background:rgba(255,255,255,0.05); padding:10px 14px; border-radius:10px; border:1px solid rgba(255,255,255,0.05);">';
                            if (item.poster) {
                                html += '<img src="' + item.poster + '" style="width:24px; height:24px; border-radius:4px; margin-right:12px; object-fit:cover;" onerror="this.style.display=\'none\'">';
                            } else {
                                html += '<div style="width:24px; height:24px; border-radius:4px; margin-right:12px; background:rgba(255,255,255,0.1); display:flex; align-items:center; justify-content:center;"><span style="font-size:12px;color:#888;">📦</span></div>';
                            }
                            html += '<span style="font-size:14px; font-weight:500;">' + item.title + '</span>';
                            html += '</div>';
                        });
                    } else if (items[0].type === 'setting') {
                        itemsDiv.style.display = 'grid';
                        itemsDiv.style.gridTemplateColumns = 'repeat(auto-fill, minmax(140px, 1fr))';
                        items.forEach(item => {
                            html += '<div style="background:rgba(255,255,255,0.05); padding:10px; border-radius:8px; border:1px solid rgba(255,255,255,0.05);">';
                            html += '<div style="font-size:10px; color:var(--text-muted); margin-bottom:4px; word-break:break-all;">' + item.title + '</div>';
                            html += '<div style="font-size:13px; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">' + item.value + '</div>';
                            html += '</div>';
                        });
                    }
                    
                    itemsDiv.innerHTML = html;
                }

                function showCloudPreview(category, jsonData) {
                    document.getElementById('previewLoader').style.display = 'none';
                    document.getElementById('previewResultContainer').style.display = 'block';
                    try {
                        const parsed = JSON.parse(jsonData);
                        document.getElementById('previewJsonData').innerHTML = renderJson(parsed, true);
                        renderVisual(category, parsed);
                    } catch(e) {
                        document.getElementById('previewJsonData').textContent = jsonData;
                        document.getElementById('visualPreviewContainer').style.display = 'none';
                    }
                }

                function openSetupGuide() {
                    const modal = document.getElementById('guideModal');
                    const contentDiv = document.getElementById('guideContent');
                    
                    modal.style.display = 'block';
                    Android.setCancelable(false);
                    
                    if (contentDiv.innerHTML.trim() === '') {
                        document.getElementById('guideLoader').style.display = 'block';
                        fetch('https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/master/docs/ULTIMA_SYNC_SETUP.md')
                            .then(response => {
                                if (!response.ok) throw new Error('HTTP error');
                                return response.text();
                            })
                            .then(text => {
                                document.getElementById('guideLoader').style.display = 'none';
                                contentDiv.innerHTML = marked.parse(text);
                            })
                            .catch(err => {
                                document.getElementById('guideLoader').style.display = 'none';
                                contentDiv.innerHTML = '<p style="color:var(--danger)">Failed to load guide.</p>';
                            });
                    }
                }

                function closeSetupGuide() {
                    document.getElementById('guideModal').style.display = 'none';
                    Android.setCancelable(true);
                }

                window.onload = loadData;
            </script>

            <div id="guideModal" class="modal" onclick="if(event.target === this) closeSetupGuide()">
                <div class="modal-content">
                    <span class="close-btn" onclick="closeSetupGuide()">&times;</span>
                    <div class="section-title" style="margin-top:0;">Setup Guide</div>
                    <div id="guideLoader" class="loader"></div>
                    <div id="guideContent"></div>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)

        alertDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(webView)
            .create()
    
        alertDialog?.setOnShowListener {
            alertDialog?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            )
        }
        
        alertDialog?.show()
    }
}
