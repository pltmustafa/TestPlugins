package com.pltmustafa

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object CloudSyncUtils {
    data class SectionInfo(
            @param:JsonProperty("name") var name: String,
            @param:JsonProperty("url") var url: String,
            @param:JsonProperty("pluginName") var pluginName: String,
            @param:JsonProperty("enabled") var enabled: Boolean = false,
            @param:JsonProperty("priority") var priority: Int = 0
    )

    data class ExtensionInfo(
            @param:JsonProperty("name") var name: String? = null,
            @param:JsonProperty("sections") var sections: Array<SectionInfo>? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ExtensionInfo

            if (name != other.name) return false
            if (!sections.contentEquals(other.sections)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + (sections?.contentHashCode() ?: 0)
            return result
        }
    }

    data class MediaProviderState(
            @param:JsonProperty("name") var name: String,
            @param:JsonProperty("enabled") var enabled: Boolean = true,
            @param:JsonProperty("customDomain") var customDomain: String? = null
    )
}

suspend fun <T> retry(
    times: Int = 3,
    delayMillis: Long = 1000,
    block: suspend () -> T
): T? {
    repeat(times - 1) {
        runCatching { return block() }.onFailure { delay(delayMillis) }
    }
    return runCatching { block() }.getOrNull()
}

suspend fun <T> runLimitedParallel(
    limit: Int = 4,
    blockList: List<suspend () -> T>
): List<T> {
    val semaphore = Semaphore(limit)
    return coroutineScope {
        blockList.map { block ->
            async(Dispatchers.IO) {
                semaphore.withPermit { block() }
            }
        }.awaitAll()
    }
}