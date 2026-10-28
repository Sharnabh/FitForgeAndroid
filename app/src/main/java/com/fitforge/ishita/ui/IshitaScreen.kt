package com.fitforge.ishita.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Gavel
import com.fitforge.FitForgeThemeColors
import com.fitforge.FitForgeViewModel
import com.fitforge.MarkdownText
import com.fitforge.data.chatgpt.CoachMessage
import com.fitforge.ishita.data.AccountabilityBlockManager
import com.fitforge.ishita.data.AccountabilityContextDto
import com.fitforge.ishita.data.AccountabilityPenalty
import com.fitforge.ishita.data.ActiveBlock
import com.fitforge.ishita.data.AppUsageDto
import com.fitforge.ishita.data.AppUsageStat
import com.fitforge.ishita.data.IshitaChatManager
import com.fitforge.ishita.data.IshitaEvent
import com.fitforge.ishita.data.OnDeviceIshitaClient
import com.fitforge.ishita.data.OnDeviceModelManager
import com.fitforge.ishita.data.PenaltyStatus
import com.fitforge.ishita.data.ProposedAction
import com.fitforge.ishita.data.UsageStatsRepository
import com.fitforge.ishita.data.WebsiteUsageDto
import com.fitforge.ishita.data.WebsiteUsageStat
import com.fitforge.ishita.service.FitForgeAccountabilityAccessibilityService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

