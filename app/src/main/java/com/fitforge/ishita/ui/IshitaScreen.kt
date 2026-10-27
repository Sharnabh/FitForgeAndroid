package com.fitforge.ishita.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.fitforge.FitForgeThemeColors
import com.fitforge.FitForgeViewModel
import com.fitforge.data.chatgpt.CoachMessage
import com.fitforge.ishita.data.AccountabilityBlockManager
import com.fitforge.ishita.data.AccountabilityContextDto
import com.fitforge.ishita.data.ActiveBlock
import com.fitforge.ishita.data.AppUsageDto
import com.fitforge.ishita.data.AppUsageStat
import com.fitforge.ishita.data.BridgeHealth
import com.fitforge.ishita.data.IshitaClient
import com.fitforge.ishita.data.IshitaEvent
import com.fitforge.ishita.data.ProposedAction
import com.fitforge.ishita.data.UsageStatsRepository
import com.fitforge.ishita.data.WebsiteUsageDto
import com.fitforge.ishita.data.WebsiteUsageStat
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun IshitaScreen(
    vm: FitForgeViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { IshitaClient(context) }
    val usageRepo = remember { UsageStatsRepository(context) }
    val blockManager = remember { AccountabilityBlockManager.get(context) }

    var isPaired by remember { mutableStateOf(client.isConnected()) }
    var bridgeHealth by remember { mutableStateOf<BridgeHealth?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Screen & web time
    var hasUsagePermission by remember { mutableStateOf(usageRepo.hasUsagePermission()) }
    var appUsageStats by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var webUsageStats by remember { mutableStateOf<List<WebsiteUsageStat>>(emptyList()) }
    val activeBlocks by blockManager.activeBlocksFlow.collectAsState()

    // Chat timeline & proposals
    val messages = remember {
        mutableStateListOf(
            CoachMessage(
                isUser = false,
                text = "I am Ishita. I don't care about your excuses—only your discipline and consistency. Did you train today, or did you waste hours staring at your phone?"
            )
        )
    }
    val pendingProposals = remember { mutableStateListOf<ProposedAction>() }
    var isStreaming by remember { mutableStateOf(false) }
    var streamingStatus by remember { mutableStateOf("Ishita is analyzing your discipline...") }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll as messages update or text streams in
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Refresh context data
    fun refreshStats() {
        hasUsagePermission = usageRepo.hasUsagePermission()
        scope.launch {
            if (hasUsagePermission) {
                appUsageStats = usageRepo.getDailyUsageStats(hoursBack = 24, maxResults = 5)
            }
            webUsageStats = blockManager.getDailyWebsiteUsage(hoursBack = 24, maxResults = 5)
            if (isPaired) {
                bridgeHealth = client.checkHealth().getOrNull()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    fun sendMessage(textToSend: String) {
        if (textToSend.isBlank() || isStreaming) return
        val userMsg = CoachMessage(isUser = true, text = textToSend)
        messages.add(userMsg)
        inputText = ""

        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val assistantMsg = CoachMessage(id = assistantMsgId, isUser = false, text = "")
        messages.add(assistantMsg)
        isStreaming = true
        streamingStatus = "Connecting to Ishita on Mac..."

        scope.launch {
            // Build current accountability context
            val schedules = vm.weeklySchedule.value
            val sessions = vm.workoutSessions.value
            val todayDate = LocalDate.now()
            val consistency = com.fitforge.data.WorkoutScheduleDefaults.calculateWeeklyConsistency(todayDate, schedules, sessions)
            val todaySession = vm.todaySession.value
            val workoutStatus = if (todaySession?.completed == true) "COMPLETED" else if (todaySession != null) "IN_PROGRESS" else "PENDING"

            val accContext = AccountabilityContextDto(
                localDate = todayDate.toString(),
                plannedWorkout = todaySession?.dayName,
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

            var accumulatedText = ""
            // Send history excluding the current user message and placeholder assistant message
            val hist = if (messages.size >= 2) messages.dropLast(2) else emptyList()

            client.streamChat(textToSend, accContext, hist).collect { event ->
                when (event) {
                    is IshitaEvent.Connected -> {
                        streamingStatus = "Connected to local model • Analyzing..."
                    }
                    is IshitaEvent.TextDelta -> {
                        accumulatedText += event.delta
                        streamingStatus = "Ishita is responding..."
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(text = accumulatedText)
                        }
                    }
                    is IshitaEvent.ProposalReceived -> {
                        pendingProposals.add(event.proposal)
                    }
                    is IshitaEvent.Error -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            val currentText = messages[idx].text
                            val errNote = if (currentText.isBlank()) "[Error: ${event.message}]" else "$currentText\n[Error: ${event.message}]"
                            messages[idx] = messages[idx].copy(text = errNote)
                        }
                    }
                    is IshitaEvent.Done -> {
                        isStreaming = false
                    }
                }
            }
            isStreaming = false
        }
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
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = FitForgeThemeColors.Secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ishita (Local Mac)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        if (isPaired) "Connected to Mac • Strict Discipline" else "Not Paired with Mac Bridge",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPaired) FitForgeThemeColors.Primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row {
                IconButton(onClick = { refreshStats() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // UNPAIRED ONBOARDING CARD
        if (!isPaired) {
            IshitaPairingCard(
                client = client,
                onPaired = {
                    isPaired = true
                    refreshStats()
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

        // PENDING PROPOSAL CARDS
        if (pendingProposals.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                pendingProposals.toList().forEach { proposal ->
                    ProposalActionCard(
                        proposal = proposal,
                        onConfirm = {
                            scope.launch {
                                client.confirmProposal(proposal.id)
                                when (proposal.type) {
                                    "BLOCK_APP" -> {
                                        val pkg = proposal.payload.optString("packageName", "")
                                        val name = proposal.payload.optString("appName", pkg)
                                        val dur = proposal.payload.optInt("durationMinutes", 60)
                                        if (pkg.isNotBlank()) {
                                            blockManager.blockApp(pkg, name, dur, proposal.reason)
                                        }
                                    }
                                    "BLOCK_WEBSITE" -> {
                                        val domain = proposal.payload.optString("domain", "")
                                        val dur = proposal.payload.optInt("durationMinutes", 60)
                                        if (domain.isNotBlank()) {
                                            blockManager.blockWebsite(domain, dur, proposal.reason)
                                        }
                                    }
                                }
                                pendingProposals.remove(proposal)
                                Toast.makeText(context, "Action Confirmed & Enforced", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onReject = {
                            scope.launch {
                                client.rejectProposal(proposal.id)
                                pendingProposals.remove(proposal)
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
        IshitaSettingsDialog(
            client = client,
            onDismiss = { showSettingsDialog = false },
            onDisconnected = {
                isPaired = false
                showSettingsDialog = false
            }
        )
    }
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
                        "PROPOSED RESTRICTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = FitForgeThemeColors.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    proposal.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(proposal.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(proposal.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                    Text(
                        text = displayResponse,
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
                            "Connecting to local model...",
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

@Composable
fun IshitaPairingCard(
    client: IshitaClient,
    onPaired: () -> Unit
) {
    var bridgeUrl by remember { mutableStateOf(client.getBridgeUrl()) }
    var pairCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, null, tint = FitForgeThemeColors.Primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Connect to Your Mac", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Private local inference over Tailscale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = { bridgeUrl = it },
                label = { Text("Bridge URL (e.g. Tailscale or LAN IP)") },
                placeholder = { Text("http://192.168.1.50:8765") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = it.uppercase() },
                label = { Text("6-Character Pairing Code") },
                placeholder = { Text("ABC123") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    client.setBridgeUrl(bridgeUrl)
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        val res = client.pair(pairCode)
                        isSubmitting = false
                        if (res.isSuccess) {
                            onPaired()
                        } else {
                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Pairing failed"
                        }
                    }
                },
                enabled = pairCode.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FitForgeThemeColors.OnPrimary)
                } else {
                    Text("PAIR WITH MAC", color = FitForgeThemeColors.OnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IshitaSettingsDialog(
    client: IshitaClient,
    onDismiss: () -> Unit,
    onDisconnected: () -> Unit
) {
    var urlInput by remember { mutableStateOf(client.getBridgeUrl()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ishita Connection Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Bridge URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        client.setBridgeUrl(urlInput)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save URL")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        client.disconnect()
                        onDisconnected()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect & Revoke Pair")
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
