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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
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
// 4. BODY & MEASUREMENTS SCREEN
// -------------------------------------------------------------
@Composable
fun BodyScreen(vm: FitForgeViewModel) {
    val entries by vm.bodyEntries.collectAsState()
    val context = LocalContext.current
    var weight by remember { mutableStateOf("75") }
    var waist by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoPathFront by remember { mutableStateOf<String?>(null) }
    var photoPathBack by remember { mutableStateOf<String?>(null) }
    var photoPathSide by remember { mutableStateOf<String?>(null) }
    var activePhotoSlot by remember { mutableStateOf("FRONT") }
    var showMoreFields by remember { mutableStateOf(false) }

    var editingEntry by remember { mutableStateOf<BodyEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<BodyEntry?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)!!
                val dir = File(context.filesDir, "progress_photos").apply { mkdirs() }
                val slot = activePhotoSlot.lowercase()
                val target = File(dir, "progress_${slot}_${System.currentTimeMillis()}.jpg")
                input.use { source -> FileOutputStream(target).use { sink -> source.copyTo(sink) } }
                when (activePhotoSlot) {
                    "FRONT" -> photoPathFront = target.absolutePath
                    "BACK" -> photoPathBack = target.absolutePath
                    "SIDE" -> photoPathSide = target.absolutePath
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Body & transformation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Weigh in under similar conditions (morning fasted). Measure waist weekly.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = waist,
                            onValueChange = { waist = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Waist (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    if (showMoreFields) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = neck,
                                onValueChange = { neck = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Neck (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = chest,
                                onValueChange = { chest = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Chest (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Notes / Observations") },
                            placeholder = { Text("e.g. Fasted morning weight, relaxed waist") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 2
                        )
                    }

                    TextButton(onClick = { showMoreFields = !showMoreFields }) {
                        Text(if (showMoreFields) "Fewer fields" else "+ Neck, Chest & Notes", style = MaterialTheme.typography.labelMedium)
                    }

                    // Three photo slots: Front, Back, Side
                    ProgressPhotoSlots(
                        photoPathFront = photoPathFront,
                        photoPathBack = photoPathBack,
                        photoPathSide = photoPathSide,
                        onPickPhoto = { slot ->
                            activePhotoSlot = slot
                            photoPicker.launch("image/*")
                        },
                        onRemovePhoto = { slot ->
                            when (slot) {
                                "FRONT" -> photoPathFront = null
                                "BACK" -> photoPathBack = null
                                "SIDE" -> photoPathSide = null
                            }
                        }
                    )

                    Button(
                        onClick = {
                            val w = weight.toDoubleOrNull() ?: 0.0
                            if (w > 0.0) {
                                vm.addBody(
                                    weight = w,
                                    waist = waist.toDoubleOrNull(),
                                    photoPath = photoPathFront,
                                    photoPathBack = photoPathBack,
                                    photoPathSide = photoPathSide,
                                    neck = neck.toDoubleOrNull(),
                                    chest = chest.toDoubleOrNull(),
                                    note = note.trim()
                                )
                                waist = ""
                                neck = ""
                                chest = ""
                                note = ""
                                photoPathFront = null
                                photoPathBack = null
                                photoPathSide = null
                            }
                        },
                        enabled = (weight.toDoubleOrNull() ?: 0.0) > 0.0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save measurement")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Measurement History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${entries.size} logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No measurements recorded yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingEntry = entry },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EntryPhotosThumbnailRow(entry)

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.date, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${entry.weightKg} kg",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    entry.waistCm?.let {
                                        Text("Waist: $it cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    entry.neckCm?.let {
                                        Text("Neck: $it cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    entry.chestCm?.let {
                                        Text("Chest: $it cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        entry.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { editingEntry = entry }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Measurement", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet for editing historical body measurement
    editingEntry?.let { entry ->
        EditBodyEntryBottomSheet(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { updated ->
                vm.updateBody(updated)
                editingEntry = null
            },
            onDelete = { id ->
                entryToDelete = entry
            }
        )
    }

    // Confirmation Alert Dialog for deletion
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Measurement?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete the ${entry.weightKg} kg entry from ${entry.date}?") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteBody(entry.id)
                        entryToDelete = null
                        editingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBodyEntryBottomSheet(
    entry: BodyEntry,
    onDismiss: () -> Unit,
    onSave: (BodyEntry) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current

    var editDate by remember { mutableStateOf(entry.date) }
    var editWeight by remember { mutableStateOf(if (entry.weightKg > 0.0) "${entry.weightKg}" else "") }
    var editWaist by remember { mutableStateOf(entry.waistCm?.let { "$it" } ?: "") }
    var editNeck by remember { mutableStateOf(entry.neckCm?.let { "$it" } ?: "") }
    var editChest by remember { mutableStateOf(entry.chestCm?.let { "$it" } ?: "") }
    var editNote by remember { mutableStateOf(entry.note) }
    var editPhotoPathFront by remember { mutableStateOf(entry.photoPath) }
    var editPhotoPathBack by remember { mutableStateOf(entry.photoPathBack) }
    var editPhotoPathSide by remember { mutableStateOf(entry.photoPathSide) }
    var editActivePhotoSlot by remember { mutableStateOf("FRONT") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)!!
                val dir = File(context.filesDir, "progress_photos").apply { mkdirs() }
                val slot = editActivePhotoSlot.lowercase()
                val target = File(dir, "progress_${slot}_${System.currentTimeMillis()}.jpg")
                input.use { source -> FileOutputStream(target).use { sink -> source.copyTo(sink) } }
                when (editActivePhotoSlot) {
                    "FRONT" -> editPhotoPathFront = target.absolutePath
                    "BACK" -> editPhotoPathBack = target.absolutePath
                    "SIDE" -> editPhotoPathSide = target.absolutePath
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Measurement",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = editDate,
                onValueChange = { editDate = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editWeight,
                    onValueChange = { editWeight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (kg) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editWaist,
                    onValueChange = { editWaist = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Waist (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editNeck,
                    onValueChange = { editNeck = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Neck (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editChest,
                    onValueChange = { editChest = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Chest (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = editNote,
                onValueChange = { editNote = it },
                label = { Text("Notes / Observations (optional)") },
                placeholder = { Text("Fasted, post-workout, creatine retention, etc.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            // Progress photos preview & management (Front, Back, Side)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressPhotoSlots(
                        photoPathFront = editPhotoPathFront,
                        photoPathBack = editPhotoPathBack,
                        photoPathSide = editPhotoPathSide,
                        onPickPhoto = { slot ->
                            editActivePhotoSlot = slot
                            photoPicker.launch("image/*")
                        },
                        onRemovePhoto = { slot ->
                            when (slot) {
                                "FRONT" -> editPhotoPathFront = null
                                "BACK" -> editPhotoPathBack = null
                                "SIDE" -> editPhotoPathSide = null
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onDelete(entry.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }

                Button(
                    onClick = {
                        val parsedWeight = editWeight.toDoubleOrNull()
                        if (parsedWeight != null && parsedWeight > 0.0) {
                            onSave(
                                entry.copy(
                                    date = editDate.trim().ifBlank { entry.date },
                                    weightKg = parsedWeight,
                                    waistCm = editWaist.toDoubleOrNull(),
                                    neckCm = editNeck.toDoubleOrNull(),
                                    chestCm = editChest.toDoubleOrNull(),
                                    note = editNote.trim(),
                                    photoPath = editPhotoPathFront,
                                    photoPathBack = editPhotoPathBack,
                                    photoPathSide = editPhotoPathSide
                                )
                            )
                        }
                    },
                    enabled = (editWeight.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Changes")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProgressPhotoSlots(
    photoPathFront: String?,
    photoPathBack: String?,
    photoPathSide: String?,
    onPickPhoto: (slot: String) -> Unit,
    onRemovePhoto: (slot: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Progress Photos (Front, Back & Side)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            val count = listOfNotNull(photoPathFront, photoPathBack, photoPathSide).size
            if (count > 0) {
                Text(
                    "$count / 3 attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressPhotoSlotItem(
                label = "Front View",
                photoPath = photoPathFront,
                modifier = Modifier.weight(1f),
                onClick = { onPickPhoto("FRONT") },
                onRemove = { onRemovePhoto("FRONT") }
            )
            ProgressPhotoSlotItem(
                label = "Back View",
                photoPath = photoPathBack,
                modifier = Modifier.weight(1f),
                onClick = { onPickPhoto("BACK") },
                onRemove = { onRemovePhoto("BACK") }
            )
            ProgressPhotoSlotItem(
                label = "Side View",
                photoPath = photoPathSide,
                modifier = Modifier.weight(1f),
                onClick = { onPickPhoto("SIDE") },
                onRemove = { onRemovePhoto("SIDE") }
            )
        }
    }
}

@Composable
fun ProgressPhotoSlotItem(
    label: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val bitmap = remember(photoPath) {
        if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
            runCatching { BitmapFactory.decodeFile(photoPath)?.asImageBitmap() }.getOrNull()
        } else null
    }

    Surface(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (bitmap != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            if (bitmap != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topEnd = 6.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(26.dp)
                        .padding(2.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $label",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "+ Add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EntryPhotosThumbnailRow(entry: BodyEntry) {
    val photos = remember(entry) {
        listOfNotNull(
            entry.photoPath?.let { "Front" to it },
            entry.photoPathBack?.let { "Back" to it },
            entry.photoPathSide?.let { "Side" to it }
        ).filter { File(it.second).exists() }
    }
    if (photos.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            photos.forEach { (tag, path) ->
                val bm = remember(path) {
                    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                }
                if (bm != null) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = bm,
                            contentDescription = tag,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(topEnd = 4.dp),
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Text(
                                tag,
                                color = Color.White,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


