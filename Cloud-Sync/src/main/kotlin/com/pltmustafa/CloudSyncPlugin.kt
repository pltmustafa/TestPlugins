package com.pltmustafa

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.lagradost.cloudstream3.CloudStreamApp
import kotlin.time.Duration.Companion.milliseconds

@CloudstreamPlugin
class CloudSyncPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    private var lifecycleCallbacks: android.app.Application.ActivityLifecycleCallbacks? = null
    private var registeredApp: android.app.Application? = null

    private var pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dirtyCategories = mutableSetOf<SyncCategory>()
    private val dirtyCategoriesLock = Any()

    @Volatile
    private var isRestoring = false
    @Volatile
    private var restoringUntil = 0L
    private val RESTORE_GUARD_MS = 5_000L
    private val sseLock = Any()

    private val pullMutex = Mutex()
    private val syncMutex = Mutex()

    private var dataPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var defaultPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pushJob: Job? = null
    private var ssePullJob: Job? = null
    private var sseCall: okhttp3.Call? = null
    @Volatile
    private var isSseConnected = false
    private var lastSseSyncKey: String? = null
    private var lastSseUrl: String? = null

    @Volatile
    private var sseRetryCount = 0
    private val SSE_MAX_BACKOFF_MS = 60_000L
    private val SSE_BASE_DELAY_MS = 5_000L

    @Volatile
    private var lastPushTimestamp = 0L

    companion object {
        private const val TAG = "CloudSyncSync"
        private const val PUSH_DEBOUNCE_MS = 2_000L
        private const val SSE_PULL_DEBOUNCE_MS = 3_000L
        private const val IGNORE_OWN_PUSH_MS = 5_000L
    }

    private fun markDirty(category: SyncCategory) {
        synchronized(dirtyCategoriesLock) {
            dirtyCategories.add(category)
        }
        scheduleDebouncedPush()
    }

    private fun consumeDirtyCategories(): Set<SyncCategory> {
        synchronized(dirtyCategoriesLock) {
            val copy = dirtyCategories.toSet()
            dirtyCategories.clear()
            return copy
        }
    }

    private fun scheduleDebouncedPush() {
        val ctx = CloudStreamApp.context ?: activity ?: return
        val creds = CloudSyncStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return

        pushJob?.cancel()
        pushJob = pluginScope.launch {
            delay(PUSH_DEBOUNCE_MS.milliseconds)
            syncMutex.withLock {
                mergeAndSyncAllCategories(ctx)
            }
        }
    }

    private suspend fun pullChangedCategories(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val creds = CloudSyncStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn() || !creds.restoreDevice) return false

        if (!pullMutex.tryLock()) {
            Log.d(TAG, "Pull already in progress, skipping")
            return false
        }

        try {
            val manifest = CloudSyncSettingsSyncUtils.fetchManifest()
            if (manifest == null) {
                Log.d(TAG, "No manifest found in cloud")
                return false
            }

            val categoriesToFetch = mutableListOf<Pair<SyncCategory, SyncCategoryMeta>>()
            for (category in SyncCategory.entries) {
                if (!creds.isRestoreEnabled(category)) continue
                val cloudMeta = manifest.getMeta(category) ?: continue
                val localTs = CloudSyncStorageManager.getCategoryTimestamp(category)
                if (!force && cloudMeta.ts <= localTs) continue
                Log.d(TAG, "Category ${category.key} changed: cloud=${cloudMeta.ts}, local=$localTs, device=${cloudMeta.device}")
                categoriesToFetch.add(category to cloudMeta)
            }

            if (categoriesToFetch.isEmpty()) return false

            val fetchedPayloads = coroutineScope {
                categoriesToFetch.map { (category, meta) ->
                    async(Dispatchers.IO) {
                        try {
                            val payload = CloudSyncSettingsSyncUtils.fetchCategory(category)
                            if (payload != null && payload.data.isNotBlank()) {
                                Triple(category, meta, payload)
                            } else null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching category ${category.key}: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (fetchedPayloads.isEmpty()) return false

            var restoredAny = false
            isRestoring = true

            try {
                for ((category, cloudMeta, payload) in fetchedPayloads) {
                    try {
                        val backupFile = mapper.readValue<BackupFile>(payload.data)

                        when (category) {
                            SyncCategory.EXTENSIONS -> {
                                CloudSyncBackupUtils.restoreCategory(appContext, category, backupFile)
                                CloudSyncBackupUtils.restoreExtensionsCategory(appContext, backupFile)
                            }
                            SyncCategory.BOOKMARKS -> {
                                CloudSyncBackupUtils.restoreCategory(appContext, category, backupFile)
                                withContext(Dispatchers.Main) {
                                    try { MainActivity.bookmarksUpdatedEvent(true) } catch (_: Throwable) {}
                                }
                            }
                            else -> {
                                CloudSyncBackupUtils.restoreCategory(appContext, category, backupFile)
                            }
                        }

                        CloudSyncStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                        CloudSyncStorageManager.setCategoryHash(category, cloudMeta.hash)
                        restoredAny = true

                        Log.d(TAG, "Restored category ${category.key} from ${cloudMeta.device}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring category ${category.key}: ${e.message}")
                    }
                }
            } finally {
                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                withContext(Dispatchers.Main) {
                    isRestoring = false
                }
            }

            if (restoredAny) {
                reload()
            }

            return restoredAny
        } finally {
            pullMutex.unlock()
        }
    }

    private suspend fun migrateFromV1(context: Context) {
        if (CloudSyncStorageManager.syncV2Migrated) return

        val creds = CloudSyncStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        Log.d(TAG, "Checking for v1 data to migrate...")

        try {
            val oldData = CloudSyncSettingsSyncUtils.fetchSharedData()
            if (oldData != null && !oldData.syncedData.isNullOrBlank()) {
                Log.d(TAG, "Found v1 shared_data, migrating to v2 categories...")

                withContext(Dispatchers.Main) {
                    showToast("Migrating sync data to v2...")
                }

                val resumeWatching = getResumeWatching()

                val categoryData = mutableMapOf<SyncCategory, Pair<String, String>>()
                for (category in SyncCategory.entries) {
                    if (!creds.isBackupEnabled(category)) continue
                    val categoryBackup = CloudSyncBackupUtils.getBackupForCategory(context, category, resumeWatching)
                    if (categoryBackup != null) {
                        val data = categoryBackup.toJsonSorted()
                        val hash = CloudSyncBackupUtils.computeHash(data)
                        categoryData[category] = Pair(data, hash)
                    }
                }

                if (categoryData.isNotEmpty()) {
                    CloudSyncSettingsSyncUtils.pushCategories(categoryData)
                }

                CloudSyncSettingsSyncUtils.deleteSharedData()
                Log.d(TAG, "Deleted old v1 shared_data")

                withContext(Dispatchers.Main) {
                    showToast("Sync migration complete!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "v1→v2 migration error: ${e.message}")
        }

        CloudSyncStorageManager.syncV2Migrated = true
    }

    private fun cleanup() {
        pushJob?.cancel()
        ssePullJob?.cancel()
        sseCall?.cancel()
        pluginScope.cancel()

        try {
            dataPrefsListener?.let { listener ->
                CloudStreamApp.context?.getSharedPrefs()?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            defaultPrefsListener?.let { listener ->
                CloudStreamApp.context?.getDefaultSharedPrefs()?.unregisterOnSharedPreferenceChangeListener(listener)
            }
        } catch (_: Exception) {}
        dataPrefsListener = null
        defaultPrefsListener = null

        lifecycleCallbacks?.let { cb ->
            registeredApp?.unregisterActivityLifecycleCallbacks(cb)
        }
        lifecycleCallbacks = null
        registeredApp = null

        activity = null
    }

    override fun load(context: Context) {
        cleanup()
        pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        activity = context as? AppCompatActivity
        registerMainAPI(CloudSync(this))

        openSettings = { ctx ->
            CloudSyncSettings.show(ctx, this)
        }

        val creds = CloudSyncStorageManager.appSettingsSyncCreds
        if (creds != null && creds.isLoggedIn()) {
            pluginScope.launch {
                try {
                    migrateFromV1(context)

                    if (creds.restoreDevice) {
                        val restored = pullChangedCategories(context)
                        if (restored) {
                            withContext(Dispatchers.Main) {
                                showToast("Synced from cloud")
                            }
                        }
                    }

                    if (creds.backupDevice) {
                        val manifest = CloudSyncSettingsSyncUtils.fetchManifest()
                        if (manifest == null) {
                            Log.d(TAG, "No manifest in cloud — performing initial push")
                            pushAllCategories(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Startup sync failed: ${e.message}")
                }
            }
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) {
                if (key == "CLOUDSYNC_APP_SETTINGS_SYNC_CREDS") {
                    Log.d(TAG, "Sync configuration changed, restarting SSE listener")
                    startSseListener(context)
                    return@OnSharedPreferenceChangeListener
                }
                if (!isRestoring && System.currentTimeMillis() > restoringUntil) {
                    val category = CloudSyncBackupUtils.classifyKey(key)
                    if (category != null) {
                        Log.d(TAG, "Pref changed: $key → category ${category.key}")
                        markDirty(category)
                    }
                }
            }
        }
        dataPrefsListener = listener
        defaultPrefsListener = listener
        try {
            context.getSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            context.getDefaultSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            Log.d(TAG, "Registered preference change listeners")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register preference change listeners: ${e.message}")
        }

        MainActivity.bookmarksUpdatedEvent += { _: Boolean ->
            if (!isRestoring && System.currentTimeMillis() > restoringUntil) {
                markDirty(SyncCategory.BOOKMARKS)
            }
        }

        val appInstance = context.applicationContext as? android.app.Application
        val callback = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity is MainActivity) {
                    this@CloudSyncPlugin.activity = activity as? AppCompatActivity
                    pluginScope.launch {
                        try {
                            val currentCreds = CloudSyncStorageManager.appSettingsSyncCreds
                            if (currentCreds != null && currentCreds.isLoggedIn() && currentCreds.restoreDevice) {
                                pullChangedCategories(activity)
                                startSseListener(activity)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (activity === this@CloudSyncPlugin.activity) {
                    this@CloudSyncPlugin.activity = null
                }
            }
        }
        lifecycleCallbacks = callback
        registeredApp = appInstance
        appInstance?.registerActivityLifecycleCallbacks(callback)

        startSseListener(context)
    }

    fun startSseListener(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(sseLock) {
            val creds = CloudSyncStorageManager.appSettingsSyncCreds
            if (creds == null || !creds.isLoggedIn() || !creds.restoreDevice) {
                sseCall?.cancel()
                sseCall = null
                isSseConnected = false
                lastSseSyncKey = null
                lastSseUrl = null
                return
            }

            val activeUrl = creds.activeUrl
            val syncKey = creds.syncKey

            if (!force && isSseConnected && lastSseSyncKey == syncKey && lastSseUrl == activeUrl) {
                Log.d(TAG, "SSE listener already connected to correct URL and key, skipping restart")
                return
            }

            sseCall?.cancel()
            isSseConnected = false
            lastSseSyncKey = syncKey
            lastSseUrl = activeUrl

            val url = "${activeUrl}sync/${syncKey}/manifest.json?alt=sse"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .build()

            Log.d(TAG, "Starting SSE listener for URL: $url")

            val sseClient = com.lagradost.cloudstream3.app.baseClient.newBuilder()
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val call = sseClient.newCall(request)
            sseCall = call

            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    synchronized(sseLock) {
                        if (sseCall !== call) return
                        isSseConnected = false
                        sseCall = null
                        sseRetryCount++
                    }
                    if (call.isCanceled()) return
                    val backoffMs = calculateSseBackoff()
                    Log.e(TAG, "SSE connection failed: ${e.message}, reconnecting in ${backoffMs}ms")
                    pluginScope.launch {
                        delay(backoffMs.milliseconds)
                        startSseListener(appContext)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    synchronized(sseLock) {
                        if (sseCall !== call) {
                            response.close()
                            return
                        }
                    }
                    if (response.code != 200) {
                        synchronized(sseLock) {
                            if (sseCall !== call) {
                                response.close()
                                return
                            }
                            isSseConnected = false
                            sseCall = null
                            sseRetryCount++
                        }
                        response.close()
                        val backoffMs = calculateSseBackoff()
                        Log.e(TAG, "SSE connection failed with HTTP code ${response.code}, reconnecting in ${backoffMs}ms")
                        pluginScope.launch {
                            delay(backoffMs.milliseconds)
                            startSseListener(appContext)
                        }
                        return
                    }

                    synchronized(sseLock) {
                        if (sseCall !== call) {
                            response.close()
                            return
                        }
                        isSseConnected = true
                        sseRetryCount = 0
                    }

                    val source = response.body.source()
                    try {
                        var currentEvent: String? = null
                        while (true) {
                            synchronized(sseLock) {
                                if (sseCall !== call) return
                            }
                            if (source.exhausted()) break
                            val line = source.readUtf8Line() ?: break

                            if (line.isBlank()) {
                                currentEvent = null
                                continue
                            }

                            if (line.startsWith("event:")) {
                                currentEvent = line.substring(6).trim()
                                continue
                            }

                            if (line.startsWith("data:") && (currentEvent == "put" || currentEvent == "patch")) {
                                val json = line.substring(5).trim()
                                if (json != "null" && json.isNotEmpty()) {
                                    val timeSinceLastPush = System.currentTimeMillis() - lastPushTimestamp
                                    if (timeSinceLastPush < IGNORE_OWN_PUSH_MS) {
                                        Log.d(TAG, "SSE: Ignoring event within ${timeSinceLastPush}ms of our own push")
                                    } else {
                                        ssePullJob?.cancel()
                                        ssePullJob = pluginScope.launch {
                                            delay(SSE_PULL_DEBOUNCE_MS.milliseconds)
                                            syncMutex.withLock {
                                                pullChangedCategories(appContext)
                                            }
                                        }
                                    }
                                }
                                currentEvent = null
                            }
                        }
                    } catch (e: Exception) {
                        synchronized(sseLock) {
                            if (sseCall === call) {
                                isSseConnected = false
                            }
                        }
                        if (!call.isCanceled()) {
                            Log.e(TAG, "SSE read error: ${e.message}")
                        }
                    } finally {
                        response.close()
                        var shouldRetry = false
                        synchronized(sseLock) {
                            if (sseCall === call) {
                                isSseConnected = false
                                sseCall = null
                                if (!call.isCanceled()) {
                                    sseRetryCount++
                                    shouldRetry = true
                                }
                            }
                        }
                        if (shouldRetry) {
                            val backoffMs = calculateSseBackoff()
                            pluginScope.launch {
                                delay(backoffMs.milliseconds)
                                startSseListener(appContext)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun calculateSseBackoff(): Long {
        val backoff = SSE_BASE_DELAY_MS * (1L shl minOf(sseRetryCount, 4))
        return minOf(backoff, SSE_MAX_BACKOFF_MS)
    }

    suspend fun pushAllCategories(context: Context) {
        val creds = CloudSyncStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        val resumeWatching = getResumeWatching()

        val categoryData = mutableMapOf<SyncCategory, Pair<String, String>>()
        for (category in SyncCategory.entries) {
            if (!creds.isBackupEnabled(category)) continue
            try {
                val backup = CloudSyncBackupUtils.getBackupForCategory(context, category, resumeWatching)
                if (backup != null) {
                    val data = backup.toJsonSorted()
                    val hash = CloudSyncBackupUtils.computeHash(data)
                    categoryData[category] = Pair(data, hash)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing ${category.key}: ${e.message}")
            }
        }

        if (categoryData.isNotEmpty()) {
            lastPushTimestamp = System.currentTimeMillis()
            val pushed = CloudSyncSettingsSyncUtils.pushCategories(categoryData)
            lastPushTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Force-pushed ${pushed.size}/${categoryData.size} categories")
        }
    }

    fun reload() {
        pluginScope.launch(Dispatchers.Main) {
            val act = activity
            if (act == null || act.isFinishing || act.isDestroyed) return@launch
            try {
                MainActivity.bookmarksUpdatedEvent.invoke(true)
                MainActivity.reloadLibraryEvent.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "reload events invoke failed: ${e.message}")
            }
        }
    }

    private suspend fun restoreAndReload(context: Context, category: SyncCategory, backupFile: BackupFile) {
        when (category) {
            SyncCategory.EXTENSIONS -> {
                CloudSyncBackupUtils.restoreCategory(context, category, backupFile)
                CloudSyncBackupUtils.restoreExtensionsCategory(context, backupFile)
            }
            SyncCategory.BOOKMARKS -> {
                CloudSyncBackupUtils.restoreCategory(context, category, backupFile)
                withContext(Dispatchers.Main) {
                    try { MainActivity.bookmarksUpdatedEvent(true) } catch (_: Throwable) {}
                }
            }
            SyncCategory.SETTINGS -> {
                CloudSyncBackupUtils.restoreCategory(context, category, backupFile)
                withContext(Dispatchers.Main) {
                    try {
                        val act = this@CloudSyncPlugin.activity
                        if (act != null && act.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            act.recreate()
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to recreate activity on settings sync: ${e.message}")
                    }
                }
            }
            else -> {
                CloudSyncBackupUtils.restoreCategory(context, category, backupFile)
            }
        }
    }

    suspend fun mergeAndSyncAllCategories(context: Context) {
        val appContext = context.applicationContext
        val creds = CloudSyncStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        val currentDirtyCategories = dirtyCategories.toSet()
        consumeDirtyCategories()

        val manifest = CloudSyncSettingsSyncUtils.fetchManifest()
        val resumeWatching = getResumeWatching()

        val enabledCategories = SyncCategory.entries.filter { cat ->
            creds.isBackupEnabled(cat) || creds.isRestoreEnabled(cat)
        }

        val cloudPayloads = coroutineScope {
            enabledCategories.map { category ->
                async(Dispatchers.IO) {
                    try {
                        val cloudMeta = manifest?.getMeta(category)
                        val localHash = CloudSyncStorageManager.getCategoryHash(category)
                        if (cloudMeta != null && cloudMeta.hash.isNotEmpty() && cloudMeta.hash == localHash) {
                            Log.d(TAG, "Skipping cloud download for ${category.key}: hash matches local")
                            category to null
                        } else {
                            val payload = CloudSyncSettingsSyncUtils.fetchCategory(category)
                            category to payload
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching category ${category.key}: ${e.message}")
                        category to null
                    }
                }
            }.awaitAll().toMap()
        }

        val categoriesToPush = mutableMapOf<SyncCategory, Pair<String, String>>()
        var restoredAny = false

        for (category in enabledCategories) {
            val isBackup = creds.isBackupEnabled(category)
            val isRestore = creds.isRestoreEnabled(category)
            if (!isBackup && !isRestore) continue

            try {
                val cloudPayload = cloudPayloads[category]
                val cloudBackup = if (cloudPayload != null && cloudPayload.data.isNotBlank()) {
                    try { mapper.readValue<BackupFile>(cloudPayload.data) } catch (_: Exception) { null }
                } else null

                val localBackup = CloudSyncBackupUtils.getBackupForCategory(appContext, category, resumeWatching)

                val isLocalEmpty = localBackup == null ||
                    ((localBackup.datastore.bool.isNullOrEmpty() && localBackup.datastore.int.isNullOrEmpty() && localBackup.datastore.string.isNullOrEmpty() && localBackup.datastore.float.isNullOrEmpty() && localBackup.datastore.long.isNullOrEmpty() && localBackup.datastore.stringSet.isNullOrEmpty()) &&
                     (localBackup.settings.bool.isNullOrEmpty() && localBackup.settings.int.isNullOrEmpty() && localBackup.settings.string.isNullOrEmpty() && localBackup.settings.float.isNullOrEmpty() && localBackup.settings.long.isNullOrEmpty() && localBackup.settings.stringSet.isNullOrEmpty()))

                val isCloudEmpty = cloudBackup == null ||
                    ((cloudBackup.datastore.bool.isNullOrEmpty() && cloudBackup.datastore.int.isNullOrEmpty() && cloudBackup.datastore.string.isNullOrEmpty() && cloudBackup.datastore.float.isNullOrEmpty() && cloudBackup.datastore.long.isNullOrEmpty() && cloudBackup.datastore.stringSet.isNullOrEmpty()) &&
                     (cloudBackup.settings.bool.isNullOrEmpty() && cloudBackup.settings.int.isNullOrEmpty() && cloudBackup.settings.string.isNullOrEmpty() && cloudBackup.settings.float.isNullOrEmpty() && cloudBackup.settings.long.isNullOrEmpty() && cloudBackup.settings.stringSet.isNullOrEmpty()))

                if (isLocalEmpty) {
                    if (!isCloudEmpty && isRestore) {
                        val cloudMeta = manifest?.getMeta(category)
                        val cloudHash = cloudMeta?.hash ?: ""
                        val localHash = CloudSyncStorageManager.getCategoryHash(category)
                        if (cloudHash != localHash) {
                            Log.d(TAG, "Local is empty for ${category.key}, pulling from cloud")
                            isRestoring = true
                            try {
                                restoreAndReload(appContext, category, cloudBackup)
                                restoredAny = true
                            } finally {
                                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                                withContext(Dispatchers.Main) {
                                    isRestoring = false
                                }
                            }
                            if (cloudMeta != null) {
                                CloudSyncStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                                CloudSyncStorageManager.setCategoryHash(category, cloudMeta.hash)
                            }
                        }
                    }
                } else if (isCloudEmpty) {
                    if (isBackup) {
                        val data = localBackup.toJsonSorted()
                        val hash = CloudSyncBackupUtils.computeHash(data)
                        val cloudMeta = manifest?.getMeta(category)
                        val cloudHash = cloudMeta?.hash ?: ""
                        if (hash != cloudHash) {
                            Log.d(TAG, "Cloud is empty for ${category.key}, queuing push")
                            categoriesToPush[category] = Pair(data, hash)
                        }
                    }
                } else {
                    val localCategoryTs = CloudSyncStorageManager.getCategoryTimestamp(category)
                    val cloudPayloadTs = cloudPayload?.ts ?: 0L
                    val isLocallyDirty = currentDirtyCategories.contains(category)
                    val mergedBackup = CloudSyncBackupUtils.mergeBackupFiles(localBackup, cloudBackup, localCategoryTs, cloudPayloadTs, isLocallyDirty)
                    if (mergedBackup != null) {
                        val data = mergedBackup.toJsonSorted()
                        val hash = CloudSyncBackupUtils.computeHash(data)
                        
                        val liveLocalData = localBackup.toJsonSorted()
                        val liveLocalHash = CloudSyncBackupUtils.computeHash(liveLocalData)

                        val cloudMeta = manifest?.getMeta(category)
                        val cloudHash = cloudMeta?.hash ?: ""

                        if (hash != liveLocalHash && isRestore) {
                            Log.d(TAG, "Merged data different from live local for ${category.key}, restoring")
                            isRestoring = true
                            try {
                                restoreAndReload(appContext, category, mergedBackup)
                                restoredAny = true
                            } finally {
                                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                                withContext(Dispatchers.Main) {
                                    isRestoring = false
                                }
                            }
                            CloudSyncStorageManager.setCategoryHash(category, hash)
                            if (cloudMeta != null) {
                                CloudSyncStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                            }

                            val keys = CloudSyncBackupUtils.getBackupFileKeys(mergedBackup)
                            CloudSyncStorageManager.setCategorySyncedKeys(category, keys)
                        }

                        if (hash != cloudHash && isBackup) {
                            Log.d(TAG, "Merged data different from cloud for ${category.key}, queuing push")
                            categoriesToPush[category] = Pair(data, hash)
                        } else if (cloudMeta != null && cloudHash.isNotEmpty() && CloudSyncStorageManager.getCategoryHash(category) != cloudHash) {
                            CloudSyncStorageManager.setCategoryHash(category, cloudHash)
                            CloudSyncStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error merging category ${category.key}: ${e.message}")
            }
        }

        if (categoriesToPush.isNotEmpty()) {
            lastPushTimestamp = System.currentTimeMillis()
            val pushed = CloudSyncSettingsSyncUtils.pushCategories(categoriesToPush)
            lastPushTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Batch pushed ${pushed.size}/${categoriesToPush.size} categories in mergeAndSyncAll")
            val failed = categoriesToPush.keys - pushed
            if (failed.isNotEmpty()) {
                Log.e(TAG, "Failed to push categories in mergeAndSyncAll: ${failed.map { it.key }}")
                synchronized(dirtyCategoriesLock) {
                    dirtyCategories.addAll(failed)
                }
                scheduleDebouncedPush()
            }
        }

        if (restoredAny) {
            reload()
        }
    }
}