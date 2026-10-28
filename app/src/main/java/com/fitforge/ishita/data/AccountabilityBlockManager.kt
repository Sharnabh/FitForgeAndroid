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

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val webUsagePrefs: SharedPreferences = appContext.getSharedPreferences(
        WEB_USAGE_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _activeBlocksFlow = MutableStateFlow<List<ActiveBlock>>(emptyList())
    val activeBlocksFlow: StateFlow<List<ActiveBlock>> = _activeBlocksFlow.asStateFlow()

    private val _penaltiesFlow = MutableStateFlow<List<AccountabilityPenalty>>(emptyList())
    val penaltiesFlow: StateFlow<List<AccountabilityPenalty>> = _penaltiesFlow.asStateFlow()

    init {
        loadBlocks()
        loadPenalties()
    }

    companion object {
        private const val PREFS_NAME = "fitforge_accountability_blocks"
        private const val WEB_USAGE_PREFS_NAME = "fitforge_website_usage"
        private const val KEY_BLOCKS_JSON = "active_blocks_json"
        private const val KEY_PENALTIES_JSON = "accountability_penalties_json"

        val KNOWN_DISTRACTION_APPS = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.instagram.android" to "Instagram",
            "com.reddit.frontpage" to "Reddit",
            "com.twitter.android" to "X (Twitter)",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.ss.android.ugc.trill" to "TikTok",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "com.snapchat.android" to "Snapchat",
            "com.netflix.mediaclient" to "Netflix",
            "com.discord" to "Discord",
            "tv.twitch.android.app" to "Twitch",
            "com.android.chrome" to "Chrome",
            "org.mozilla.firefox" to "Firefox",
            "com.microsoft.emmx" to "Edge",
            "com.brave.browser" to "Brave",
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.spotify.music" to "Spotify",
            "com.valvesoftware.android.steam.community" to "Steam"
        )

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

    /**
     * Resolves an app name or raw package string to the canonical Android package name.
     */
    fun resolvePackageName(rawPackageOrName: String): String {
        val trimmed = rawPackageOrName.trim().lowercase(Locale.ROOT)
        if (trimmed.isBlank()) return ""
        if (trimmed.contains(".") && trimmed.split(".").size >= 3) {
            return trimmed
        }

        // Reverse lookup in KNOWN_DISTRACTION_APPS
        for ((pkg, label) in KNOWN_DISTRACTION_APPS) {
            val cleanLabel = label.lowercase(Locale.ROOT)
            if (cleanLabel == trimmed || cleanLabel.contains(trimmed) || trimmed.contains(cleanLabel)) {
                return pkg
            }
        }

        // Query installed applications
        try {
            val pm = appContext.packageManager
            val installed = pm.getInstalledApplications(0)
            for (app in installed) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label == trimmed || label.contains(trimmed) || trimmed.contains(label)) {
                    return app.packageName
                }
            }
            for (app in installed) {
                if (app.packageName.lowercase(Locale.ROOT).contains(trimmed)) {
                    return app.packageName
                }
            }
        } catch (_: Exception) {}

        return trimmed
    }

    /**
     * Resolves a package or raw name to a human-friendly display label.
     */
    fun resolveAppDisplayName(pkgOrName: String): String {
        val clean = pkgOrName.trim().lowercase(Locale.ROOT)
        KNOWN_DISTRACTION_APPS[clean]?.let { return it }
        for ((pkg, label) in KNOWN_DISTRACTION_APPS) {
            if (pkg.contains(clean) || clean.contains(pkg)) return label
        }
        try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(clean, 0)
            return pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {}

        return if (clean.contains(".")) {
            clean.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        } else {
            clean.replaceFirstChar { it.uppercase() }
        }
    }

    @Synchronized
    fun blockApp(rawPackageOrName: String, appName: String, durationMinutes: Int, reason: String) {
        val resolvedPkg = resolvePackageName(rawPackageOrName.ifBlank { appName })
        val resolvedName = if (appName.isNotBlank() && appName != "App") appName else resolveAppDisplayName(resolvedPkg)
        val until = System.currentTimeMillis() + (durationMinutes.toLong().coerceAtLeast(1) * 60_000)
        val block = ActiveBlock(
            id = resolvedPkg.ifBlank { rawPackageOrName.trim().lowercase(Locale.ROOT) },
            displayName = resolvedName,
            isWebsite = false,
            blockedUntilEpochMs = until,
            reason = reason
        )
        saveBlock(block)
    }

    @Synchronized
    fun blockWebsite(rawDomain: String, durationMinutes: Int, reason: String) {
        val domain = extractDomain(rawDomain) ?: rawDomain.trim().lowercase(Locale.ROOT)
        val until = System.currentTimeMillis() + (durationMinutes.toLong().coerceAtLeast(1) * 60_000)
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
        return _activeBlocksFlow.value.firstOrNull { block ->
            if (block.isWebsite || block.isExpired) return@firstOrNull false
            if (block.id == key) return@firstOrNull true
            if (block.id.isNotBlank() && (key.contains(block.id) || block.id.contains(key))) return@firstOrNull true
            val label = resolveAppDisplayName(key)
            if (label.equals(block.displayName, ignoreCase = true) || label.equals(block.id, ignoreCase = true)) return@firstOrNull true
            false
        }
    }

    @Synchronized
    fun isDomainBlocked(rawUrlOrDomain: String): ActiveBlock? {
        cleanExpiredBlocks()
        val domain = extractDomain(rawUrlOrDomain) ?: rawUrlOrDomain.trim().lowercase(Locale.ROOT)
        return _activeBlocksFlow.value.firstOrNull { it.isWebsite && (it.id == domain || domain.endsWith(".${it.id}")) && !it.isExpired }
    }

    @Synchronized
    fun unblock(idOrName: String) {
        val clean = idOrName.trim().lowercase(Locale.ROOT)
        val resolved = resolvePackageName(clean)
        val current = _activeBlocksFlow.value.filter {
            it.id != clean && it.id != resolved && !it.displayName.equals(clean, ignoreCase = true)
        }
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

    @Synchronized
    fun addPenalty(penalty: AccountabilityPenalty) {
        val current = _penaltiesFlow.value.toMutableList()
        current.removeAll { it.id == penalty.id }
        current.add(0, penalty)
        persistPenalties(current)
    }

    @Synchronized
    fun updatePenaltyStatus(penaltyId: String, newStatus: PenaltyStatus) {
        val current = _penaltiesFlow.value.map {
            if (it.id == penaltyId) {
                it.copy(
                    status = newStatus,
                    completedAtEpochMs = if (newStatus == PenaltyStatus.COMPLETED) System.currentTimeMillis() else it.completedAtEpochMs
                )
            } else it
        }
        persistPenalties(current)
    }

    @Synchronized
    fun deletePenalty(penaltyId: String) {
        val current = _penaltiesFlow.value.filter { it.id != penaltyId }
        persistPenalties(current)
    }

    @Synchronized
    fun clearAllPenalties() {
        persistPenalties(emptyList<AccountabilityPenalty>())
    }

    private fun persistPenalties(penalties: List<AccountabilityPenalty>) {
        _penaltiesFlow.value = penalties
        val array = JSONArray()
        penalties.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("type", p.type)
                put("title", p.title)
                put("description", p.description)
                put("reason", p.reason)
                put("issuedAtEpochMs", p.issuedAtEpochMs)
                put("status", p.status.name)
                put("target", p.target ?: "")
                put("durationMinutes", p.durationMinutes)
                put("completedAtEpochMs", p.completedAtEpochMs ?: -1L)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PENALTIES_JSON, array.toString()).apply()
    }

    private fun loadPenalties() {
        val raw = prefs.getString(KEY_PENALTIES_JSON, null) ?: return
        val list = mutableListOf<AccountabilityPenalty>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", PenaltyStatus.PENDING.name)
                val status = try {
                    PenaltyStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    PenaltyStatus.PENDING
                }
                val completedAt = obj.optLong("completedAtEpochMs", -1L)
                list.add(
                    AccountabilityPenalty(
                        id = obj.getString("id"),
                        type = obj.getString("type"),
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        reason = obj.getString("reason"),
                        issuedAtEpochMs = obj.optLong("issuedAtEpochMs", System.currentTimeMillis()),
                        status = status,
                        target = obj.optString("target").ifEmpty { null },
                        durationMinutes = obj.optInt("durationMinutes", 0),
                        completedAtEpochMs = if (completedAt > 0) completedAt else null
                    )
                )
            }
        } catch (_: Exception) {}
        _penaltiesFlow.value = list
    }
}
