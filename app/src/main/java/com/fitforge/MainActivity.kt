package com.fitforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitforge.data.FitForgeDatabase

class MainActivity : ComponentActivity() {
    private val initialCoachOpen = mutableStateOf(false)
    private val initialCoachEngine = mutableStateOf("CHATGPT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        val db = FitForgeDatabase.get(applicationContext)
        val vmFactory = FitForgeViewModel.Factory(db.dao(), applicationContext)
        setContent {
            val vm: FitForgeViewModel = viewModel(factory = vmFactory)
            MaterialTheme(colorScheme = ModernDarkColorScheme) {
                FitForgeApp(
                    vm = vm,
                    initialCoachOpen = initialCoachOpen.value,
                    initialCoachEngine = initialCoachEngine.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("extra_open_coach", false) == true) {
            initialCoachOpen.value = true
            initialCoachEngine.value = intent.getStringExtra("extra_coach_engine") ?: "CHATGPT"
        }
    }
}

private enum class Tab(val label: String) {
    Today("Today"),
    Workout("Workouts"),
    Nutrition("Nutrition"),
    Progress("Progress")
}

@Composable
private fun FitForgeApp(
    vm: FitForgeViewModel,
    initialCoachOpen: Boolean = false,
    initialCoachEngine: String = "CHATGPT"
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Today) }
    var isCoachOpen by remember(initialCoachOpen) { mutableStateOf(initialCoachOpen) }
    var coachEngine by remember(initialCoachEngine) { mutableStateOf(initialCoachEngine) }

    // Request notification permission on Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = isCoachOpen) {
        isCoachOpen = false
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                listOf(
                    Tab.Today to Icons.Default.Home,
                    Tab.Workout to Icons.Default.FitnessCenter,
                    Tab.Nutrition to Icons.Default.Restaurant,
                    Tab.Progress to Icons.Default.BarChart
                ).forEach { (item, icon) ->
                    val isSelected = (tab == item) && !isCoachOpen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            tab = item
                            isCoachOpen = false
                        },
                        icon = {
                            Icon(
                                icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isCoachOpen) {
                CoachScreen(
                    vm = vm,
                    onBack = { isCoachOpen = false },
                    initialEngine = coachEngine
                )
            } else {
                when (tab) {
                    Tab.Today -> DashboardScreen(
                        vm = vm,
                        onFood = { tab = Tab.Nutrition },
                        onWorkout = { tab = Tab.Workout },
                        onCoach = { isCoachOpen = true },
                        onProgress = { tab = Tab.Progress }
                    )
                    Tab.Workout -> WorkoutModuleScreen(vm)
                    Tab.Nutrition -> FoodMealsScreen(vm)
                    Tab.Progress -> ProgressScreen(vm)
                }
            }
        }
    }
}
