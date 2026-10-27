package com.fitforge

import com.fitforge.data.chatgpt.CoachMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class CoachEngine {
    ChatGPT,
    Ishita
}

// -------------------------------------------------------------
// AI COACH SCREEN (Sign in with ChatGPT / SIWC)
// -------------------------------------------------------------
@Composable
fun CoachScreen(vm: FitForgeViewModel, onBack: (() -> Unit)? = null) {
    var selectedEngine by remember { mutableStateOf(CoachEngine.ChatGPT) }

    Column(Modifier.fillMaxSize()) {
        // Engine Selector
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = if (selectedEngine == CoachEngine.ChatGPT) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedEngine == CoachEngine.ChatGPT,
                    onClick = { selectedEngine = CoachEngine.ChatGPT },
                    text = {
                        Text(
                            "Cloud (ChatGPT)",
                            fontWeight = if (selectedEngine == CoachEngine.ChatGPT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedEngine == CoachEngine.Ishita,
                    onClick = { selectedEngine = CoachEngine.Ishita },
                    text = {
                        Text(
                            "Local (On-Device)",
                            fontWeight = if (selectedEngine == CoachEngine.Ishita) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        if (selectedEngine == CoachEngine.Ishita) {
            com.fitforge.ishita.ui.IshitaScreen(vm = vm, onBack = onBack)
        } else {
            val session = vm.chatGptSession
            val latestBody by vm.latestBody.collectAsState()
            var inputMessage by remember { mutableStateOf("") }
            val keyboardController = LocalSoftwareKeyboardController.current

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI Fitness Coach",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Powered by ChatGPT (Zero Extra Cost)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (session != null) {
                TextButton(onClick = { vm.signOutChatGpt() }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (session == null) {
            // Opt-in Presentation & Onboarding
            ChatGptOnboardingCard(
                isAuthenticating = vm.isChatGptAuthenticating,
                statusMessage = vm.authStatusMessage,
                hasPendingAuth = vm.hasPendingAuth,
                onConnect = { vm.startChatGptAuth() },
                onRetry = { vm.retryPendingAuth() },
                onManualCallback = { vm.completeWithManualCallback(it) }
            )
        } else {
            // Connected Dashboard & Active Chat
            // User status chip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                session.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(session.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "SIWC Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Active Model Selector / Status Bar
            LaunchedEffect(session) {
                if (vm.availableChatGptModels.isEmpty()) {
                    vm.loadChatGptModels()
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                var showModelMenu by remember { mutableStateOf(false) }
                val currentSlug = vm.selectedChatGptModel
                val currentModel = vm.availableChatGptModels.firstOrNull { it.slug == currentSlug }
                val modelLabel = currentModel?.displayName ?: currentSlug ?: if (vm.isLoadingModels) "Discovering models..." else "Account Catalog (Auto)"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = vm.availableChatGptModels.size > 1) {
                                showModelMenu = true
                            }
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Active Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (vm.availableChatGptModels.size > 1) {
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Model",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            vm.availableChatGptModels.forEach { model ->
                                val isSelected = model.slug == vm.selectedChatGptModel
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                model.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                model.slug,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        vm.selectChatGptModel(model.slug)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (vm.isLoadingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = { vm.loadChatGptModels() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh models",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { vm.clearCoachHistory() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear Chat History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { vm.showAiConsentDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Privacy & Consent Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Granular Data Sharing Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Include:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = vm.shareNutritionContext,
                    onClick = { vm.toggleShareNutrition() },
                    label = { Text("Nutrition", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (vm.shareNutritionContext) {
                        { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                    } else null
                )
                FilterChip(
                    selected = vm.shareWorkoutContext,
                    onClick = { vm.toggleShareWorkout() },
                    label = { Text("Workouts", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (vm.shareWorkoutContext) {
                        { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                    } else null
                )
                FilterChip(
                    selected = vm.shareBodyContext,
                    onClick = { vm.toggleShareBody() },
                    label = { Text("Body Measurements", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (vm.shareBodyContext) {
                        { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                    } else null
                )
            }

            Spacer(Modifier.height(6.dp))

            // Multimodal Photo Toggle & Status (Opt-in per question)
            if (latestBody?.photoPath != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (vm.attachProgressPhoto) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleAttachPhoto() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (vm.attachProgressPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Attach latest progress photo (${latestBody?.date}) for this question (opt-in)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vm.attachProgressPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = vm.attachProgressPhoto,
                            onCheckedChange = { vm.toggleAttachPhoto() },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Quick Prompt suggestions (horizontal scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Review today's calories & macro balance",
                    "Analyze my workout sets & suggest progression",
                    "Suggest a high-protein recipe for remaining macros",
                    "How is my weight trend looking?",
                    "Give me a 3-point action plan for tomorrow"
                )
                suggestions.forEach { prompt ->
                    SuggestionChip(
                        onClick = {
                            inputMessage = prompt
                            vm.askCoach(prompt)
                            inputMessage = ""
                        },
                        label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Message Stream List
            val listState = rememberLazyListState()
            LaunchedEffect(vm.coachMessages.size, vm.coachMessages.lastOrNull()?.text) {
                if (vm.coachMessages.isNotEmpty()) {
                    listState.animateScrollToItem(vm.coachMessages.size - 1)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (vm.coachMessages.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ask Your Coach",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Your AI Coach has direct access to today's food logs, calories, protein, workout sets, and body weight. Tap a suggested chip above or type your question below!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(vm.coachMessages, key = { it.id }) { msg ->
                            CoachMessageBubble(msg)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Ask your coach anything...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputMessage.isNotBlank() && !vm.isCoachStreaming) {
                            val textToSend = inputMessage
                            inputMessage = ""
                            keyboardController?.hide()
                            vm.askCoach(textToSend)
                        }
                    }),
                    maxLines = 3
                )

                Spacer(Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (vm.isCoachStreaming) {
                            vm.stopCoachStreaming()
                        } else if (inputMessage.isNotBlank()) {
                            val textToSend = inputMessage
                            inputMessage = ""
                            keyboardController?.hide()
                            vm.askCoach(textToSend)
                        }
                    },
                    containerColor = if (vm.isCoachStreaming) MaterialTheme.colorScheme.error else if (inputMessage.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (vm.isCoachStreaming) Color.White else if (inputMessage.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (vm.isCoachStreaming) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Generating")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
    }
}

    if (vm.showAiConsentDialog) {
        AiCoachConsentDialog(
            onDismiss = { vm.showAiConsentDialog = false },
            onAccept = { vm.grantAiConsent() }
        )
    }
}

@Composable
fun AiCoachConsentDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("AI Coach Privacy & Disclaimer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "FitForge AI Coach connects directly to OpenAI using your ChatGPT subscription. Before you start chatting, please review how your data is handled:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔒 Local-First Data Privacy", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text("• Only your selected context (nutrition totals, logged workout sets, and body weight) is sent to generate fitness answers.", style = MaterialTheme.typography.bodySmall)
                        Text("• Local device paths are never transmitted.", style = MaterialTheme.typography.bodySmall)
                        Text("• Progress photos are strictly opt-in per message and never sent automatically.", style = MaterialTheme.typography.bodySmall)
                        Text("• You can clear your chat history or disconnect your account at any time.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚠️ Informational Wellness Disclaimer", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        Text("• FitForge AI Coach is for general motivation and fitness informational purposes only.", style = MaterialTheme.typography.bodySmall)
                        Text("• It is NOT a medical doctor, registered dietitian, or licensed physical therapist.", style = MaterialTheme.typography.bodySmall)
                        Text("• For medical conditions, eating disorders, or physical injuries, always consult a qualified healthcare professional.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("I Understand & Agree")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CoachMessageBubble(msg: CoachMessage) {
    val isUser = msg.isUser
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
                containerColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) "You" else "FitForge AI Coach",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    if (msg.hasPhotoAttached) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "📷 Photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ChatGptOnboardingCard(
    isAuthenticating: Boolean,
    statusMessage: String?,
    hasPendingAuth: Boolean,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    onManualCallback: (String) -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var pastedLink by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Sign in with ChatGPT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Bring your current ChatGPT plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "FitForge integrates with official Sign in with ChatGPT (SIWC). Connect your personal ChatGPT account (Free, Plus, or Pro) to unlock an intelligent, real-time AI fitness coach without any extra subscription cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingFeatureRow("Zero Added Cost", "Uses your existing ChatGPT subscription allowance directly.")
                OnboardingFeatureRow("Full Fitness Awareness", "Analyzes meals, macros, recipe builder, and workout volume.")
                OnboardingFeatureRow("Multimodal Vision", "Respectfully assesses body progress photos for posture & composition.")
                OnboardingFeatureRow("Privacy First (store: false)", "Conversations are never stored on external third-party servers.")
            }

            Spacer(Modifier.height(18.dp))

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusMessage.contains("error", true) || statusMessage.contains("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (statusMessage.contains("Unable to resolve host", true)) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tip: Check that mobile data or Wi-Fi is active. You can tap Retry Token Exchange or paste your callback link below.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (hasPendingAuth) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isAuthenticating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Token Exchange")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onConnect,
                enabled = !isAuthenticating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Authorizing via browser...")
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasPendingAuth) "Start Fresh Sign In" else "Continue with ChatGPT",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = { showManualInput = !showManualInput },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    if (showManualInput) "Hide callback link input" else "Or paste callback link from browser",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showManualInput) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    placeholder = { Text("Paste http://127.0.0.1:14555/auth/callback?code=...", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (pastedLink.isNotBlank()) {
                            onManualCallback(pastedLink)
                            pastedLink = ""
                            showManualInput = false
                        }
                    },
                    enabled = pastedLink.isNotBlank() && !isAuthenticating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Confirm Callback Link", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun OnboardingFeatureRow(title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
