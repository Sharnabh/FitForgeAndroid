package com.fitforge.ishita.data

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val foregroundMinutes: Long = totalTimeInForegroundMs / (1000 * 60),
    val lastTimeUsedEpochMs: Long,
    val isDistractionSuspect: Boolean = false
)

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    companion object {
        private val KNOWN_APP_NAMES = mapOf(
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
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.valvesoftware.android.steam.community" to "Steam"
        )

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )
    }

    private val knownDistractionPackages = KNOWN_APP_NAMES.keys

    /**
     * Checks if the user has granted PACKAGE_USAGE_STATS permission via Special App Access.
     */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Creates an intent to navigate directly to the system Usage Access settings page.
     */
    fun createUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Queries foreground app usage for the specified time window (default: last 24 hours).
     * Filters out system launchers, keyboard input methods, and the FitForge app itself.
     */
    suspend fun getDailyUsageStats(
        hoursBack: Int = 24,
        maxResults: Int = 10
    ): List<AppUsageStat> = withContext(Dispatchers.IO) {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return@withContext emptyList()
        }

        val endTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val startTime = (endTime - (hoursBack.toLong() * 60 * 60 * 1000)).coerceAtMost(startOfToday)

        var usageStatsList: List<UsageStats>? = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        if (usageStatsList.isNullOrEmpty()) {
            usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
        }
        if (usageStatsList.isNullOrEmpty()) return@withContext emptyList()

        // Group by package name in case there are multiple intervals reported
        val aggregatedByPackage = usageStatsList
            .filter {
                it.packageName != context.packageName &&
                !IGNORED_PACKAGES.contains(it.packageName) &&
                it.totalTimeInForeground > 0
            }
            .groupBy { it.packageName }
            .mapValues { entry ->
                val totalForeground = entry.value.sumOf { it.totalTimeInForeground }
                val maxLastUsed = entry.value.maxOf { it.lastTimeUsed }
                Pair(totalForeground, maxLastUsed)
            }

        val results = mutableListOf<AppUsageStat>()
        for ((pkgName, times) in aggregatedByPackage) {
            val (totalForegroundMs, lastTimeUsed) = times
            if (totalForegroundMs < 30_000L) continue // Skip under 30 seconds

            var appLabel: String? = try {
                val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isDistraction = knownDistractionPackages.contains(pkgName)

                if (isSystemApp && !isDistraction && pkgName != "com.android.chrome") {
                    null
                } else {
                    packageManager.getApplicationLabel(appInfo).toString()
                }
            } catch (_: Exception) {
                null
            }

            if (appLabel.isNullOrBlank()) {
                appLabel = KNOWN_APP_NAMES[pkgName]
            }

            if (appLabel.isNullOrBlank()) {
                if (!pkgName.startsWith("android") && !pkgName.startsWith("com.android.internal")) {
                    appLabel = pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }

            if (!appLabel.isNullOrBlank()) {
                results.add(
                    AppUsageStat(
                        packageName = pkgName,
                        appName = appLabel,
                        totalTimeInForegroundMs = totalForegroundMs,
                        lastTimeUsedEpochMs = lastTimeUsed,
                        isDistractionSuspect = knownDistractionPackages.contains(pkgName)
                    )
                )
            }
        }

        // Sort by foreground duration descending
        results.sortedByDescending { it.totalTimeInForegroundMs }.take(maxResults)
    }
}
