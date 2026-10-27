package com.fitforge

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitforge.data.*
import com.fitforge.data.api.FoodApiClient
import com.fitforge.data.api.FoodSearchResult
import com.fitforge.data.api.WgerApiClient
import com.fitforge.data.api.WgerRoutineBundle
import com.fitforge.data.chatgpt.ChatGptAuthManager
import com.fitforge.data.chatgpt.ChatGptClient
import com.fitforge.data.chatgpt.ChatGptModel
import com.fitforge.data.chatgpt.ChatGptSession
import com.fitforge.data.chatgpt.CoachMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate


// -------------------------------------------------------------
// 3. WORKOUTS MODULE (Today's Workout Logging & Exercise Catalogue)
// -------------------------------------------------------------
@Composable
fun WorkoutModuleScreen(vm: FitForgeViewModel) {
    var workoutSubTab by remember { mutableStateOf(0) } // 0: Session, 1: Schedule, 2: Plans, 3: Catalogue

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = workoutSubTab) {
            Tab(selected = workoutSubTab == 0, onClick = { workoutSubTab = 0 }, text = { Text("Session") }, icon = { Icon(Icons.Default.FitnessCenter, null) })
            Tab(selected = workoutSubTab == 1, onClick = { workoutSubTab = 1 }, text = { Text("Schedule") }, icon = { Icon(Icons.Default.DateRange, null) })
            Tab(selected = workoutSubTab == 2, onClick = { workoutSubTab = 2 }, text = { Text("Plans") }, icon = { Icon(Icons.Default.Assignment, null) })
            Tab(selected = workoutSubTab == 3, onClick = { workoutSubTab = 3 }, text = { Text("Catalogue") }, icon = { Icon(Icons.Default.FormatListBulleted, null) })
        }

        Box(Modifier.weight(1f)) {
            when (workoutSubTab) {
                0 -> WorkoutLoggingScreen(
                    vm = vm,
                    onBrowsePlans = { workoutSubTab = 2 },
                    onBrowseSchedule = { workoutSubTab = 1 }
                )
                1 -> WorkoutScheduleScreen(
                    vm = vm,
                    onStartRoutine = { routine ->
                        vm.loadRoutineIntoSession(routine)
                        vm.startWorkoutTimer()
                        workoutSubTab = 0
                    }
                )
                2 -> WorkoutRoutinesScreen(
                    vm = vm,
                    onStartRoutine = { workoutSubTab = 0 },
                    onAssignRoutine = { workoutSubTab = 1 }
                )
                3 -> ExerciseCatalogueScreen(
                    vm = vm,
                    onLogExercise = { exName ->
                        vm.addExerciseToActiveSession(exName)
                        workoutSubTab = 0
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutLoggingScreen(
    vm: FitForgeViewModel,
    onBrowsePlans: () -> Unit,
    onBrowseSchedule: () -> Unit = {}
) {
    val todaySession by vm.todaySession.collectAsState()
    val todaySets by vm.todaySets.collectAsState()
    val allWorkoutSets by vm.allWorkoutSets.collectAsState()
    val exercises by vm.exercises.collectAsState()

    var dayName by remember(todaySession) { mutableStateOf(todaySession?.dayName ?: "Strength & Conditioning") }
    var durationText by remember(todaySession) { mutableStateOf((todaySession?.durationMinutes ?: 45).toString()) }
    var distanceText by remember(todaySession) { mutableStateOf((todaySession?.distanceKm ?: 0.0).toString()) }
    var notesText by remember(todaySession) { mutableStateOf(todaySession?.notes ?: "") }
    var completed by remember(todaySession) { mutableStateOf(todaySession?.completed ?: false) }

    val durationMin = durationText.toIntOrNull() ?: 45
    val estimatedBurn = vm.calculateMetCalories(5.5, durationMin)

    // Add Set State
    var showAddSetDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var selectedExerciseName by remember { mutableStateOf("Barbell Bench Press") }
    var repsInput by remember { mutableStateOf("10") }
    var weightInput by remember { mutableStateOf("50") }
    var weightTypeInput by remember { mutableStateOf("barbell") }
    var rirInput by remember { mutableStateOf<Int?>(2) }
    var durationSecInput by remember { mutableStateOf("30") }
    var startRestCheckbox by remember { mutableStateOf(true) }

    val activeExercises = vm.activeRoutineExercises
    val currentExerciseIndex = vm.currentExerciseIndex
    val completedExercises = vm.completedExerciseNames

    LaunchedEffect(currentExerciseIndex, activeExercises.size) {
        if (activeExercises.isNotEmpty() && currentExerciseIndex in activeExercises.indices) {
            selectedExerciseName = activeExercises[currentExerciseIndex].exerciseName
        }
    }

    LaunchedEffect(selectedExerciseName) {
        val previousSet = todaySets.lastOrNull { it.exercise.equals(selectedExerciseName, ignoreCase = true) }
            ?: allWorkoutSets.lastOrNull { it.exercise.equals(selectedExerciseName, ignoreCase = true) }
        if (previousSet != null) {
            weightInput = if (previousSet.weightKg % 1.0 == 0.0) previousSet.weightKg.toInt().toString() else previousSet.weightKg.toString()
            repsInput = previousSet.reps.toString()
            weightTypeInput = previousSet.weightType
            rirInput = previousSet.rir ?: 2
        } else {
            val prescribed = activeExercises.find { it.exerciseName.equals(selectedExerciseName, ignoreCase = true) }
            if (prescribed != null) {
                repsInput = prescribed.targetRepsMin.toString()
            }
        }
    }

    fun adjustWeight(delta: Double) {
        val current = weightInput.toDoubleOrNull() ?: 0.0
        val next = (current + delta).coerceAtLeast(0.0)
        weightInput = if (next % 1.0 == 0.0) next.toInt().toString() else String.format(java.util.Locale.US, "%.1f", next)
    }

    fun adjustReps(delta: Int) {
        val current = repsInput.toIntOrNull() ?: 10
        val next = (current + delta).coerceAtLeast(1)
        repsInput = next.toString()
    }

    val totalVolumeKg = remember(todaySets) {
        todaySets.sumOf { WorkoutCalculations.calculateSetVolume(it.reps, it.weightKg, it.weightType) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workout Session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Configure weight & reps before sets, track rest & mark exercises completed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onBrowseSchedule) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Schedule")
                    }
                    TextButton(onClick = onBrowsePlans) {
                        Icon(Icons.Default.Assignment, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Plans")
                    }
                }
            }
        }

        // Live Workout Timers HUD Card (Workout Duration & Rest Timer)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Total Workout Timer
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Workout Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                WorkoutScheduleDefaults.formatDuration(vm.workoutDurationSeconds),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!vm.isWorkoutTimerRunning) {
                                Button(onClick = { vm.startWorkoutTimer() }) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Workout")
                                }
                            } else {
                                FilledTonalButton(onClick = { vm.pauseWorkoutTimer() }) {
                                    Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause")
                                }
                                Button(
                                    onClick = { vm.finishWorkoutSession() },
                                    colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Success)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Finish")
                                }
                            }
                        }
                    }

                    // Rest Countdown Timer HUD
                    if (vm.isRestTimerRunning || vm.restSecondsRemaining > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        Surface(
                            color = if (vm.restSecondsRemaining > 0) MaterialTheme.colorScheme.primaryContainer else FitForgeThemeColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.HourglassTop, null, modifier = Modifier.size(18.dp))
                                        Text(
                                            if (vm.restSecondsRemaining > 0) "Rest: ${WorkoutScheduleDefaults.formatDuration(vm.restSecondsRemaining)} remaining" else "Rest Finished! Ready for next set",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { vm.addRestSeconds(30) }) { Text("+30s") }
                                        TextButton(onClick = { vm.skipRestCountdown() }) { Text("Skip") }
                                    }
                                }

                                if (vm.restSecondsRemaining > 0) {
                                    val total = vm.restTotalTargetSeconds.coerceAtLeast(1).toFloat()
                                    LinearProgressIndicator(
                                        progress = { (vm.restSecondsRemaining.toFloat() / total).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Routine Exercise Progression Strip
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.activeRoutine?.name ?: "Workout Exercise Sequence",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val compCount = completedExercises.size
                            val totCount = activeExercises.size
                            Text(
                                if (totCount > 0) "$compCount of $totCount exercises completed" else "Follow prescribed order or add custom exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { showExercisePicker = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Ex")
                        }
                    }

                    if (activeExercises.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { (completedExercises.size.toFloat() / activeExercises.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = FitForgeThemeColors.Primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(activeExercises) { idx, ex ->
                                val isSelected = idx == currentExerciseIndex || ex.exerciseName.equals(selectedExerciseName, ignoreCase = true)
                                val isDone = completedExercises.contains(ex.exerciseName)
                                val exSetsCount = todaySets.count { it.exercise.equals(ex.exerciseName, ignoreCase = true) }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        vm.selectActiveExercise(idx)
                                        selectedExerciseName = ex.exerciseName
                                    },
                                    leadingIcon = {
                                        if (isDone) {
                                            Icon(Icons.Default.CheckCircle, "Completed", tint = FitForgeThemeColors.Success, modifier = Modifier.size(16.dp))
                                        } else if (isSelected) {
                                            Icon(Icons.Default.FitnessCenter, "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "${idx + 1}. ${ex.exerciseName}${if (exSetsCount > 0) " ($exSetsCount/${ex.targetSets})" else ""}",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDone) FitForgeThemeColors.Success else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isDone) FitForgeThemeColors.Success.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                        containerColor = if (isDone) FitForgeThemeColors.Success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Set Prep & Weight Logger Console (Before Set & Mark Done)
        item {
            val currentPrescribed = activeExercises.find { it.exerciseName.equals(selectedExerciseName, ignoreCase = true) }
            val setsForCurrentExercise = todaySets.filter { it.exercise.equals(selectedExerciseName, ignoreCase = true) }
            val nextSetNumber = setsForCurrentExercise.size + 1
            val isCurrentCompleted = completedExercises.contains(selectedExerciseName)

            val parsedReps = repsInput.toIntOrNull() ?: 0
            val parsedWeight = if (weightTypeInput == "bodyweight") 0.0 else (weightInput.toDoubleOrNull() ?: 0.0)
            val previewVol = WorkoutCalculations.calculateSetVolume(parsedReps, parsedWeight, weightTypeInput)
            val previewE1RM = WorkoutCalculations.calculateE1RM(parsedWeight, parsedReps)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Exercise Title & Status Header
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    selectedExerciseName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { showExercisePicker = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Change Exercise", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (isCurrentCompleted) {
                                Text("✓ Exercise marked completed", color = FitForgeThemeColors.Success, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            } else if (currentPrescribed != null) {
                                Text(
                                    "Target: ${currentPrescribed.targetSets} sets • ${currentPrescribed.targetRepsMin}-${currentPrescribed.targetRepsMax} reps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Set $nextSetNumber for this exercise",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isCurrentCompleted) {
                            FilterChip(
                                selected = true,
                                onClick = { vm.unmarkExerciseCompleted(selectedExerciseName) },
                                leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = FitForgeThemeColors.Success) },
                                label = { Text("Done (Undo)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                    Text(
                        "SET PREPARATION & WEIGHT LOGGER (Before Set $nextSetNumber)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    // Weight Measurement Type Chips
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "barbell" to "Barbell",
                            "dumbbell" to "Dumbbell (each)",
                            "bodyweight" to "Bodyweight",
                            "machine" to "Machine"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = weightTypeInput == type,
                                onClick = { weightTypeInput = type },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // Weight Stepper & Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (weightTypeInput == "dumbbell") "Weight per Dumbbell (kg):" else "Weight (kg):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { adjustWeight(-5.0) },
                                enabled = weightTypeInput != "bodyweight",
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("-5") }

                            FilledTonalButton(
                                onClick = { adjustWeight(-2.5) },
                                enabled = weightTypeInput != "bodyweight",
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("-2.5") }

                            OutlinedTextField(
                                value = if (weightTypeInput == "bodyweight") "0" else weightInput,
                                onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } },
                                enabled = weightTypeInput != "bodyweight",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                            )

                            FilledTonalButton(
                                onClick = { adjustWeight(2.5) },
                                enabled = weightTypeInput != "bodyweight",
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("+2.5") }

                            FilledTonalButton(
                                onClick = { adjustWeight(5.0) },
                                enabled = weightTypeInput != "bodyweight",
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("+5") }
                        }
                    }

                    // Reps Stepper & Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Target Reps:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { adjustReps(-1) },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) { Text("-1") }

                            OutlinedTextField(
                                value = repsInput,
                                onValueChange = { repsInput = it.filter { c -> c.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                            )

                            FilledTonalButton(
                                onClick = { adjustReps(1) },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) { Text("+1") }
                        }
                    }

                    // Effort / RIR (Reps in Reserve)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Effort / RIR:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0 to "Failure", 1 to "1 RIR", 2 to "2 RIR", 3 to "3+ RIR").forEach { (r, label) ->
                                FilterChip(
                                    selected = rirInput == r,
                                    onClick = { rirInput = r },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Calculations preview
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Set Vol: ${previewVol.toInt()} kg",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Est. 1RM: ${if (previewE1RM > 0) "${previewE1RM.toInt()} kg" else "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFB74D)
                            )
                        }
                    }

                    // Active Set Execution Controls
                    if (!vm.isSetTimerRunning) {
                        Button(
                            onClick = {
                                if (!vm.isWorkoutTimerRunning) {
                                    vm.startWorkoutTimer()
                                }
                                vm.startSetTimer()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            val loadLabel = if (weightTypeInput == "bodyweight") "Bodyweight" else "$weightInput kg"
                            Text("START SET $nextSetNumber ($loadLabel × $repsInput reps)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            color = FitForgeThemeColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = FitForgeThemeColors.Secondary
                                        )
                                        Text("Set In Progress: Set $nextSetNumber", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Secondary)
                                    }
                                    Text(
                                        "${vm.activeSetDurationSeconds}s",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = FitForgeThemeColors.Secondary
                                    )
                                }

                                Text(
                                    "Lifting: ${if (weightTypeInput == "bodyweight") "Bodyweight" else "$weightInput kg"} × $repsInput reps (adjust if different)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FitForgeThemeColors.TextSecondary
                                )

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { vm.resetSetTimer() },
                                        modifier = Modifier.weight(0.35f)
                                    ) {
                                        Text("Cancel")
                                    }

                                    Button(
                                        onClick = {
                                            val sec = vm.stopSetTimer()
                                            val reps = repsInput.toIntOrNull() ?: 10
                                            val weight = if (weightTypeInput == "bodyweight") 0.0 else (weightInput.toDoubleOrNull() ?: 0.0)
                                            if (activeExercises.none { it.exerciseName.equals(selectedExerciseName, ignoreCase = true) }) {
                                                vm.addExerciseToActiveSession(selectedExerciseName)
                                            }
                                            vm.addWorkoutSet(
                                                exercise = selectedExerciseName,
                                                reps = reps,
                                                weightKg = weight,
                                                weightType = weightTypeInput,
                                                rir = rirInput,
                                                durationSeconds = sec
                                            )
                                            if (startRestCheckbox) {
                                                vm.startRestCountdown(90)
                                            }
                                            vm.resetSetTimer()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Success),
                                        modifier = Modifier.weight(0.65f)
                                    ) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("COMPLETE SET & REST", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // MARK EXERCISE COMPLETED & NEXT EXERCISE
                    val isDone = completedExercises.contains(selectedExerciseName)
                    val nextExIndex = activeExercises.indexOfFirst { !completedExercises.contains(it.exerciseName) && it.exerciseName != selectedExerciseName }
                    val nextExerciseName = if (nextExIndex != -1) activeExercises[nextExIndex].exerciseName else null

                    FilledTonalButton(
                        onClick = {
                            if (!isDone) {
                                vm.markExerciseCompleted(selectedExerciseName, restBetweenExercisesSeconds = 120)
                                if (nextExerciseName != null) {
                                    selectedExerciseName = nextExerciseName
                                }
                            } else {
                                if (nextExerciseName != null) {
                                    val targetIdx = activeExercises.indexOfFirst { it.exerciseName == nextExerciseName }
                                    if (targetIdx != -1) {
                                        vm.selectActiveExercise(targetIdx)
                                        selectedExerciseName = nextExerciseName
                                    }
                                } else if (activeExercises.isNotEmpty() && currentExerciseIndex < activeExercises.size - 1) {
                                    vm.selectActiveExercise(currentExerciseIndex + 1)
                                    selectedExerciseName = activeExercises[currentExerciseIndex + 1].exerciseName
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDone) MaterialTheme.colorScheme.secondaryContainer else FitForgeThemeColors.PrimaryContainer,
                            contentColor = if (isDone) MaterialTheme.colorScheme.onSecondaryContainer else FitForgeThemeColors.OnPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(
                            if (isDone) Icons.Default.ArrowForward else Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (!isDone) {
                            if (nextExerciseName != null) {
                                Text("✓ Mark Exercise Done & Next: $nextExerciseName →", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("✓ Mark Exercise Done & Finish Routine", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            if (nextExerciseName != null) {
                                Text("Move to Next: $nextExerciseName →", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("All Exercises Completed ✓", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Workout Session Header Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        label = { Text("Session Name / Focus") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                            label = { Text("Duration (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = distanceText,
                            onValueChange = { distanceText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Distance (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Facts vs Estimates Banner: MET Calorie Expenditure
                    Surface(
                        color = FitForgeThemeColors.SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Estimated Energy Expenditure", color = FitForgeThemeColors.Secondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Text("MET 5.5 estimate: (MET × 3.5 × weight) / 200 × $durationMin min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "≈ ${estimatedBurn.toInt()} kcal (est.)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = FitForgeThemeColors.Secondary
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Session Completed", fontWeight = FontWeight.Medium)
                        Switch(checked = completed, onCheckedChange = { completed = it })
                    }

                    Button(
                        onClick = {
                            vm.startOrUpdateWorkoutSession(
                                dayName = dayName,
                                durationMinutes = durationMin,
                                distanceKm = distanceText.toDoubleOrNull() ?: 0.0,
                                estimatedKcal = estimatedBurn,
                                completed = completed,
                                notes = notesText
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Workout Session")
                    }
                }
            }
        }

        // Training Volume & Sets Summary Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${todaySets.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Sets Logged", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${totalVolumeKg.toInt()} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = FitForgeThemeColors.Protein)
                        Text("Training Volume", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val maxE1RM = todaySets.maxOfOrNull { WorkoutCalculations.calculateE1RM(it.weightKg, it.reps) } ?: 0.0
                        Text(if (maxE1RM > 0) "${maxE1RM.toInt()} kg" else "—", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = FitForgeThemeColors.Fat)
                        Text("Peak e1RM (est.)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Strength Training Sets Tracker Header
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Logged Exercises & Sets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    durationSecInput = if (vm.activeSetDurationSeconds > 0) vm.activeSetDurationSeconds.toString() else "30"
                    showAddSetDialog = true
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Log Set")
                }
            }
        }

        if (todaySets.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No sets recorded today", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap 'Log Set' or pick a workout from the 'Workout Plans' tab to follow pre-set targets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Sets grouped by exercise with Progressive Overload Tips & PR Badges
        val groupedSets = todaySets.groupBy { it.exercise }
        groupedSets.forEach { (exerciseName, sets) ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(exerciseName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            val exVolume = sets.sumOf { WorkoutCalculations.calculateSetVolume(it.reps, it.weightKg, it.weightType) }
                            Text("Vol: ${exVolume.toInt()} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Progressive overload tip based on RIR and reps
                        val suggestion = remember(sets) { WorkoutCalculations.suggestProgression(exerciseName, sets) }
                        if (suggestion != null) {
                            Surface(
                                color = FitForgeThemeColors.SurfaceElevated,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.TrendingUp, null, tint = FitForgeThemeColors.Primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        suggestion.rationale,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FitForgeThemeColors.Primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                        sets.forEach { s ->
                            val prs = remember(s, allWorkoutSets) { WorkoutCalculations.checkPersonalRecords(s, allWorkoutSets) }
                            val setVol = WorkoutCalculations.calculateSetVolume(s.reps, s.weightKg, s.weightType)
                            val e1rm = WorkoutCalculations.formatE1RM(s.weightKg, s.reps)

                            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Set ${s.setNumber}:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            val loadText = if (s.weightType == "bodyweight") "Bodyweight × ${s.reps} reps" else "${s.weightKg} kg (${s.weightType}) × ${s.reps} reps"
                                            Text(loadText, style = MaterialTheme.typography.bodyMedium)
                                            if (s.durationSeconds > 0) {
                                                Surface(
                                                    color = Color(0xFFFFB74D).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("⏱️ ${s.durationSeconds}s", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB74D), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                            if (s.rir != null) {
                                                Surface(
                                                    color = if (s.rir == 0) Color(0xFFEF5350).copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        if (s.rir == 0) "Failure" else "RIR ${s.rir}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (s.rir == 0) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (setVol > 0) {
                                                Text("Vol: ${setVol.toInt()} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (s.weightKg > 0) {
                                                Text("e1RM: $e1rm", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB74D))
                                            }
                                        }
                                    }

                                    IconButton(onClick = { vm.deleteWorkoutSet(s.id) }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove Set", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                if (prs.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        prs.forEach { pr ->
                                            Surface(
                                                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    pr.badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFFFB300),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Set Bottom Sheet
    if (showAddSetDialog) {
        val addSetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showAddSetDialog = false },
            sheetState = addSetSheetState,
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
                    Text("Log Exercise Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showAddSetDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = selectedExerciseName,
                    onValueChange = { selectedExerciseName = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Weight Measurement Type Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Weight Measurement:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "barbell" to "Barbell (Total)",
                            "dumbbell" to "Dumbbell (Per DB)",
                            "bodyweight" to "Bodyweight",
                            "machine" to "Machine / Stack"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = weightTypeInput == type,
                                onClick = { weightTypeInput = type },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (weightTypeInput == "bodyweight") "0" else weightInput,
                        onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(if (weightTypeInput == "dumbbell") "Weight per DB (kg)" else "Weight (kg)") },
                        enabled = weightTypeInput != "bodyweight",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = { repsInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Set Duration (seconds)
                OutlinedTextField(
                    value = durationSecInput,
                    onValueChange = { durationSecInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Set Duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // RIR (Reps in Reserve) Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reps in Reserve (RIR / Effort):", style = MaterialTheme.typography.labelMedium)
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

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start 90s Rest Countdown", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = startRestCheckbox, onCheckedChange = { startRestCheckbox = it })
                }

                // Live calculation preview
                val parsedReps = repsInput.toIntOrNull() ?: 0
                val parsedWeight = if (weightTypeInput == "bodyweight") 0.0 else (weightInput.toDoubleOrNull() ?: 0.0)
                val previewVol = WorkoutCalculations.calculateSetVolume(parsedReps, parsedWeight, weightTypeInput)
                val previewE1RM = WorkoutCalculations.calculateE1RM(parsedWeight, parsedReps)

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${previewVol.toInt()} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(if (weightTypeInput == "dumbbell") "Vol (2x DB)" else "Set Volume", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (previewE1RM > 0) "${previewE1RM.toInt()} kg" else "—", fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                            Text("Est. 1RM", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { showAddSetDialog = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val reps = repsInput.toIntOrNull() ?: 10
                            val weight = if (weightTypeInput == "bodyweight") 0.0 else (weightInput.toDoubleOrNull() ?: 0.0)
                            val dur = durationSecInput.toIntOrNull() ?: 0
                            if (activeExercises.none { it.exerciseName.equals(selectedExerciseName, ignoreCase = true) }) {
                                vm.addExerciseToActiveSession(selectedExerciseName)
                            }
                            vm.addWorkoutSet(
                                exercise = selectedExerciseName,
                                reps = reps,
                                weightKg = weight,
                                weightType = weightTypeInput,
                                rir = rirInput,
                                durationSeconds = dur
                            )
                            if (startRestCheckbox) {
                                vm.startRestCountdown(90)
                            }
                            vm.resetSetTimer()
                            showAddSetDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Set")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showExercisePicker) {
        ExercisePickerBottomSheet(
            allExercises = exercises,
            onDismiss = { showExercisePicker = false },
            onSelect = { selectedEx ->
                if (activeExercises.none { it.exerciseName.equals(selectedEx.name, ignoreCase = true) }) {
                    vm.addExerciseToActiveSession(selectedEx.name)
                }
                selectedExerciseName = selectedEx.name
                val targetIdx = activeExercises.indexOfFirst { it.exerciseName.equals(selectedEx.name, ignoreCase = true) }
                if (targetIdx != -1) {
                    vm.selectActiveExercise(targetIdx)
                }
                showExercisePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutRoutinesScreen(
    vm: FitForgeViewModel,
    onStartRoutine: () -> Unit,
    onAssignRoutine: () -> Unit = {}
) {
    val routines by vm.workoutRoutines.collectAsState()
    var routineExercisesMap by remember { mutableStateOf<Map<String, List<RoutineExercise>>>(emptyMap()) }
    var showWgerModal by remember { mutableStateOf(false) }
    var showCreateRoutineDialog by remember { mutableStateOf(false) }
    var routineToAssign by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var routineToDelete by remember { mutableStateOf<WorkoutRoutine?>(null) }

    LaunchedEffect(routines) {
        val map = mutableMapOf<String, List<RoutineExercise>>()
        routines.forEach { r ->
            map[r.id] = vm.getRoutineExercises(r.id)
        }
        routineExercisesMap = map
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Workout Plans & Routines", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Pre-built splits, custom weekly plans, and wger community routines", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { showCreateRoutineDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Custom Plan")
                    }
                    Button(
                        onClick = {
                            vm.fetchWgerRoutines()
                            showWgerModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("wger")
                    }
                }
            }
        }

        if (routines.isEmpty()) {
            item {
                Text("No pre-set routines found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(routines) { routine ->
            val exercises = routineExercisesMap[routine.id] ?: emptyList()
            var isExpanded by remember { mutableStateOf(false) }
            val isCustom = routine.id.startsWith("custom_routine_")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        routine.splitCategory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isCustom) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "My Plan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(routine.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(routine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { routineToAssign = routine }) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Schedule")
                            }
                            Button(onClick = {
                                vm.loadRoutineIntoSession(routine)
                                vm.startWorkoutTimer()
                                onStartRoutine()
                            }) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Start")
                            }
                            IconButton(onClick = { routineToDelete = routine }) {
                                Icon(Icons.Default.Delete, "Delete routine", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    TextButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isExpanded) "Hide Prescribed Exercises ▲" else "View ${exercises.size} Prescribed Exercises ▼")
                    }

                    if (isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            exercises.forEach { ex ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${ex.orderIndex}. ${ex.exerciseName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            if (ex.notes.isNotBlank()) {
                                                Text(ex.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(
                                            "${ex.targetSets} sets × ${ex.targetRepsMin}-${ex.targetRepsMax} reps",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Routine Confirmation Popup (Permitted as Confirmation Dialog)
    if (routineToDelete != null) {
        val target = routineToDelete!!
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete Workout Plan?") },
            text = {
                Text("Are you sure you want to delete '${target.name}'? This will permanently remove the plan and all its prescribed exercises.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteRoutine(target.id)
                        routineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { routineToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Routine Builder Bottom Sheet
    if (showCreateRoutineDialog) {
        CreateCustomRoutineBottomSheet(vm = vm, onDismiss = { showCreateRoutineDialog = false })
    }

    // Quick Assign to Weekly Schedule Bottom Sheet
    if (routineToAssign != null) {
        val targetRoutine = routineToAssign!!
        val days = listOf(
            1 to "Monday",
            2 to "Tuesday",
            3 to "Wednesday",
            4 to "Thursday",
            5 to "Friday",
            6 to "Saturday",
            7 to "Sunday"
        )
        val assignSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { routineToAssign = null },
            sheetState = assignSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Assign '${targetRoutine.name}' to Day", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { routineToAssign = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Text("Select which day of your weekly schedule to perform this routine:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                days.forEach { (dayNum, dayName) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.updateDaySchedule(
                                dayOfWeek = dayNum,
                                dayName = dayName,
                                routineId = targetRoutine.id,
                                routineName = targetRoutine.name,
                                isRestDay = false,
                                targetDurationMinutes = 45,
                                defaultRestSeconds = 90
                            )
                            routineToAssign = null
                            onAssignRoutine()
                        }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dayName, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showWgerModal) {
        var wgerKeyInput by remember { mutableStateOf(vm.wgerApiKey) }
        var importSuccessMsg by remember { mutableStateOf<String?>(null) }
        val wgerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

        ModalBottomSheet(
            onDismissRequest = { showWgerModal = false },
            sheetState = wgerSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("Import wger Routines", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { showWgerModal = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Sync and import workout plans from wger.de into FitForge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = wgerKeyInput,
                    onValueChange = { wgerKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("wger API Key / Token (Optional)") },
                    placeholder = { Text("Leave blank for community routines") },
                    singleLine = true,
                    trailingIcon = {
                        if (wgerKeyInput.isNotBlank()) {
                            IconButton(onClick = {
                                vm.updateWgerApiKey(wgerKeyInput)
                                vm.fetchWgerRoutines()
                            }) {
                                Icon(Icons.Default.Check, "Save Key")
                            }
                        }
                    }
                )

                if (importSuccessMsg != null) {
                    Surface(
                        color = FitForgeThemeColors.Success.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            importSuccessMsg!!,
                            color = FitForgeThemeColors.Success,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (vm.isFetchingWgerRoutines) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    Text("Available wger Routines (${vm.wgerRoutines.size}):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    vm.wgerRoutines.forEach { bundle ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(bundle.routine.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(bundle.routine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (bundle.exercises.isNotEmpty()) {
                                        Text("${bundle.exercises.size} prescribed exercises", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        vm.importWgerRoutine(bundle) {
                                            importSuccessMsg = "Imported '${bundle.routine.name}' into your plans!"
                                        }
                                    }
                                ) {
                                    Text("Import")
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = { showWgerModal = false }) { Text("Close") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ExerciseCatalogueScreen(
    vm: FitForgeViewModel,
    onLogExercise: (String) -> Unit
) {
    val exercises by vm.exercises.collectAsState()
    var searchExercise by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf("All") }
    var catalogueTab by remember { mutableStateOf(0) } // 0: Offline DB (876), 1: wger Online
    var exerciseForLogModal by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val muscleGroups = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio")

    val filteredOfflineExercises = remember(exercises, searchExercise, selectedMuscle) {
        exercises.filter { ex ->
            val matchMuscle = selectedMuscle == "All" || ex.muscleGroup.equals(selectedMuscle, ignoreCase = true)
            val matchQuery = searchExercise.isBlank() || ex.name.contains(searchExercise, ignoreCase = true) || ex.equipment.contains(searchExercise, ignoreCase = true)
            matchMuscle && matchQuery
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Exercise Catalogue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Complete 800+ exercise library offline, plus live wger.de database search",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Segmented Tab Selector: Offline DB vs wger Online
        item {
            TabRow(selectedTabIndex = catalogueTab) {
                Tab(
                    selected = catalogueTab == 0,
                    onClick = { catalogueTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Storage, null, modifier = Modifier.size(16.dp))
                            Text("Offline Free DB (${exercises.size})")
                        }
                    }
                )
                Tab(
                    selected = catalogueTab == 1,
                    onClick = {
                        catalogueTab = 1
                        if (vm.wgerExercises.isEmpty()) {
                            vm.searchWger(searchExercise, if (selectedMuscle == "All") null else selectedMuscle)
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp))
                            Text("wger Online")
                        }
                    }
                )
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchExercise,
                onValueChange = {
                    searchExercise = it
                    if (catalogueTab == 1) {
                        vm.searchWger(it, if (selectedMuscle == "All") null else selectedMuscle)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (catalogueTab == 0) "Search ${exercises.size} offline exercises..." else "Search wger.de online catalogue...")
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (catalogueTab == 1) {
                        IconButton(onClick = {
                            vm.searchWger(searchExercise, if (selectedMuscle == "All") null else selectedMuscle)
                        }) {
                            Icon(Icons.Default.Refresh, "Search wger")
                        }
                    }
                }
            )
        }

        // Muscle filter chips
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                muscleGroups.forEach { muscle ->
                    FilterChip(
                        selected = selectedMuscle == muscle,
                        onClick = {
                            selectedMuscle = muscle
                            if (catalogueTab == 1) {
                                vm.searchWger(searchExercise, if (muscle == "All") null else muscle)
                            }
                        },
                        label = { Text(muscle) }
                    )
                }
            }
        }

        // TAB 0: OFFLINE DATABASE (876 Exercises)
        if (catalogueTab == 0) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Offline Exercises (${filteredOfflineExercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Free Exercise DB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(filteredOfflineExercises) { ex ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(ex.muscleGroup, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Text("• ${ex.equipment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("• ${ex.measurementType.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    Text("• MET ${ex.metValue}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF8A65))
                                }
                            }
                            IconButton(onClick = {
                                exerciseForLogModal = Triple(ex.name, ex.muscleGroup, ex.equipment)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add to workout", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Instructions:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Text(ex.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ex.secondaryMuscles.isNotBlank()) {
                                Text("Secondary muscles: ${ex.secondaryMuscles}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 1: WGER ONLINE
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "wger Online Results (${vm.wgerExercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "wger.de API v2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (vm.isSearchingWger) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Querying wger online database...")
                    }
                }
            } else if (vm.wgerError != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("wger Connection Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(vm.wgerError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                vm.searchWger(searchExercise, if (selectedMuscle == "All") null else selectedMuscle)
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            } else if (vm.wgerExercises.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No exercises returned from wger for this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            vm.searchWger(searchExercise, if (selectedMuscle == "All") null else selectedMuscle)
                        }) {
                            Text("Fetch wger Exercises")
                        }
                    }
                }
            }

            items(vm.wgerExercises) { wgerEx ->
                var expanded by remember { mutableStateOf(false) }
                var isImported by remember { mutableStateOf(exercises.any { it.name.equals(wgerEx.name, ignoreCase = true) }) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(wgerEx.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("wger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(wgerEx.muscleGroup, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Text("• ${wgerEx.equipment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        vm.importWgerExercise(wgerEx) {
                                            isImported = true
                                        }
                                    },
                                    enabled = !isImported
                                ) {
                                    Icon(
                                        if (isImported) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                        contentDescription = "Save to library",
                                        tint = if (isImported) FitForgeThemeColors.Success else MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = {
                                    exerciseForLogModal = Triple(wgerEx.name, wgerEx.muscleGroup, wgerEx.equipment)
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add to workout", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Instructions:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Text(wgerEx.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (wgerEx.secondaryMuscles.isNotBlank()) {
                                Text("Secondary muscles: ${wgerEx.secondaryMuscles}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (exerciseForLogModal != null) {
        val targetEx = exerciseForLogModal!!
        LogSetFromCatalogueBottomSheet(
            exerciseName = targetEx.first,
            muscleGroup = targetEx.second,
            equipment = targetEx.third,
            initialDurationSeconds = vm.activeSetDurationSeconds,
            onDismiss = { exerciseForLogModal = null },
            onConfirm = { weightKg, reps, weightType, rir, durationSeconds, startRestTimer ->
                vm.addWorkoutSet(
                    exercise = targetEx.first,
                    reps = reps,
                    weightKg = weightKg,
                    weightType = weightType,
                    rir = rir,
                    durationSeconds = durationSeconds
                )
                if (startRestTimer) {
                    vm.startRestCountdown(90)
                }
                vm.resetSetTimer()
                exerciseForLogModal = null
                onLogExercise(targetEx.first)
            }
        )
    }
}


