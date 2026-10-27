package com.fitforge.ishita.data

data class AppUsageDto(
    val packageName: String,
    val appName: String,
    val foregroundMinutes: Int
)

data class WebsiteUsageDto(
    val domain: String,
    val foregroundMinutes: Int,
    val visitCount: Int
)

data class AccountabilityContextDto(
    val localDate: String,
    val plannedWorkout: String?,
    val workoutStatus: String, // "COMPLETED", "PENDING", "MISSED"
    val completedSessionsThisWeek: Int,
    val missedSessionsThisWeek: Int,
    val streakDays: Int,
    val topDistractingApps: List<AppUsageDto>,
    val topDistractingWebsites: List<WebsiteUsageDto>
)
