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
// 5. TRENDS & PROGRESS HISTORY SCREEN
// -------------------------------------------------------------
@Composable
fun TrendsScreen(vm: FitForgeViewModel) {
    val entries by vm.bodyEntries.collectAsState()
    val recentLogs by vm.recentLogs.collectAsState()
    val sessions by vm.workoutSessions.collectAsState()
    val chronological = entries.sortedBy { it.date }.takeLast(14)

    // Aggregate daily intake averages
    val logsByDate = recentLogs.groupBy { it.date }
    val avgKcal = if (logsByDate.isNotEmpty()) logsByDate.values.map { list -> list.sumOf { it.kcal } }.average() else 0.0
    val avgProtein = if (logsByDate.isNotEmpty()) logsByDate.values.map { list -> list.sumOf { it.protein } }.average() else 0.0
    val avgCarbs = if (logsByDate.isNotEmpty()) logsByDate.values.map { list -> list.sumOf { it.carbs } }.average() else 0.0
    val avgFat = if (logsByDate.isNotEmpty()) logsByDate.values.map { list -> list.sumOf { it.fat } }.average() else 0.0

    val completedWorkouts = sessions.count { it.completed }
    val totalBurn = sessions.sumOf { it.estimatedCaloriesBurned }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Progress History & Trends", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Weekly averages filter out day-to-day water weight and metabolic noise", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Weight Trend Chart
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Body Weight Trend (Last 14 measurements)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (chronological.size >= 2) {
                        WeightChart(chronological.map { it.weightKg })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Start: ${chronological.first().weightKg} kg")
                            Text("Current: ${chronological.last().weightKg} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Text("Add at least two weight entries in the Body tab to see your trend curve.")
                    }
                }
            }
        }

        // 14-Day Nutrition Intake Averages
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("14-Day Average Nutrition Intake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${avgKcal.toInt()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("avg kcal", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${avgProtein.toInt()}g", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = FitForgeThemeColors.Protein)
                            Text("avg protein", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${avgCarbs.toInt()}g", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = FitForgeThemeColors.Carbs)
                            Text("avg carbs", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${avgFat.toInt()}g", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = FitForgeThemeColors.Fat)
                            Text("avg fat", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Workout Volume & Burn History
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Training Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Completed Sessions:")
                        Text("$completedWorkouts sessions", fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Estimated Energy Burned:")
                        Text("${totalBurn.toInt()} kcal", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Calories)
                    }
                }
            }
        }

        // Data Management & Backup
        item {
            var isExporting by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        isExporting = true
                        try {
                            val json = vm.exportDatabaseBackup()
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(json.toByteArray(Charsets.UTF_8))
                            }
                            vm.setBackupStatus("Backup exported successfully!")
                        } catch (e: Exception) {
                            vm.setBackupStatus("Export failed: ${e.localizedMessage}")
                        } finally {
                            isExporting = false
                        }
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.bufferedReader(Charsets.UTF_8).readText()
                            }
                            if (!json.isNullOrBlank()) {
                                vm.restoreDatabaseBackup(json)
                            } else {
                                vm.setBackupStatus("Selected file was empty")
                            }
                        } catch (e: Exception) {
                            vm.setBackupStatus("Restore error: ${e.localizedMessage}")
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Data Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Export a full JSON backup of your food logs, recipes, custom workout routines, schedule, and weigh-ins. You can restore it anytime on any device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val filename = "fitforge_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())}.json"
                                exportLauncher.launch(filename)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export", style = MaterialTheme.typography.bodySmall)
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    vm.backupStatusMessage?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (status.contains("failed", true) || status.contains("error", true)) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (status.contains("failed", true) || status.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Coach's Rule: Judge progress by weekly averages. Keep deficit moderate and protein high.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WeightChart(values: List<Double>) {
    val min = (values.minOrNull() ?: 0.0) - 1.0
    val max = (values.maxOrNull() ?: 1.0) + 1.0
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
        val left = 12f
        val right = size.width - 12f
        val top = 12f
        val bottom = size.height - 12f
        repeat(4) { i ->
            val y = top + (bottom - top) * i / 3f
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(left, y), Offset(right, y), 1f)
        }
        val points = values.mapIndexed { index, v ->
            val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
            val y = bottom - ((v - min) / (max - min)).toFloat() * (bottom - top)
            Offset(x, y)
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(FitForgeThemeColors.Primary, a, b, 4f) }
        points.forEach { drawCircle(FitForgeThemeColors.Primary, 5f, it) }
    }
}


