package com.pltmustafa

import com.pltmustafa.CloudSyncUtils.ExtensionInfo
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey

object CloudSyncStorageManager {

    var extNameOnHome: Boolean
        get() = getKey("CLOUDSYNC_EXT_NAME_ON_HOME") ?: true
        set(value) {
            setKey("CLOUDSYNC_EXT_NAME_ON_HOME", value)
        }

    var currentExtensions: Array<ExtensionInfo>
        get() = getKey("CLOUDSYNC_EXTENSIONS_LIST") ?: emptyArray<ExtensionInfo>()
        set(value) {
            setKey("CLOUDSYNC_EXTENSIONS_LIST", value)
        }

    var appSettingsSyncCreds: AppSettingsSyncCreds?
        get() = getKey("CLOUDSYNC_APP_SETTINGS_SYNC_CREDS")
        set(value) {
            setKey("CLOUDSYNC_APP_SETTINGS_SYNC_CREDS", value)
        }

    var lastLocalSyncTime: Long
        get() = getKey("CLOUDSYNC_LAST_LOCAL_SYNC_TIME") ?: 0L
        set(value) {
            setKey("CLOUDSYNC_LAST_LOCAL_SYNC_TIME", value)
        }

    var syncV2Migrated: Boolean
        get() = getKey("CLOUDSYNC_SYNC_V2_MIGRATED") ?: false
        set(value) {
            setKey("CLOUDSYNC_SYNC_V2_MIGRATED", value)
        }

    fun getCategoryTimestamp(category: SyncCategory): Long {
        return getKey("CLOUDSYNC_SYNC_TS_${category.key}") ?: 0L
    }

    fun setCategoryTimestamp(category: SyncCategory, ts: Long) {
        setKey("CLOUDSYNC_SYNC_TS_${category.key}", ts)
    }

    fun getCategoryHash(category: SyncCategory): String {
        return getKey("CLOUDSYNC_SYNC_HASH_${category.key}") ?: ""
    }

    fun setCategoryHash(category: SyncCategory, hash: String) {
        setKey("CLOUDSYNC_SYNC_HASH_${category.key}", hash)
    }

    fun getCategorySyncedKeys(category: SyncCategory): Set<String> {
        return getKey<Array<String>>("CLOUDSYNC_SYNCED_KEYS_${category.key}")?.toSet() ?: emptySet()
    }

    fun setCategorySyncedKeys(category: SyncCategory, keys: Set<String>) {
        setKey("CLOUDSYNC_SYNCED_KEYS_${category.key}", keys.toTypedArray())
    }

    fun deleteAllData() {
        listOf(
                        "CLOUDSYNC_PROVIDER_LIST",
                        "CLOUDSYNC_EXT_NAME_ON_HOME",
                        "CLOUDSYNC_EXTENSIONS_LIST",
                        "CLOUDSYNC_CURRENT_META_PROVIDERS",
                        "CLOUDSYNC_CURRENT_MEDIA_PROVIDERS",
                        "CLOUDSYNC_APP_SETTINGS_SYNC_CREDS",
                        "CLOUDSYNC_LAST_LOCAL_SYNC_TIME",
                        "CLOUDSYNC_SYNC_V2_MIGRATED"
                )
                .forEach { setKey(it, null) }
        SyncCategory.entries.forEach { cat ->
            setKey("CLOUDSYNC_SYNC_TS_${cat.key}", null)
            setKey("CLOUDSYNC_SYNC_HASH_${cat.key}", null)
            setKey("CLOUDSYNC_SYNCED_KEYS_${cat.key}", null)
        }
    }
}
