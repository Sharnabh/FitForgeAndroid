package com.fitforge.ishita.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitforge.FitForgeThemeColors
import com.fitforge.ishita.data.AccountabilityBlockManager
import com.fitforge.ishita.data.AccountabilityPenalty
import com.fitforge.ishita.data.ActiveBlock
import com.fitforge.ishita.data.PenaltyStatus
import com.fitforge.ishita.service.FitForgeAccountabilityAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PenaltiesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val blockManager = remember { AccountabilityBlockManager.get(context) }
    val penalties by blockManager.penaltiesFlow.collectAsState()
    val activeBlocks by blockManager.activeBlocksFlow.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, ACTIVE, COMPLETED
    var showClearDialog by remember { mutableStateOf(false) }

    val hasAccessibility = remember {
        FitForgeAccountabilityAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    val filteredPenalties = remember(penalties, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> penalties.filter { it.status == PenaltyStatus.PENDING }
            "ACTIVE" -> penalties.filter { it.status == PenaltyStatus.ACTIVE }
            "COMPLETED" -> penalties.filter { it.status == PenaltyStatus.COMPLETED }
            else -> penalties
        }
    }

    val pendingCount = penalties.count { it.status == PenaltyStatus.PENDING }
    val activeCount = penalties.count { it.status == PenaltyStatus.ACTIVE } + activeBlocks.size
    val completedCount = penalties.count { it.status == PenaltyStatus.COMPLETED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitForgeThemeColors.Background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            tint = FitForgeThemeColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Accountability Penalties",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Discipline Orders & Enforced Restrictions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (penalties.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // DISCIPLINE SUMMARY CARDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Pending Orders",
                count = pendingCount,
                color = FitForgeThemeColors.Secondary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Active Locks",
                count = activeCount,
                color = if (activeCount > 0) FitForgeThemeColors.Error else FitForgeThemeColors.Primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Fulfilled",
                count = completedCount,
                color = FitForgeThemeColors.Success,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ACCESSIBILITY WARNING IF BLOCKS PENDING BUT SERVICE NOT ACTIVE
        if (!hasAccessibility && (activeCount > 0 || pendingCount > 0)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.Secondary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Accessibility Required for Locks",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = FitForgeThemeColors.Secondary
                        )
                        Text(
                            "Enable FitForge in Settings to intercept and lock blocked apps.",
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

        // FILTER CHIPS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${penalties.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FitForgeThemeColors.Secondary.copy(alpha = 0.2f),
                        selectedLabelColor = FitForgeThemeColors.Secondary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "PENDING",
                    onClick = { selectedFilter = "PENDING" },
                    label = { Text("Pending ($pendingCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FitForgeThemeColors.Secondary.copy(alpha = 0.2f),
                        selectedLabelColor = FitForgeThemeColors.Secondary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "ACTIVE",
                    onClick = { selectedFilter = "ACTIVE" },
                    label = { Text("Active ($activeCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FitForgeThemeColors.Error.copy(alpha = 0.2f),
                        selectedLabelColor = FitForgeThemeColors.Error
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "COMPLETED",
                    onClick = { selectedFilter = "COMPLETED" },
                    label = { Text("Fulfilled ($completedCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FitForgeThemeColors.Success.copy(alpha = 0.2f),
                        selectedLabelColor = FitForgeThemeColors.Success
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // PENALTIES & PROPOSALS LIST
        if (filteredPenalties.isEmpty() && activeBlocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = FitForgeThemeColors.Success.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Clean Record",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "No penalties or active discipline orders recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // Show currently active physical system locks first if in ALL or ACTIVE filter
                if (selectedFilter == "ALL" || selectedFilter == "ACTIVE") {
                    items(activeBlocks, key = { "block_${it.id}" }) { block ->
                        ActiveSystemBlockCard(
                            block = block,
                            onUnblock = {
                                blockManager.unblock(block.id)
                                Toast.makeText(context, "${block.displayName} unblocked", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                items(filteredPenalties, key = { it.id }) { penalty ->
                    PenaltyCard(
                        penalty = penalty,
                        onConfirm = {
                            if (!hasAccessibility && (penalty.type == "BLOCK_APP" || penalty.type == "BLOCK_WEBSITE")) {
                                Toast.makeText(context, "Enable FitForge Accessibility Service to enforce block!", Toast.LENGTH_LONG).show()
                                context.startActivity(FitForgeAccountabilityAccessibilityService.createSettingsIntent())
                            }
                            // Execute actual block if applicable
                            val dur = if (penalty.durationMinutes > 0) penalty.durationMinutes else 60
                            when (penalty.type) {
                                "BLOCK_APP" -> {
                                    val targetRaw = penalty.target?.ifBlank { null }
                                        ?: penalty.title.removePrefix("Block ").trim()
                                    blockManager.blockApp(targetRaw, penalty.title.removePrefix("Block "), dur, penalty.reason)
                                }
                                "BLOCK_WEBSITE" -> {
                                    val domain = penalty.target?.ifBlank { null }
                                        ?: penalty.title.removePrefix("Block ").trim()
                                    blockManager.blockWebsite(domain, dur, penalty.reason)
                                }
                            }
                            blockManager.updatePenaltyStatus(penalty.id, PenaltyStatus.ACTIVE)
                            Toast.makeText(context, "Penalty Enforced for ${dur}m", Toast.LENGTH_SHORT).show()
                        },
                        onReject = {
                            blockManager.updatePenaltyStatus(penalty.id, PenaltyStatus.REJECTED)
                            Toast.makeText(context, "Penalty Rejected", Toast.LENGTH_SHORT).show()
                        },
                        onMarkCompleted = {
                            blockManager.updatePenaltyStatus(penalty.id, PenaltyStatus.COMPLETED)
                            if (penalty.type == "BLOCK_APP" || penalty.type == "BLOCK_WEBSITE") {
                                val target = penalty.target?.ifBlank { null } ?: penalty.title.removePrefix("Block ").trim()
                                blockManager.unblock(target)
                            }
                            Toast.makeText(context, "Discipline Order Fulfilled", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            blockManager.deletePenalty(penalty.id)
                        }
                    )
                }
            }
        }
    }

    // CLEAR CONFIRMATION DIALOG
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Penalty History?") },
            text = { Text("This will remove all saved penalties, discipline orders, and proposal logs from history.") },
            confirmButton = {
                Button(
                    onClick = {
                        blockManager.clearAllPenalties()
                        showClearDialog = false
                        Toast.makeText(context, "Penalty history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FitForgeThemeColors.SurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveSystemBlockCard(
    block: ActiveBlock,
    onUnblock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.Error.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FitForgeThemeColors.Error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FitForgeThemeColors.Error.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = FitForgeThemeColors.Error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            block.displayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = FitForgeThemeColors.Error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "LOCK ACTIVE",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${block.remainingMinutes}m remaining • ${block.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onUnblock,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Release", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PenaltyCard(
    penalty: AccountabilityPenalty,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onMarkCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(penalty.issuedAtEpochMs) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(penalty.issuedAtEpochMs))
    }

    val typeIcon = when (penalty.type) {
        "BLOCK_APP", "BLOCK_WEBSITE" -> Icons.Default.Block
        "SCHEDULE_MAKEUP" -> Icons.Default.FitnessCenter
        "CREATE_REMINDER" -> Icons.Default.Notifications
        else -> Icons.Default.Warning
    }

    val statusColor = when (penalty.status) {
        PenaltyStatus.PENDING -> FitForgeThemeColors.Secondary
        PenaltyStatus.ACTIVE -> FitForgeThemeColors.Error
        PenaltyStatus.COMPLETED -> FitForgeThemeColors.Success
        PenaltyStatus.REJECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Type Icon, Title, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            penalty.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        penalty.status.name,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Reason / Punishment description
            Text(
                penalty.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (penalty.description.isNotBlank() && penalty.description != penalty.reason) {
                Spacer(Modifier.height(4.dp))
                Text(
                    penalty.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (penalty.status) {
                        PenaltyStatus.PENDING -> {
                            OutlinedButton(
                                onClick = onReject,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Reject", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = onConfirm,
                                colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Secondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Enforce Order", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        PenaltyStatus.ACTIVE -> {
                            Button(
                                onClick = onMarkCompleted,
                                colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Success),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mark Fulfilled", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        PenaltyStatus.COMPLETED, PenaltyStatus.REJECTED -> {
                            // Completed/rejected state
                        }
                    }
                }
            }
        }
    }
}

