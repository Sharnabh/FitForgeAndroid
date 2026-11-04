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
    val splitCategory: String? = null,
    val targetMuscles: String? = null,
    val routineExercises: List<String> = emptyList(),
    val lastCompletedDate: String? = null,
    val lastCompletedWorkout: String? = null,
    val workoutStatus: String, // "COMPLETED", "IN_PROGRESS", "PENDING"
    val completedSessionsThisWeek: Int,
    val missedSessionsThisWeek: Int,
    val streakDays: Int,
    val topDistractingApps: List<AppUsageDto>,
    val topDistractingWebsites: List<WebsiteUsageDto>
)

enum class PenaltyStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    REJECTED
}

data class AccountabilityPenalty(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "BLOCK_APP", "BLOCK_WEBSITE", "SCHEDULE_MAKEUP", "DISCIPLINE_TASK", "CREATE_REMINDER"
    val title: String,
    val description: String,
    val reason: String,
    val issuedAtEpochMs: Long = System.currentTimeMillis(),
    val status: PenaltyStatus = PenaltyStatus.PENDING,
    val target: String? = null,
    val durationMinutes: Int = 0,
    val completedAtEpochMs: Long? = null
)
