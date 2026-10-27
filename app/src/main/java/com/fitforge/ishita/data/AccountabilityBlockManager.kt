package com.fitforge.ishita.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class ActiveBlock(
    val id: String, // package name or domain (e.g. "com.instagram.android" or "reddit.com")
    val displayName: String,
    val isWebsite: Boolean,
    val blockedUntilEpochMs: Long,
    val reason: String
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= blockedUntilEpochMs
    val remainingMinutes: Long get() = ((blockedUntilEpochMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
}

data class WebsiteUsageStat(
    val domain: String,
    val totalSeconds: Long,
    val visitCount: Int,
    val lastVisitedEpochMs: Long,
    val totalMinutes: Long = totalSeconds / 60
)

class AccountabilityBlockManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val webUsagePrefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        WEB_USAGE_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _activeBlocksFlow = MutableStateFlow<List<ActiveBlock>>(emptyList())
    val activeBlocksFlow: StateFlow<List<ActiveBlock>> = _activeBlocksFlow.asStateFlow()

    init {
        loadBlocks()
    }

    companion object {
        private const val PREFS_NAME = "fitforge_accountability_blocks"
        private const val WEB_USAGE_PREFS_NAME = "fitforge_website_usage"
        private const val KEY_BLOCKS_JSON = "active_blocks_json"

        @Volatile
        private var INSTANCE: AccountabilityBlockManager? = null

        fun get(context: Context): AccountabilityBlockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AccountabilityBlockManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun extractDomain(rawUrl: String): String? {
            return try {
                var cleaned = rawUrl.trim()
                if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
                    cleaned = "https://$cleaned"
                }
                val uri = URI(cleaned)
                val host = uri.host?.lowercase(Locale.ROOT) ?: return null
                if (host.startsWith("www.")) host.substring(4) else host
            } catch (_: Exception) {
                null
            }
        }
    }

    @Synchronized
    fun blockApp(packageName: String, appName: String, durationMinutes: Int, reason: String) {
        val until = System.currentTimeMillis() + (durationMinutes.toLong() * 60_000)
        val block = ActiveBlock(
            id = packageName.trim().lowercase(Locale.ROOT),
            displayName = appName,
            isWebsite = false,
            blockedUntilEpochMs = until,
            reason = reason
        )
        saveBlock(block)
    }

    @Synchronized
    fun blockWebsite(rawDomain: String, durationMinutes: Int, reason: String) {
        val domain = extractDomain(rawDomain) ?: rawDomain.trim().lowercase(Locale.ROOT)
        val until = System.currentTimeMillis() + (durationMinutes.toLong() * 60_000)
        val block = ActiveBlock(
            id = domain,
            displayName = domain,
            isWebsite = true,
            blockedUntilEpochMs = until,
            reason = reason
        )
        saveBlock(block)
    }

    @Synchronized
    fun isAppBlocked(packageName: String): ActiveBlock? {
        cleanExpiredBlocks()
        val key = packageName.trim().lowercase(Locale.ROOT)
        return _activeBlocksFlow.value.firstOrNull { !it.isWebsite && it.id == key && !it.isExpired }
    }

    @Synchronized
    fun isDomainBlocked(rawUrlOrDomain: String): ActiveBlock? {
        cleanExpiredBlocks()
        val domain = extractDomain(rawUrlOrDomain) ?: rawUrlOrDomain.trim().lowercase(Locale.ROOT)
        return _activeBlocksFlow.value.firstOrNull { it.isWebsite && (it.id == domain || domain.endsWith(".${it.id}")) && !it.isExpired }
    }

    @Synchronized
    fun unblock(id: String) {
        val current = _activeBlocksFlow.value.filter { it.id != id.trim().lowercase(Locale.ROOT) }
        persistBlocks(current)
    }

    @Synchronized
    fun unblockAll() {
        persistBlocks(emptyList())
    }

    @Synchronized
    fun recordDomainUsage(domain: String, durationSeconds: Long) {
        val cleanDomain = domain.trim().lowercase(Locale.ROOT)
        if (cleanDomain.isBlank() || durationSeconds <= 0) return

        val now = System.currentTimeMillis()
        val rawJson = webUsagePrefs.getString(cleanDomain, null)
        val stat = if (rawJson != null) {
            try {
                val obj = JSONObject(rawJson)
                WebsiteUsageStat(
                    domain = cleanDomain,
                    totalSeconds = obj.optLong("totalSeconds", 0L) + durationSeconds,
                    visitCount = obj.optInt("visitCount", 0) + 1,
                    lastVisitedEpochMs = now
                )
            } catch (_: Exception) {
                WebsiteUsageStat(cleanDomain, durationSeconds, 1, now)
            }
        } else {
            WebsiteUsageStat(cleanDomain, durationSeconds, 1, now)
        }

        val jsonObj = JSONObject().apply {
            put("domain", stat.domain)
            put("totalSeconds", stat.totalSeconds)
            put("visitCount", stat.visitCount)
            put("lastVisitedEpochMs", stat.lastVisitedEpochMs)
        }
        webUsagePrefs.edit().putString(cleanDomain, jsonObj.toString()).apply()
    }

    fun getDailyWebsiteUsage(hoursBack: Int = 24, maxResults: Int = 10): List<WebsiteUsageStat> {
        val cutoff = System.currentTimeMillis() - (hoursBack.toLong() * 60 * 60 * 1000)
        val allEntries = webUsagePrefs.all
        val results = mutableListOf<WebsiteUsageStat>()

        for ((_, value) in allEntries) {
            val jsonStr = value as? String ?: continue
            try {
                val obj = JSONObject(jsonStr)
                val lastVisited = obj.optLong("lastVisitedEpochMs", 0L)
                if (lastVisited >= cutoff) {
                    val stat = WebsiteUsageStat(
                        domain = obj.optString("domain"),
                        totalSeconds = obj.optLong("totalSeconds", 0L),
                        visitCount = obj.optInt("visitCount", 1),
                        lastVisitedEpochMs = lastVisited
                    )
                    if (stat.totalSeconds >= 10) { // filter out momentary flashes under 10 seconds
                        results.add(stat)
                    }
                }
            } catch (_: Exception) {}
        }

        return results.sortedByDescending { it.totalSeconds }.take(maxResults)
    }

    private fun saveBlock(block: ActiveBlock) {
        val current = _activeBlocksFlow.value.filter { it.id != block.id }.toMutableList()
        current.add(block)
        persistBlocks(current)
    }

    private fun cleanExpiredBlocks() {
        val current = _activeBlocksFlow.value
        val valid = current.filter { !it.isExpired }
        if (valid.size != current.size) {
            persistBlocks(valid)
        }
    }

    private fun persistBlocks(blocks: List<ActiveBlock>) {
        _activeBlocksFlow.value = blocks
        val array = JSONArray()
        blocks.forEach { b ->
            val obj = JSONObject().apply {
                put("id", b.id)
                put("displayName", b.displayName)
                put("isWebsite", b.isWebsite)
                put("blockedUntilEpochMs", b.blockedUntilEpochMs)
                put("reason", b.reason)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_BLOCKS_JSON, array.toString()).apply()
    }

    private fun loadBlocks() {
        val raw = prefs.getString(KEY_BLOCKS_JSON, null) ?: return
        val list = mutableListOf<ActiveBlock>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val block = ActiveBlock(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    isWebsite = obj.getBoolean("isWebsite"),
                    blockedUntilEpochMs = obj.getLong("blockedUntilEpochMs"),
                    reason = obj.getString("reason")
                )
                if (!block.isExpired) {
                    list.add(block)
                }
            }
        } catch (_: Exception) {}
        _activeBlocksFlow.value = list
    }
}