@Composable
fun IshitaScreen(
    vm: FitForgeViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember { OnDeviceModelManager(context) }
    val client = remember { OnDeviceIshitaClient.get(context) }
    val usageRepo = remember { UsageStatsRepository(context) }
    val blockManager = remember { AccountabilityBlockManager.get(context) }

    var isModelDownloaded by remember { mutableStateOf(modelManager.isModelDownloaded()) }
    var isModelLoading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPenaltiesScreen by remember { mutableStateOf(false) }

    // Screen & web time
    var hasUsagePermission by remember { mutableStateOf(usageRepo.hasUsagePermission()) }
    var hasAccessibilityPermission by remember { mutableStateOf(FitForgeAccountabilityAccessibilityService.isAccessibilityServiceEnabled(context)) }
    var appUsageStats by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var webUsageStats by remember { mutableStateOf<List<WebsiteUsageStat>>(emptyList()) }
    val activeBlocks by blockManager.activeBlocksFlow.collectAsState()

    // Persistent Ishita chat manager & background execution
    val chatManager = remember { IshitaChatManager.get(context) }
    val messages by chatManager.messagesFlow.collectAsState()
    val isStreaming by chatManager.isStreamingFlow.collectAsState()
    val streamingStatus by chatManager.streamingStatusFlow.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        chatManager.isScreenVisible = true
        onDispose {
            chatManager.isScreenVisible = false
        }
    }

    // Auto-scroll as messages update or text streams in
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Refresh context data
    fun refreshStats() {
        hasUsagePermission = usageRepo.hasUsagePermission()
        hasAccessibilityPermission = FitForgeAccountabilityAccessibilityService.isAccessibilityServiceEnabled(context)
        scope.launch {
            if (hasUsagePermission) {
                appUsageStats = usageRepo.getDailyUsageStats(hoursBack = 24, maxResults = 5)
            }
            webUsageStats = blockManager.getDailyWebsiteUsage(hoursBack = 24, maxResults = 5)
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    // Auto refresh when returning from Android Settings (e.g. after granting usage or accessibility)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Document picker to import GGUF model directly from device storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isDownloading = true
            downloadStatusText = "Importing local GGUF file..."
            scope.launch {
                val res = modelManager.importModelFromUri(uri) { copied, _ ->
                    val mb = copied / (1024.0 * 1024.0)
                    downloadStatusText = String.format("Importing: %.1f MB copied", mb)
                }
                isDownloading = false
                if (res.isSuccess) {
                    isModelDownloaded = true
                    Toast.makeText(context, "Gemmasutra Mini 2B imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun sendMessage(textToSend: String) {
        if (textToSend.isBlank() || isStreaming) return
        inputText = ""

        scope.launch {
            // Build current accountability context with rich workout and streak details
            val schedules = vm.weeklySchedule.value
            val sessions = vm.workoutSessions.value
            val todayDate = LocalDate.now()
            val consistency = com.fitforge.data.WorkoutScheduleDefaults.calculateWeeklyConsistency(todayDate, schedules, sessions)
            val todaySession = vm.todaySession.value
            val workoutStatus = if (todaySession?.completed == true) "COMPLETED" else if (todaySession != null) "IN_PROGRESS" else "PENDING"

            val todayDayOfWeek = todayDate.dayOfWeek.value
            val todaySchedule = schedules.firstOrNull { it.dayOfWeek == todayDayOfWeek }
            val scheduledRoutine = vm.workoutRoutines.value.firstOrNull { it.id == todaySchedule?.routineId }

            val lastCompletedSession = sessions.filter { it.completed }.maxByOrNull { it.date }

            val routineId = scheduledRoutine?.id
            val routineExercisesList = mutableListOf<String>()
            if (routineId != null) {
                try {
                    val routineExs = vm.getRoutineExercises(routineId)
                    routineExercisesList.addAll(routineExs.map { "${it.exerciseName} (${it.targetSets}x${it.targetRepsMin}-${it.targetRepsMax})" })
                } catch (_: Exception) {}
            }

            val accContext = AccountabilityContextDto(
                localDate = todayDate.toString(),
                plannedWorkout = scheduledRoutine?.name ?: todaySession?.dayName ?: todaySchedule?.routineName ?: "Rest Day",
                splitCategory = scheduledRoutine?.splitCategory ?: "General",
                targetMuscles = scheduledRoutine?.splitCategory,
                routineExercises = routineExercisesList,
                lastCompletedDate = lastCompletedSession?.date,
                lastCompletedWorkout = lastCompletedSession?.dayName,
                workoutStatus = workoutStatus,
                completedSessionsThisWeek = consistency.completedCount,
                missedSessionsThisWeek = (consistency.scheduledCount - consistency.completedCount).coerceAtLeast(0),
                streakDays = consistency.currentStreakDays,
                topDistractingApps = appUsageStats.map {
                    AppUsageDto(packageName = it.packageName, appName = it.appName, foregroundMinutes = it.foregroundMinutes.toInt())
                },
                topDistractingWebsites = webUsageStats.map {
                    WebsiteUsageDto(domain = it.domain, foregroundMinutes = it.totalMinutes.toInt(), visitCount = it.visitCount)
                }
            )

            chatManager.sendMessage(textToSend, accContext)
        }
    }

    if (showPenaltiesScreen) {
        PenaltiesScreen(onBack = { showPenaltiesScreen = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = FitForgeThemeColors.Secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ishita (Gemmasutra 2B)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        if (isModelDownloaded) "On-Device • Offline • Strict Discipline" else "Model Setup Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isModelDownloaded) FitForgeThemeColors.Primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val penalties by blockManager.penaltiesFlow.collectAsState()
                val pendingOrdersCount = penalties.count { it.status == PenaltyStatus.PENDING }
                IconButton(onClick = { showPenaltiesScreen = true }) {
                    BadgedBox(
                        badge = {
                            if (pendingOrdersCount > 0) {
                                Badge(containerColor = FitForgeThemeColors.Secondary) {
                                    Text(pendingOrdersCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = "Penalties",
                            tint = FitForgeThemeColors.Secondary
                        )
                    }
                }
                IconButton(onClick = { refreshStats() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // MODEL DOWNLOAD / IMPORT ONBOARDING CARD
        if (!isModelDownloaded) {
            OnDeviceModelCard(
                modelManager = modelManager,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                downloadStatusText = downloadStatusText,
                onStartDownload = {
                    isDownloading = true
                    scope.launch {
                        val res = modelManager.downloadModel { downloaded, total, percent ->
                            downloadProgress = percent
                            val dlMb = downloaded / (1024.0 * 1024.0)
                            val totalMb = total / (1024.0 * 1024.0)
                            downloadStatusText = if (total > 0) {
                                String.format("%.0f MB / %.0f MB (%.0f%%)", dlMb, totalMb, percent * 100)
                            } else {
                                String.format("%.1f MB downloaded", dlMb)
                            }
                        }
                        isDownloading = false
                        if (res.isSuccess) {
                            isModelDownloaded = true
                            Toast.makeText(context, "Gemmasutra Mini 2B is ready!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Download failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onPickLocalFile = {
                    filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                }
            )
            return@Column
        }

        // USAGE ACCESS PERMISSION WARNING BANNER
        if (!hasUsagePermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.Secondary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Usage Access Required",
                            fontWeight = FontWeight.Bold,
                            color = FitForgeThemeColors.Secondary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Grant permission so Ishita can audit distracting apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            context.startActivity(usageRepo.createUsageAccessSettingsIntent())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ACCESSIBILITY SERVICE PERMISSION WARNING BANNER
        if (!hasAccessibilityPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.Secondary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Accessibility Service Required",
                            fontWeight = FontWeight.Bold,
                            color = FitForgeThemeColors.Secondary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Enable FitForge in Accessibility settings to track web domains and enforce app/site blocks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            context.startActivity(FitForgeAccountabilityAccessibilityService.createSettingsIntent())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Enable", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DAILY DISCIPLINE STRIP
        Card(
            colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TODAY'S AUDIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = FitForgeThemeColors.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (vm.todaySession.collectAsState().value != null) "✓ WORKOUT LOGGED" else "⚠️ WORKOUT PENDING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (vm.todaySession.collectAsState().value != null) FitForgeThemeColors.Success else FitForgeThemeColors.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(14.dp), tint = FitForgeThemeColors.Primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Top Apps:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (appUsageStats.isEmpty()) {
                            Text("No major distraction recorded", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                appUsageStats.take(2).joinToString { "${it.appName} (${it.foregroundMinutes}m)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp), tint = FitForgeThemeColors.Primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Web Domains:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (webUsageStats.isEmpty()) {
                            Text("No web browsing logged", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                webUsageStats.take(2).joinToString { "${it.domain} (${it.totalMinutes}m)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ACTIVE BLOCKS (IF ANY)
        if (activeBlocks.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items(activeBlocks) { b ->
                    Surface(
                        color = FitForgeThemeColors.Secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, FitForgeThemeColors.Secondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Block, null, tint = FitForgeThemeColors.Secondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${b.displayName} (${b.remainingMinutes}m left)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Unblock",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp).clickable { blockManager.unblock(b.id) }
                            )
                        }
                    }
                }
            }
        }

        // DISCIPLINE & PENALTIES HUB SHORTCUT BANNER
        val allPenalties by blockManager.penaltiesFlow.collectAsState()
        Surface(
            color = FitForgeThemeColors.Secondary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { showPenaltiesScreen = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = FitForgeThemeColors.Secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Discipline & Penalties Hub",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FitForgeThemeColors.Secondary
                    )
                }
                Text(
                    "View All (${allPenalties.size}) →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // PENDING PROPOSAL CARDS
        val pendingPenalties = allPenalties.filter { it.status == PenaltyStatus.PENDING }
        if (pendingPenalties.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                pendingPenalties.forEach { penalty ->
                    val proposal = penalty.toProposedAction()
                    ProposalActionCard(
                        proposal = proposal,
                        onConfirm = {
                            scope.launch {
                                client.confirmProposal(proposal.id)
                                if (!hasAccessibilityPermission && (proposal.type == "BLOCK_APP" || proposal.type == "BLOCK_WEBSITE")) {
                                    Toast.makeText(context, "Enable FitForge Accessibility Service to enforce block!", Toast.LENGTH_LONG).show()
                                    context.startActivity(FitForgeAccountabilityAccessibilityService.createSettingsIntent())
                                }
                                val dur = proposal.payload.optInt("durationMinutes", 60).coerceAtLeast(1)
                                when (proposal.type) {
                                    "BLOCK_APP" -> {
                                        val pkg = proposal.payload.optString("packageName", "").ifEmpty { proposal.payload.optString("appName", "") }
                                        val name = proposal.payload.optString("appName", pkg)
                                        if (pkg.isNotBlank() || name.isNotBlank()) {
                                            blockManager.blockApp(pkg.ifBlank { name }, name, dur, proposal.reason)
                                        }
                                    }
                                    "BLOCK_WEBSITE" -> {
                                        val domain = proposal.payload.optString("domain", "").ifEmpty { proposal.title.removePrefix("Block ").trim() }
                                        if (domain.isNotBlank()) {
                                            blockManager.blockWebsite(domain, dur, proposal.reason)
                                        }
                                    }
                                }
                                blockManager.updatePenaltyStatus(proposal.id, PenaltyStatus.ACTIVE)
                                Toast.makeText(context, "Action Confirmed & Enforced for ${dur}m", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onReject = {
                            scope.launch {
                                client.rejectProposal(proposal.id)
                                blockManager.updatePenaltyStatus(proposal.id, PenaltyStatus.REJECTED)
                            }
                        }
                    )
                }
            }
        }

        // CONVERSATION TIMELINE
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
            if (isStreaming) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = FitForgeThemeColors.Secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(streamingStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // QUICK ACTION CHIPS
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            item {
                SuggestionChip(
                    onClick = { sendMessage("Audit my screen and web time today. Be brutal.") },
                    label = { Text("Audit Screen Time") }
                )
            }
            item {
                SuggestionChip(
                    onClick = { sendMessage("I skipped my workout today because I was tired.") },
                    label = { Text("I Skipped Today") }
                )
            }
            item {
                SuggestionChip(
                    onClick = { sendMessage("What workout should I do to make up for my missed session?") },
                    label = { Text("Makeup Session") }
                )
            }
        }

        // INPUT BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Respond to Ishita...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { sendMessage(inputText) },
                enabled = inputText.isNotBlank() && !isStreaming
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isStreaming) FitForgeThemeColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // SETTINGS DIALOG
    if (showSettingsDialog) {
        OnDeviceModelSettingsDialog(
            modelManager = modelManager,
            client = client,
            onPickLocalFile = {
                filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
            },
            onModelDeleted = {
                client.release()
                isModelDownloaded = false
                showSettingsDialog = false
            },
            onClearChat = {
                chatManager.clearChat()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun OnDeviceModelCard(
    modelManager: OnDeviceModelManager,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStatusText: String,
    onStartDownload: () -> Unit,
    onPickLocalFile: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = FitForgeThemeColors.Secondary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Gemmasutra Mini 2B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("On-Device Local Inference • 100% Offline & Private", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Model: Gemmasutra-Mini-2B-v1 (Q4_K_M Quantized)", style = MaterialTheme.typography.bodySmall)
                    Text("• File Size: ~1.4 GB GGUF", style = MaterialTheme.typography.bodySmall)
                    Text("• Engine: llama.cpp native on-device runtime", style = MaterialTheme.typography.bodySmall)
                    Text("• Zero network calls: Your data never leaves this phone", style = MaterialTheme.typography.bodySmall, color = FitForgeThemeColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (downloadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = FitForgeThemeColors.Secondary
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = FitForgeThemeColors.Secondary
                        )
                    }
                    Text(
                        downloadStatusText.ifEmpty { "Downloading model..." },
                        style = MaterialTheme.typography.labelSmall,
                        color = FitForgeThemeColors.Secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Button(
                    onClick = onStartDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Secondary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DOWNLOAD GEMMASUTRA MINI 2B (~1.4 GB)", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onPickLocalFile,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Existing .GGUF File", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun OnDeviceModelSettingsDialog(
    modelManager: OnDeviceModelManager,
    client: OnDeviceIshitaClient,
    onPickLocalFile: () -> Unit,
    onModelDeleted: () -> Unit,
    onClearChat: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isReloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("On-Device Model Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Model: ${OnDeviceModelManager.MODEL_DISPLAY_NAME}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Storage: ${modelManager.getModelFileSizeFormatted()}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Status: " + if (client.isModelLoaded) "Loaded in RAM" else "Stored on Device",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (client.isModelLoaded) FitForgeThemeColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        isReloading = true
                        scope.launch {
                            val res = client.loadModel()
                            isReloading = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Model loaded into memory!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isReloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isReloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reload Model into RAM")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPickLocalFile()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Different GGUF")
                }

                OutlinedButton(
                    onClick = {
                        onClearChat()
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Chat History")
                }

                OutlinedButton(
                    onClick = {
                        val deleted = modelManager.deleteModel()
                        if (deleted) {
                            Toast.makeText(context, "Model deleted. Storage reclaimed.", Toast.LENGTH_SHORT).show()
                            onModelDeleted()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Model File (~1.4 GB)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun AccountabilityPenalty.toProposedAction(): ProposedAction {
    val payload = JSONObject()
    val dur = if (durationMinutes > 0) durationMinutes else 60
    when (type) {
        "BLOCK_APP" -> {
            val resolvedTarget = target?.ifBlank { null } ?: title.removePrefix("Block ").trim()
            payload.put("packageName", resolvedTarget)
            payload.put("appName", resolvedTarget)
            payload.put("durationMinutes", dur)
        }
        "BLOCK_WEBSITE" -> {
            val resolvedTarget = target?.ifBlank { null } ?: title.removePrefix("Block ").trim()
            payload.put("domain", resolvedTarget)
            payload.put("durationMinutes", dur)
        }
        else -> {
            if (target != null) payload.put("target", target)
            payload.put("durationMinutes", dur)
        }
    }
    return ProposedAction(
        id = id,
        type = type,
        title = title,
        reason = reason.ifBlank { description },
        payload = payload
    )
}

@Composable
fun ProposalActionCard(
    proposal: ProposedAction,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, FitForgeThemeColors.Secondary, RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = FitForgeThemeColors.Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        proposal.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = FitForgeThemeColors.Secondary
                    )
                }

                Surface(
                    color = FitForgeThemeColors.Secondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        proposal.type,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = FitForgeThemeColors.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                proposal.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Secondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CONFIRM & ENFORCE", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: CoachMessage) {
    val isUser = msg.isUser
    val raw = msg.text

    // Separate thinking monologue from actual coach verdict if present
    val (thoughtText, responseText) = remember(raw) {
        if (raw.contains("<think>")) {
            val parts = raw.split("</think>", limit = 2)
            val thought = parts[0].substringAfter("<think>").trim()
            val rest = if (parts.size > 1) parts[1].trim() else ""
            Pair(thought, rest)
        } else {
            Pair("", raw)
        }
    }

    // Strip raw XML proposal tags from displayed text (they are shown as interactive cards above)
    val displayResponse = remember(responseText) {
        responseText.replace(Regex("<proposal[^>]*/>", RegexOption.IGNORE_CASE), "").trim()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else FitForgeThemeColors.SurfaceElevated
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Ishita",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) FitForgeThemeColors.Primary else FitForgeThemeColors.Secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))

                if (!isUser && thoughtText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = FitForgeThemeColors.Secondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (displayResponse.isEmpty()) "Ishita is analyzing..." else "Discipline Assessment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FitForgeThemeColors.Secondary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (displayResponse.isEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    thoughtText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                if (displayResponse.isNotEmpty()) {
                    MarkdownText(
                        markdown = displayResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (!isUser && thoughtText.isEmpty()) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = FitForgeThemeColors.Secondary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Gemmasutra 2B is thinking...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
