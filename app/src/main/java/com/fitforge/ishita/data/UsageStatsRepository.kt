package com.fitforge.ishita.data

import android.app.AppOpsManager
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

    // Common known distraction apps
    private val knownDistractionPackages = setOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok regional
        "com.facebook.katana",
        "com.snapchat.android",
        "com.netflix.mediaclient",
        "com.valvesoftware.android.steam.community",
        "com.discord",
        "tv.twitch.android.app",
        "com.android.chrome"
    )

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
     * Filters out system background services, the FitForge app itself, and system launchers.
     */
    suspend fun getDailyUsageStats(
        hoursBack: Int = 24,
        maxResults: Int = 10
    ): List<AppUsageStat> = withContext(Dispatchers.IO) {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return@withContext emptyList()
        }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (hoursBack.toLong() * 60 * 60 * 1000)

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return@withContext emptyList()

        // Group by package name in case there are multiple intervals reported
        val aggregatedByPackage = usageStatsList
            .filter { it.packageName != context.packageName && it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { entry ->
                val totalForeground = entry.value.sumOf { it.totalTimeInForeground }
                val maxLastUsed = entry.value.maxOf { it.lastTimeUsed }
                Pair(totalForeground, maxLastUsed)
            }

        // Resolve app labels and filter out invisible system-only packages
        val results = mutableListOf<AppUsageStat>()
        for ((pkgName, times) in aggregatedByPackage) {
            val (totalForegroundMs, lastTimeUsed) = times
            if (totalForegroundMs < 60_000) continue // Skip under 1 minute

            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                // Filter out core system apps that are not interactive user apps unless they're known browsers
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isDistraction = knownDistractionPackages.contains(pkgName)

                if (isSystemApp && !isDistraction && pkgName != "com.android.chrome") {
                    null
                } else {
                    packageManager.getApplicationLabel(appInfo).toString()
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

            if (appLabel != null) {
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
