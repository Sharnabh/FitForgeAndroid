package com.fitforge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitforge.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScheduleScreen(
    vm: FitForgeViewModel,
    onStartRoutine: (WorkoutRoutine) -> Unit
) {
    val schedules by vm.weeklySchedule.collectAsState()
    val routines by vm.workoutRoutines.collectAsState()
    val sessions by vm.workoutSessions.collectAsState()
    val exercises by vm.exercises.collectAsState()

    val todayDate = remember { LocalDate.now() }
    val consistencyReport = remember(schedules, sessions) {
        WorkoutScheduleDefaults.calculateWeeklyConsistency(todayDate, schedules, sessions)
    }

    var selectedDayForEdit by remember { mutableStateOf<WorkoutSchedule?>(null) }
    var retroactiveLogTarget by remember { mutableStateOf<Triple<WorkoutSchedule, String, String>?>(null) } // schedule, dateStr, dayName

    val mondayDate = remember(todayDate) {
        todayDate.minusDays((todayDate.dayOfWeek.value - 1).toLong())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Weekly Consistency Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Weekly Consistency",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${consistencyReport.completedCount} of ${consistencyReport.scheduledCount} training days completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Streak badge
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = FitForgeThemeColors.Secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "${consistencyReport.currentStreakDays}d streak",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Adherence Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Weekly Adherence",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${consistencyReport.adherencePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (consistencyReport.adherencePercent >= 80) FitForgeThemeColors.Success else MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (consistencyReport.adherencePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (consistencyReport.adherencePercent >= 80) FitForgeThemeColors.Success else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    // 7-Day Quick Visual Bubbles (Mon - Sun)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
                        consistencyReport.dayStatuses.forEachIndexed { idx, dayStatus ->
                            val isCompleted = dayStatus.isCompleted
                            val isRest = dayStatus.schedule?.isRestDay ?: (dayStatus.dayOfWeek == 3 || dayStatus.dayOfWeek == 6 || dayStatus.dayOfWeek == 7)
                            val isMissed = dayStatus.isPast && !isRest && !isCompleted

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isCompleted -> FitForgeThemeColors.Success
                                        isMissed -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        isRest -> MaterialTheme.colorScheme.surfaceVariant
                                        dayStatus.isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when {
                                            isCompleted -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            isMissed -> Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            isRest -> Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            dayStatus.isToday -> Text(dayLetters[idx], color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                            else -> Text(dayLetters[idx], color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    dayStatus.dayName.take(3),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dayStatus.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Weekly Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap to edit split & log past workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Day Cards (Monday to Sunday)
        val defaultDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        items((1..7).toList()) { dayNumber ->
            val schedule = schedules.find { it.dayOfWeek == dayNumber }
            val dayName = schedule?.dayName?.ifBlank { defaultDays[dayNumber - 1] } ?: defaultDays[dayNumber - 1]
            val isRest = schedule?.isRestDay ?: (dayNumber == 3 || dayNumber == 6 || dayNumber == 7)
            val routineName = schedule?.routineName ?: if (isRest) "Rest & Recovery" else "Push Day"
            val targetDuration = schedule?.targetDurationMinutes ?: if (isRest) 0 else 45
            val restSeconds = schedule?.defaultRestSeconds ?: 90

            val currentDayDate = mondayDate.plusDays((dayNumber - 1).toLong())
            val dateStr = currentDayDate.toString()
            val isToday = todayDate.dayOfWeek.value == dayNumber
            val isCompleted = sessions.any { it.date == dateStr && it.completed }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isToday) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                border = if (isToday) CardDefaults.outlinedCardBorder().copy(width = 1.5.dp) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (isToday) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "TODAY",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isCompleted) {
                                Surface(
                                    color = FitForgeThemeColors.Success.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = FitForgeThemeColors.Success, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Completed", color = FitForgeThemeColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        IconButton(onClick = {
                            selectedDayForEdit = schedule ?: WorkoutSchedule(
                                dayOfWeek = dayNumber,
                                dayName = dayName,
                                routineId = null,
                                routineName = routineName,
                                isRestDay = isRest,
                                targetDurationMinutes = targetDuration,
                                defaultRestSeconds = restSeconds
                            )
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Routine details
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isRest) {
                                    Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Text(routineName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(routineName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (!isRest) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⏱️ $targetDuration min target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("• ⏳ ${restSeconds}s rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRest && isToday) {
                            val matchedRoutine = routines.find { it.id == schedule?.routineId || it.name.equals(routineName, ignoreCase = true) }
                                ?: routines.firstOrNull()
                            if (matchedRoutine != null) {
                                Button(
                                    onClick = { onStartRoutine(matchedRoutine) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Live")
                                }
                            }
                        }

                        // Retroactive logging button: Log completed workout for this day
                        OutlinedButton(
                            onClick = {
                                val matchedRoutine = routines.find { it.id == schedule?.routineId }
                                    ?: routines.find { it.name.equals(routineName, ignoreCase = true) }
                                    ?: routines.find {
                                        val sName = schedule?.routineName ?: routineName
                                        sName.contains("Push", ignoreCase = true) && it.id == "push_day" ||
                                        sName.contains("Pull", ignoreCase = true) && it.id == "pull_day" ||
                                        sName.contains("Leg", ignoreCase = true) && it.id == "legs_day" ||
                                        sName.contains("Upper", ignoreCase = true) && it.id == "upper_body" ||
                                        sName.contains("Lower", ignoreCase = true) && it.id == "lower_body" ||
                                        sName.contains("Full", ignoreCase = true) && it.id == "full_body_power"
                                    }
                                    ?: routines.firstOrNull()

                                val sched = schedule?.copy(
                                    routineId = schedule.routineId ?: matchedRoutine?.id,
                                    routineName = schedule.routineName ?: matchedRoutine?.name ?: routineName
                                ) ?: WorkoutSchedule(
                                    dayOfWeek = dayNumber,
                                    dayName = dayName,
                                    routineId = matchedRoutine?.id,
                                    routineName = matchedRoutine?.name ?: routineName,
                                    isRestDay = isRest,
                                    targetDurationMinutes = targetDuration,
                                    defaultRestSeconds = restSeconds
                                )
                                retroactiveLogTarget = Triple(sched, dateStr, dayName)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.HistoryEdu, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isCompleted) "Add More Sets" else "Mark Done & Log")
                        }
                    }
                }
            }
        }
    }

    // 1. Edit Day Schedule Bottom Sheet
    if (selectedDayForEdit != null) {
        val editing = selectedDayForEdit!!
        val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        var isRestDayState by remember { mutableStateOf(editing.isRestDay) }
        var selectedRoutineId by remember { mutableStateOf(editing.routineId ?: routines.firstOrNull()?.id ?: "") }
        var targetDurationState by remember { mutableStateOf(editing.targetDurationMinutes.toString()) }
        var restSecondsState by remember { mutableStateOf(editing.defaultRestSeconds.toString()) }

        ModalBottomSheet(
            onDismissRequest = { selectedDayForEdit = null },
            sheetState = editSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Configure ${editing.dayName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { selectedDayForEdit = null }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Rest / Recovery Day", fontWeight = FontWeight.Medium)
                        Text("No scheduled training on this day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isRestDayState, onCheckedChange = { isRestDayState = it })
                }

                if (!isRestDayState) {
                    Text("Assigned Workout Plan:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        routines.forEach { r ->
                            Surface(
                                color = if (selectedRoutineId == r.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().clickable { selectedRoutineId = r.id }
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(r.splitCategory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selectedRoutineId == r.id) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = targetDurationState,
                            onValueChange = { targetDurationState = it.filter { c -> c.isDigit() } },
                            label = { Text("Duration (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = restSecondsState,
                            onValueChange = { restSecondsState = it.filter { c -> c.isDigit() } },
                            label = { Text("Rest Between Sets (sec)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { selectedDayForEdit = null }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val routine = routines.find { it.id == selectedRoutineId }
                            vm.updateDaySchedule(
                                dayOfWeek = editing.dayOfWeek,
                                dayName = editing.dayName,
                                routineId = if (isRestDayState) null else selectedRoutineId,
                                routineName = if (isRestDayState) "Rest & Recovery" else (routine?.name ?: "Workout"),
                                isRestDay = isRestDayState,
                                targetDurationMinutes = if (isRestDayState) 0 else (targetDurationState.toIntOrNull() ?: 45),
                                defaultRestSeconds = restSecondsState.toIntOrNull() ?: 90
                            )
                            selectedDayForEdit = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Schedule")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 2. Retroactive Workout Logger Bottom Sheet ("Forgot to log while doing it")
    if (retroactiveLogTarget != null) {
        val (targetSchedule, dateString, dayLabel) = retroactiveLogTarget!!
        RetroactiveWorkoutLogBottomSheet(
            schedule = targetSchedule,
            dateStr = dateString,
            dayName = dayLabel,
            routines = routines,
            allExercises = exercises,
            vm = vm,
            onDismiss = { retroactiveLogTarget = null },
            onSave = { date, day, duration, sets ->
                vm.logRetroactiveWorkout(date, day, duration, sets)
                retroactiveLogTarget = null
            }
        )
    }
}

// -------------------------------------------------------------
// RETROACTIVE WORKOUT LOGGING BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroactiveWorkoutLogBottomSheet(
    schedule: WorkoutSchedule,
    dateStr: String,
    dayName: String,
    routines: List<WorkoutRoutine>,
    allExercises: List<Exercise>,
    vm: FitForgeViewModel,
    onDismiss: () -> Unit,
    onSave: (date: String, dayName: String, durationMinutes: Int, sets: List<RetroactiveSetEntry>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    val allHistoricalSets by vm.allWorkoutSets.collectAsState()

    // Resolve matching routine from schedule
    val resolvedRoutine = remember(schedule, routines) {
        routines.find { it.id == schedule.routineId }
            ?: routines.find { it.name.equals(schedule.routineName, ignoreCase = true) }
            ?: routines.find {
                val sName = schedule.routineName ?: ""
                (sName.contains("Push", ignoreCase = true) && it.id == "push_day") ||
                (sName.contains("Pull", ignoreCase = true) && it.id == "pull_day") ||
                (sName.contains("Leg", ignoreCase = true) && it.id == "legs_day") ||
                (sName.contains("Upper", ignoreCase = true) && it.id == "upper_body") ||
                (sName.contains("Lower", ignoreCase = true) && it.id == "lower_body") ||
                (sName.contains("Full", ignoreCase = true) && it.id == "full_body_power")
            }
            ?: routines.firstOrNull()
    }

    var selectedRoutineId by remember {
        mutableStateOf(resolvedRoutine?.id ?: "")
    }
    var durationMinutesInput by remember {
        mutableStateOf(if (schedule.targetDurationMinutes > 0) schedule.targetDurationMinutes.toString() else "45")
    }

    val loggedSets = remember { mutableStateListOf<RetroactiveSetEntry>() }
    var showExercisePicker by remember { mutableStateOf(false) }
    var isLoadingSets by remember { mutableStateOf(true) }

    // Helper to seed sets from the prescribed routine exercises in the database
    fun populateFromRoutine(routineId: String) {
        coroutineScope.launch {
            isLoadingSets = true
            val prescribed = vm.getRoutineExercises(routineId)
            loggedSets.clear()
            if (prescribed.isNotEmpty()) {
                prescribed.forEach { pe ->
                    val prevSet = allHistoricalSets.lastOrNull { it.exercise.equals(pe.exerciseName, ignoreCase = true) }
                    val isBody = pe.exerciseName.contains("Pull-Up", ignoreCase = true) ||
                                 pe.exerciseName.contains("Dip", ignoreCase = true) ||
                                 pe.exerciseName.contains("Push-up", ignoreCase = true) ||
                                 pe.exerciseName.contains("Plank", ignoreCase = true)
                    val defaultWeight = when {
                        prevSet != null -> prevSet.weightKg
                        isBody -> 0.0
                        pe.exerciseName.contains("Squat", ignoreCase = true) || pe.exerciseName.contains("Deadlift", ignoreCase = true) -> 60.0
                        pe.exerciseName.contains("Bench", ignoreCase = true) -> 50.0
                        pe.exerciseName.contains("Dumbbell", ignoreCase = true) -> 14.0
                        pe.exerciseName.contains("Lateral", ignoreCase = true) -> 8.0
                        pe.exerciseName.contains("Curl", ignoreCase = true) -> 12.0
                        else -> 40.0
                    }
                    val defaultReps = prevSet?.reps ?: pe.targetRepsMin.coerceAtLeast(1)
                    val defaultWeightType = prevSet?.weightType ?: if (isBody) "bodyweight" else "barbell"
                    val count = pe.targetSets.coerceIn(1, 6)
                    repeat(count) {
                        loggedSets.add(
                            RetroactiveSetEntry(
                                exercise = pe.exerciseName,
                                reps = defaultReps,
                                weightKg = defaultWeight,
                                weightType = defaultWeightType,
                                rir = prevSet?.rir ?: 2,
                                durationSeconds = 35
                            )
                        )
                    }
                }
            } else {
                // Fallback compound movements if routine is not in DB yet
                val fallbackList = when {
                    resolvedRoutine?.splitCategory?.contains("Push", ignoreCase = true) == true ->
                        listOf("Barbell Bench Press", "Incline Dumbbell Press", "Barbell Overhead Press (OHP)", "Triceps Dips")
                    resolvedRoutine?.splitCategory?.contains("Pull", ignoreCase = true) == true ->
                        listOf("Conventional Deadlift", "Pull-ups / Chin-ups", "Bent-Over Barbell Row", "Barbell Bicep Curl")
                    resolvedRoutine?.splitCategory?.contains("Leg", ignoreCase = true) == true ->
                        listOf("Barbell Back Squat", "Romanian Deadlift (RDL)", "Walking Lunges (Dumbbell / Bodyweight)", "Standing Calf Raise")
                    else ->
                        listOf("Barbell Bench Press", "Barbell Back Squat", "Bent-Over Barbell Row")
                }
                fallbackList.forEach { exName ->
                    val isBody = exName.contains("Pull-up", ignoreCase = true) || exName.contains("Dip", ignoreCase = true)
                    loggedSets.add(
                        RetroactiveSetEntry(
                            exercise = exName,
                            reps = 10,
                            weightKg = if (isBody) 0.0 else 50.0,
                            weightType = if (isBody) "bodyweight" else "barbell",
                            rir = 2,
                            durationSeconds = 35
                        )
                    )
                }
            }
            isLoadingSets = false
        }
    }

    // On initial load: Check if existing session sets exist for this date, otherwise load prescribed plan
    LaunchedEffect(dateStr) {
        isLoadingSets = true
        val existing = vm.getExistingSetsForDate(dateStr)
        if (existing.isNotEmpty()) {
            loggedSets.clear()
            existing.forEach { s ->
                loggedSets.add(
                    RetroactiveSetEntry(
                        exercise = s.exercise,
                        reps = s.reps,
                        weightKg = s.weightKg,
                        weightType = s.weightType,
                        rir = s.rir ?: 2,
                        durationSeconds = s.durationSeconds
                    )
                )
            }
            isLoadingSets = false
        } else if (selectedRoutineId.isNotBlank()) {
            populateFromRoutine(selectedRoutineId)
        } else {
            isLoadingSets = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Log Completed Workout",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Mark $dayName ($dateStr) as done & record sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Scheduled Workout Plan Info Card
            val currentPlan = routines.find { it.id == selectedRoutineId } ?: resolvedRoutine
            var showPlanDropdown by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Scheduled Workout Plan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentPlan != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    currentPlan.splitCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Plan Selector Dropdown trigger
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPlanDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    currentPlan?.name ?: schedule.routineName ?: "Custom / Free Workout",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tap to switch plan or reload its exercises",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        DropdownMenu(
                            expanded = showPlanDropdown,
                            onDismissRequest = { showPlanDropdown = false }
                        ) {
                            routines.forEach { r ->
                                val isSelected = r.id == selectedRoutineId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                r.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                r.splitCategory,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        selectedRoutineId = r.id
                                        showPlanDropdown = false
                                        populateFromRoutine(r.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Duration & Add Exercise Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = durationMinutesInput,
                    onValueChange = { durationMinutesInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Total Duration (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { showExercisePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Exercise")
                }
            }

            if (isLoadingSets) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Sets list header with reset button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Logged Sets (${loggedSets.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                if (selectedRoutineId.isNotBlank()) {
                    TextButton(
                        onClick = { populateFromRoutine(selectedRoutineId) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Plan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (loggedSets.isEmpty() && !isLoadingSets) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No sets logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showExercisePicker = true }) {
                            Text("+ Tap here to select an exercise from database")
                        }
                    }
                }
            } else {
                loggedSets.forEachIndexed { index, setEntry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Set ${index + 1}: ${setEntry.exercise}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TextButton(
                                        onClick = { loggedSets.add(index + 1, setEntry.copy()) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("+ Set", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { loggedSets.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Remove set", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Steppers for Weight & Reps
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Weight
                                Column(Modifier.weight(1f)) {
                                    Text("Weight (kg)", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                val nextW = (setEntry.weightKg - 2.5).coerceAtLeast(0.0)
                                                loggedSets[index] = setEntry.copy(weightKg = nextW)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            "${setEntry.weightKg} kg",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val nextW = setEntry.weightKg + 2.5
                                                loggedSets[index] = setEntry.copy(weightKg = nextW)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Reps
                                Column(Modifier.weight(1f)) {
                                    Text("Reps", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                val nextR = (setEntry.reps - 1).coerceAtLeast(1)
                                                loggedSets[index] = setEntry.copy(reps = nextR)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            "${setEntry.reps} reps",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                val nextR = setEntry.reps + 1
                                                loggedSets[index] = setEntry.copy(reps = nextR)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val duration = durationMinutesInput.toIntOrNull() ?: 45
                        val matchedName = routines.find { it.id == selectedRoutineId }?.name ?: dayName
                        onSave(dateStr, matchedName, duration, loggedSets.toList())
                    },
                    modifier = Modifier.weight(1f),
                    enabled = loggedSets.isNotEmpty()
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save & Mark Done")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showExercisePicker) {
        ExercisePickerBottomSheet(
            allExercises = allExercises,
            onDismiss = { showExercisePicker = false },
            onSelect = { ex ->
                val isBody = ex.equipment.contains("body", ignoreCase = true)
                loggedSets.add(
                    RetroactiveSetEntry(
                        exercise = ex.name,
                        reps = 10,
                        weightKg = if (isBody) 0.0 else 40.0,
                        weightType = if (isBody) "bodyweight" else "barbell",
                        rir = 2,
                        durationSeconds = 30
                    )
                )
                showExercisePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 3. CREATE CUSTOM WORKOUT ROUTINE BOTTOM SHEET
// -------------------------------------------------------------
data class CustomRoutineExerciseDraft(
    val exerciseId: String,
    val exerciseName: String,
    val setsText: String = "3",
    val repsMinText: String = "8",
    val repsMaxText: String = "12",
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomRoutineBottomSheet(
    vm: FitForgeViewModel,
    onDismiss: () -> Unit
) {
    val allExercises by vm.exercises.collectAsState()
    var routineName by remember { mutableStateOf("") }
    var splitCategory by remember { mutableStateOf("Custom") }
    var description by remember { mutableStateOf("") }

    val routineExercises = remember { mutableStateListOf<CustomRoutineExerciseDraft>() }
    var showExercisePicker by remember { mutableStateOf(false) }

    val categories = listOf("Custom", "Push", "Pull", "Legs", "Upper", "Lower", "Full Body")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Create Custom Workout Plan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = routineName,
                onValueChange = { routineName = it },
                label = { Text("Routine Name (e.g. Hypertrophy Upper)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Split category chips
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = splitCategory == cat,
                        onClick = { splitCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Exercises (${routineExercises.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { showExercisePicker = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Exercise")
                }
            }

            if (routineExercises.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No exercises added yet. Tap 'Add Exercise' to select from the 800+ exercise library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                routineExercises.forEachIndexed { index, ex ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}. ${ex.exerciseName}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                IconButton(onClick = { routineExercises.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ex.setsText,
                                    onValueChange = { str ->
                                        routineExercises[index] = ex.copy(setsText = str.filter { it.isDigit() })
                                    },
                                    label = { Text("Sets") },
                                    placeholder = { Text("3") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = ex.repsMinText,
                                    onValueChange = { str ->
                                        routineExercises[index] = ex.copy(repsMinText = str.filter { it.isDigit() })
                                    },
                                    label = { Text("Min Reps") },
                                    placeholder = { Text("8") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = ex.repsMaxText,
                                    onValueChange = { str ->
                                        routineExercises[index] = ex.copy(repsMaxText = str.filter { it.isDigit() })
                                    },
                                    label = { Text("Max Reps") },
                                    placeholder = { Text("12") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (routineName.isNotBlank() && routineExercises.isNotEmpty()) {
                            val finalExercises = routineExercises.mapIndexed { idx, draft ->
                                val sets = draft.setsText.toIntOrNull()?.coerceAtLeast(1) ?: 3
                                val minReps = draft.repsMinText.toIntOrNull()?.coerceAtLeast(1) ?: 8
                                val maxReps = (draft.repsMaxText.toIntOrNull()?.coerceAtLeast(minReps)) ?: (minReps + 4)
                                RoutineExercise(
                                    routineId = "",
                                    exerciseId = draft.exerciseId,
                                    exerciseName = draft.exerciseName,
                                    targetSets = sets,
                                    targetRepsMin = minReps,
                                    targetRepsMax = maxReps,
                                    orderIndex = idx + 1,
                                    notes = draft.notes
                                )
                            }
                            vm.saveCustomRoutine(
                                name = routineName.trim(),
                                splitCategory = splitCategory,
                                description = description.trim(),
                                exercises = finalExercises
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = routineName.isNotBlank() && routineExercises.isNotEmpty()
                ) {
                    Text("Save Routine")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showExercisePicker) {
        ExercisePickerBottomSheet(
            allExercises = allExercises,
            onDismiss = { showExercisePicker = false },
            onSelect = { selectedEx ->
                routineExercises.add(
                    CustomRoutineExerciseDraft(
                        exerciseId = selectedEx.id,
                        exerciseName = selectedEx.name,
                        setsText = "3",
                        repsMinText = "8",
                        repsMaxText = "12",
                        notes = "${selectedEx.muscleGroup} • ${selectedEx.equipment}"
                    )
                )
                showExercisePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 4. EXERCISE PICKER BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerBottomSheet(
    allExercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSelect: (Exercise) -> Unit
) {
    var pickerSearchQuery by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf("All") }
    val muscleGroups = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio")

    val filteredExercises = remember(allExercises, pickerSearchQuery, selectedMuscle) {
        allExercises.filter { ex ->
            val matchMuscle = selectedMuscle == "All" || ex.muscleGroup.equals(selectedMuscle, ignoreCase = true)
            val matchQuery = pickerSearchQuery.isBlank() || ex.name.contains(pickerSearchQuery, ignoreCase = true) || ex.equipment.contains(pickerSearchQuery, ignoreCase = true)
            matchMuscle && matchQuery
        }.take(50)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Exercise from DB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = pickerSearchQuery,
                onValueChange = { pickerSearchQuery = it },
                placeholder = { Text("Search 800+ exercises...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                muscleGroups.forEach { muscle ->
                    FilterChip(
                        selected = selectedMuscle == muscle,
                        onClick = { selectedMuscle = muscle },
                        label = { Text(muscle) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredExercises) { ex ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(ex) }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(ex.muscleGroup, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("• ${ex.equipment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// 5. LOG SET FROM CATALOGUE BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSetFromCatalogueBottomSheet(
    exerciseName: String,
    muscleGroup: String = "",
    equipment: String = "Barbell",
    initialDurationSeconds: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (weightKg: Double, reps: Int, weightType: String, rir: Int?, durationSeconds: Int, startRestTimer: Boolean) -> Unit
) {
    var weightInput by remember { mutableStateOf(if (equipment.contains("Body", ignoreCase = true)) "0" else "40") }
    var repsInput by remember { mutableStateOf("10") }
    var weightTypeInput by remember {
        mutableStateOf(
            when {
                equipment.contains("Dumbbell", ignoreCase = true) -> "dumbbell"
                equipment.contains("Body", ignoreCase = true) -> "bodyweight"
                equipment.contains("Machine", ignoreCase = true) || equipment.contains("Cable", ignoreCase = true) -> "machine"
                else -> "barbell"
            }
        )
    }
    var rirInput by remember { mutableStateOf<Int?>(2) }
    var durationSecInput by remember { mutableStateOf(if (initialDurationSeconds > 0) initialDurationSeconds.toString() else "30") }
    var startRestTimerCheckbox by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Log Set: $exerciseName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    if (muscleGroup.isNotBlank()) {
                        Text("$muscleGroup • $equipment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Weight measurement type selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Weight Type:", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "barbell" to "Barbell",
                        "dumbbell" to "Dumbbell (Per DB)",
                        "bodyweight" to "Bodyweight",
                        "machine" to "Machine / Cable"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = weightTypeInput == type,
                            onClick = { weightTypeInput = type },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Weight Input & Steppers
            if (weightTypeInput != "bodyweight") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (weightTypeInput == "dumbbell") "Weight / DB (kg)" else "Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(onClick = {
                            val cur = weightInput.toDoubleOrNull() ?: 0.0
                            weightInput = (cur - 2.5).coerceAtLeast(0.0).toString()
                        }) { Text("-2.5") }
                        FilledTonalButton(onClick = {
                            val cur = weightInput.toDoubleOrNull() ?: 0.0
                            weightInput = (cur + 2.5).toString()
                        }) { Text("+2.5") }
                    }
                }
            }

            // Reps Input & Steppers
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = { repsInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = {
                        val cur = repsInput.toIntOrNull() ?: 10
                        repsInput = (cur - 1).coerceAtLeast(1).toString()
                    }) { Text("-1") }
                    FilledTonalButton(onClick = {
                        val cur = repsInput.toIntOrNull() ?: 10
                        repsInput = (cur + 1).toString()
                    }) { Text("+1") }
                }
            }

            // Set Duration
            OutlinedTextField(
                value = durationSecInput,
                onValueChange = { durationSecInput = it.filter { c -> c.isDigit() } },
                label = { Text("Set Duration (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // RIR (Reps in Reserve) Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Effort / RIR (Reps in Reserve):", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "0 (Failure)", 1 to "1 RIR", 2 to "2 RIR", 3 to "3+ RIR").forEach { (r, label) ->
                        FilterChip(
                            selected = rirInput == r,
                            onClick = { rirInput = r },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Start Rest Timer Checkbox
            Row(
                Modifier.fillMaxWidth().clickable { startRestTimerCheckbox = !startRestTimerCheckbox },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = startRestTimerCheckbox,
                    onCheckedChange = { startRestTimerCheckbox = it }
                )
                Text("Auto-start 90s Rest Countdown after saving", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val w = if (weightTypeInput == "bodyweight") 0.0 else (weightInput.toDoubleOrNull() ?: 0.0)
                        val r = repsInput.toIntOrNull() ?: 10
                        val dur = durationSecInput.toIntOrNull() ?: 0
                        onConfirm(w, r, weightTypeInput, rirInput, dur, startRestTimerCheckbox)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Set")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
