package com.fitforge.data

import java.time.LocalDate

data class DayScheduleStatus(
    val dayOfWeek: Int, // 1 = Mon, 2 = Tue, ..., 7 = Sun
    val dayName: String,
    val dateString: String,
    val schedule: WorkoutSchedule?,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val isPast: Boolean
)

data class WeeklyConsistencyReport(
    val scheduledCount: Int,
    val completedCount: Int,
    val adherencePercent: Int,
    val currentStreakDays: Int,
    val dayStatuses: List<DayScheduleStatus>
)

object WorkoutScheduleDefaults {
    val defaultWeeklySchedule = listOf(
        WorkoutSchedule(
            dayOfWeek = 1,
            dayName = "Monday",
            routineId = "push_day",
            routineName = "Push Day (Chest, Shoulders, Triceps)",
            isRestDay = false,
            targetDurationMinutes = 50,
            defaultRestSeconds = 90
        ),
        WorkoutSchedule(
            dayOfWeek = 2,
            dayName = "Tuesday",
            routineId = "pull_day",
            routineName = "Pull Day (Back & Biceps)",
            isRestDay = false,
            targetDurationMinutes = 50,
            defaultRestSeconds = 90
        ),
        WorkoutSchedule(
            dayOfWeek = 3,
            dayName = "Wednesday",
            routineId = null,
            routineName = "Active Recovery / Mobility",
            isRestDay = true,
            targetDurationMinutes = 0,
            defaultRestSeconds = 60
        ),
        WorkoutSchedule(
            dayOfWeek = 4,
            dayName = "Thursday",
            routineId = "legs_day",
            routineName = "Legs & Core Day",
            isRestDay = false,
            targetDurationMinutes = 55,
            defaultRestSeconds = 120
        ),
        WorkoutSchedule(
            dayOfWeek = 5,
            dayName = "Friday",
            routineId = "upper_body",
            routineName = "Upper Body Strength",
            isRestDay = false,
            targetDurationMinutes = 45,
            defaultRestSeconds = 90
        ),
        WorkoutSchedule(
            dayOfWeek = 6,
            dayName = "Saturday",
            routineId = null,
            routineName = "Weekend Rest",
            isRestDay = true,
            targetDurationMinutes = 0,
            defaultRestSeconds = 60
        ),
        WorkoutSchedule(
            dayOfWeek = 7,
            dayName = "Sunday",
            routineId = null,
            routineName = "Weekend Rest",
            isRestDay = true,
            targetDurationMinutes = 0,
            defaultRestSeconds = 60
        )
    )

    fun calculateWeeklyConsistency(
        todayDate: LocalDate,
        schedules: List<WorkoutSchedule>,
        sessions: List<WorkoutSession>
    ): WeeklyConsistencyReport {
        val dayOfWeekValue = todayDate.dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val mondayDate = todayDate.minusDays((dayOfWeekValue - 1).toLong())
        val completedDates = sessions.filter { it.completed }.map { it.date }.toSet()

        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val statuses = mutableListOf<DayScheduleStatus>()
        var scheduledCount = 0
        var completedCount = 0

        for (i in 0 until 7) {
            val dayOfWeek = i + 1
            val currentDate = mondayDate.plusDays(i.toLong())
            val dateStr = currentDate.toString()
            val schedule = schedules.find { it.dayOfWeek == dayOfWeek }
            val isRest = schedule?.isRestDay ?: (dayOfWeek == 3 || dayOfWeek == 6 || dayOfWeek == 7)
            val isCompleted = completedDates.contains(dateStr)

            if (!isRest) {
                scheduledCount++
                if (isCompleted) {
                    completedCount++
                }
            }

            statuses.add(
                DayScheduleStatus(
                    dayOfWeek = dayOfWeek,
                    dayName = dayNames[i],
                    dateString = dateStr,
                    schedule = schedule,
                    isCompleted = isCompleted,
                    isToday = currentDate == todayDate,
                    isPast = currentDate.isBefore(todayDate)
                )
            )
        }

        val adherencePercent = if (scheduledCount > 0) {
            ((completedCount.toDouble() / scheduledCount.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        // Calculate consecutive completed days streak leading up to today
        var streak = 0
        var checkDate = todayDate
        while (completedDates.contains(checkDate.toString())) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return WeeklyConsistencyReport(
            scheduledCount = scheduledCount,
            completedCount = completedCount,
            adherencePercent = adherencePercent,
            currentStreakDays = streak,
            dayStatuses = statuses
        )
    }

    fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

data class RetroactiveSetEntry(
    val exercise: String,
    val reps: Int,
    val weightKg: Double,
    val weightType: String = "barbell",
    val rir: Int? = 2,
    val durationSeconds: Int = 30
)
