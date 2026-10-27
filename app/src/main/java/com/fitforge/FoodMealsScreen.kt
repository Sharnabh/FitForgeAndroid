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
// 2. FOOD & MEALS SCREEN (Log Meals & Custom Recipes)
// -------------------------------------------------------------
@Composable
fun FoodMealsScreen(vm: FitForgeViewModel) {
    var foodSubTab by remember { mutableStateOf(0) } // 0: Log Meals, 1: Custom Recipes

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = foodSubTab) {
            Tab(selected = foodSubTab == 0, onClick = { foodSubTab = 0 }, text = { Text("Log Meals") }, icon = { Icon(Icons.Default.Restaurant, null) })
            Tab(selected = foodSubTab == 1, onClick = { foodSubTab = 1 }, text = { Text("Custom Recipes") }, icon = { Icon(Icons.Default.MenuBook, null) })
        }

        Box(Modifier.weight(1f)) {
            if (foodSubTab == 0) {
                FoodDiaryScreen(vm)
            } else {
                CustomRecipesScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodDiaryScreen(vm: FitForgeViewModel) {
    val localFoods by vm.foods.collectAsState()
    val logs by vm.todayLogs.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var selected by remember { mutableStateOf<Food?>(null) }
    var portionMode by remember { mutableStateOf("pieces") } // "pieces" or "grams"
    var pieceCount by remember { mutableStateOf("2") }
    var pieceWeight by remember { mutableStateOf("40") }
    var servingUnitName by remember { mutableStateOf("piece") }
    var grams by remember { mutableStateOf("100") }
    var showEditUnitWeight by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        if (selected != null) {
            val (unit, weight) = selected!!.getDefaultServingUnitAndWeight()
            servingUnitName = unit
            pieceWeight = if (weight % 1.0 == 0.0) "${weight.toInt()}" else "$weight"
            showEditUnitWeight = false

            if (unit != "g") {
                portionMode = "pieces"
                pieceCount = if (unit == "roti" || unit == "slice") "2" else "1"
            } else {
                portionMode = "grams"
                grams = "100"
            }
        }
    }

    val savedFoodIds = remember(localFoods) { localFoods.map { it.id }.toSet() }

    val filteredLocal = remember(localFoods, vm.searchQuery) {
        val q = vm.searchQuery.trim()
        if (q.isBlank()) localFoods else localFoods.filter { it.name.contains(q, ignoreCase = true) || it.category.contains(q, true) }
    }

    val displayOnline = vm.onlineFoods

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text("Meal Logging", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Search real foods & track your nutrition", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Meal Type Selector (Breakfast, Lunch, Dinner, Snack)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Select Meal to Log Into:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MEAL_TYPES.forEach { meal ->
                        val isSelected = vm.selectedMealType.equals(meal, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { vm.setMealType(meal) },
                            label = { Text(meal) }
                        )
                    }
                }
            }
        }

        // Search Bar with automatic debounce
        item {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search food or enter barcode...") },
                placeholder = { Text("e.g. Chicken breast, Oats, Roti, Bread, Milk") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (vm.isSearchingOnline) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (vm.searchQuery.isNotBlank()) {
                        IconButton(onClick = { vm.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    vm.searchOnline()
                })
            )
        }

        // Search loading indicator & status
        if (vm.isSearchingOnline) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Searching foods...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (vm.searchStatusMessage != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        vm.searchStatusMessage!!,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Online Search Results Section
        if (displayOnline.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Search Results (${displayOnline.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Tap + to log portion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(displayOnline) { food ->
                FoodCardItem(
                    food = food,
                    isSaved = savedFoodIds.contains(food.id),
                    onSelect = { selected = food },
                    onSave = { vm.saveFoodToDb(food) }
                )
            }
        }

        // Local / Saved Database Foods Section
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Foods (${filteredLocal.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (filteredLocal.isEmpty() && localFoods.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No saved foods yet",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Type any food name above (e.g. roti, bread, eggs) to search real foods and tap '+' to log portions by pieces or grams.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredLocal) { food ->
                FoodCardItem(
                    food = food,
                    isSaved = true,
                    onSelect = { selected = food },
                    onSave = { }
                )
            }
        }

        // Today's Meals Section
        item {
            Text("Logged Today by Meal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        MEAL_TYPES.forEach { meal ->
            val mealItems = logs.filter { it.mealType.equals(meal, ignoreCase = true) }
            item {
                Text(
                    "$meal (${mealItems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (mealItems.isEmpty()) {
                item {
                    Text("No $meal logged yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(mealItems) { log ->
                    ListItem(
                        headlineContent = { Text("${log.foodName} · ${formatFoodPortion(log)}", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                "${log.kcal.toInt()} kcal · P ${log.protein.toInt()}g · C ${log.carbs.toInt()}g · F ${log.fat.toInt()}g",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.deleteLog(log.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Portion Calculator & Logger
    // Opens smoothly right where the user taps '+' without scrolling back to the top!
    if (selected != null) {
        val currentFood = selected!!
        val isPieceMode = portionMode == "pieces"
        val parsedCount = (pieceCount.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.1)
        val parsedPieceWeight = (pieceWeight.toDoubleOrNull() ?: 40.0).coerceAtLeast(1.0)
        val gVal = if (isPieceMode) (parsedCount * parsedPieceWeight) else ((grams.toDoubleOrNull() ?: 100.0).coerceAtLeast(1.0))
        val mult = gVal / 100.0
        val calcKcal = (currentFood.kcalPer100g * mult).toInt()
        val calcProtein = Math.round(currentFood.proteinPer100g * mult * 10.0) / 10.0
        val calcCarbs = Math.round(currentFood.carbsPer100g * mult * 10.0) / 10.0
        val calcFat = Math.round(currentFood.fatPer100g * mult * 10.0) / 10.0
        val calcFiber = Math.round(currentFood.fiberPer100g * mult * 10.0) / 10.0

        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        if (currentFood.category.isNotBlank()) {
                            Text(
                                currentFood.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            currentFood.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Meal Selector Row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Log to:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MEAL_TYPES.forEach { meal ->
                            val isSelectedMeal = vm.selectedMealType.equals(meal, ignoreCase = true)
                            FilterChip(
                                selected = isSelectedMeal,
                                onClick = { vm.setMealType(meal) },
                                label = { Text(meal) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Portion Mode Picker (Pieces vs Grams)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val unitLabel = if (servingUnitName != "g") servingUnitName.replaceFirstChar { it.uppercase() } + "s" else "Pieces"
                    FilterChip(
                        selected = isPieceMode,
                        onClick = { portionMode = "pieces" },
                        label = { Text("By $unitLabel (e.g. 1, 2, 3)") },
                        leadingIcon = { Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isPieceMode,
                        onClick = { portionMode = "grams" },
                        label = { Text("By Grams (g)") },
                        leadingIcon = { Icon(Icons.Default.Scale, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isPieceMode) {
                    // Piece Counter Row: [-] [Count] [+]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                val c = (pieceCount.toDoubleOrNull() ?: 1.0) - 1.0
                                if (c >= 0.5) {
                                    pieceCount = if (c % 1.0 == 0.0) "${c.toInt()}" else "$c"
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }

                        OutlinedTextField(
                            value = pieceCount,
                            onValueChange = { pieceCount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Number of ${servingUnitName}s") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        FilledIconButton(
                            onClick = {
                                val c = (pieceCount.toDoubleOrNull() ?: 1.0) + 1.0
                                pieceCount = if (c % 1.0 == 0.0) "${c.toInt()}" else "$c"
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }

                    // Quick Piece Chips
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("1", "2", "3", "4", "5", "6").forEach { p ->
                            val isCur = pieceCount == p
                            FilterChip(
                                selected = isCur,
                                onClick = { pieceCount = p },
                                label = { Text("$p $servingUnitName${if (p != "1") "s" else ""}") }
                            )
                        }
                    }

                    // Reference Weight Info Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "1 $servingUnitName ≈ ${parsedPieceWeight.toInt()}g · Total: ${gVal.toInt()}g",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { showEditUnitWeight = !showEditUnitWeight }) {
                                    Text(if (showEditUnitWeight) "Done" else "Adjust Weight", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (showEditUnitWeight) {
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = pieceWeight,
                                    onValueChange = { pieceWeight = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Weight per 1 $servingUnitName (grams)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    // Grams Input & Quick Chips
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("50", "100", "150", "200", "250", "300").forEach { portion ->
                            SuggestionChip(
                                onClick = { grams = portion },
                                label = { Text("${portion}g") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = grams,
                        onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Portion Quantity (grams)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Scaled Nutrition Banner
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$calcKcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text("kcal", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${calcProtein}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Protein, style = MaterialTheme.typography.titleMedium)
                            Text("protein", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${calcCarbs}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Carbs, style = MaterialTheme.typography.titleMedium)
                            Text("carbs", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${calcFat}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Fat, style = MaterialTheme.typography.titleMedium)
                            Text("fat", style = MaterialTheme.typography.labelSmall)
                        }
                        if (calcFiber > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${calcFiber}g", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("fiber", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val countVal = if (isPieceMode) parsedCount else 0.0
                            val unitVal = if (isPieceMode) servingUnitName else "g"
                            vm.addFoodLog(
                                food = currentFood,
                                grams = gVal,
                                quantity = countVal,
                                unit = unitVal,
                                meal = vm.selectedMealType
                            )
                            selected = null
                            grams = "100"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        val logDesc = if (isPieceMode) {
                            val cStr = if (parsedCount % 1.0 == 0.0) "${parsedCount.toInt()}" else "$parsedCount"
                            val pUnit = if (parsedCount > 1.0 && !servingUnitName.endsWith("s")) "${servingUnitName}s" else servingUnitName
                            "Log $cStr $pUnit (${gVal.toInt()}g)"
                        } else {
                            "Log ${gVal.toInt()}g"
                        }
                        Text("$logDesc to ${vm.selectedMealType}")
                    }

                    if (!savedFoodIds.contains(currentFood.id)) {
                        OutlinedButton(onClick = { vm.saveFoodToDb(currentFood) }) {
                            Icon(Icons.Default.BookmarkBorder, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// CUSTOM RECIPES SCREEN
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRecipesScreen(vm: FitForgeViewModel) {
    val recipes by vm.recipes.collectAsState()
    val ingredientsMap by vm.recipeIngredients.collectAsState()
    val foods by vm.foods.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var recipeNameInput by remember { mutableStateOf("") }
    var servingsInput by remember { mutableStateOf("1") }
    var instructionsInput by remember { mutableStateOf("") }

    var selectedRecipeForIngredient by remember { mutableStateOf<Recipe?>(null) }
    var ingredientSearchQuery by remember { mutableStateOf("") }
    var ingredientGrams by remember { mutableStateOf("100") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Custom Recipes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Combine ingredients to estimate total & per-serving nutrition", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("New Recipe")
                }
            }
        }

        if (recipes.isEmpty()) {
            item {
                Text("No custom recipes yet. Tap 'New Recipe' to create your first recipe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(recipes) { recipe ->
            val ingredients = ingredientsMap[recipe.id] ?: emptyList()
            val totalGrams = ingredients.sumOf { it.grams }
            val totalKcal = ingredients.sumOf { it.kcal }
            val totalProtein = ingredients.sumOf { it.protein }
            val totalCarbs = ingredients.sumOf { it.carbs }
            val totalFat = ingredients.sumOf { it.fat }
            val servings = recipe.servings.coerceAtLeast(1.0)
            val kcalPerServing = totalKcal / servings
            val proteinPerServing = totalProtein / servings
            val carbsPerServing = totalCarbs / servings
            val fatPerServing = totalFat / servings

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${servings.toInt()} serving(s) · Total batch: ${totalGrams.toInt()}g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteRecipe(recipe.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Recipe", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Per-serving nutrition banner
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${kcalPerServing.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("kcal/serv", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${Math.round(proteinPerServing * 10.0)/10.0}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Protein)
                                Text("protein", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${Math.round(carbsPerServing * 10.0)/10.0}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Carbs)
                                Text("carbs", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${Math.round(fatPerServing * 10.0)/10.0}g", fontWeight = FontWeight.Bold, color = FitForgeThemeColors.Fat)
                                Text("fat", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (recipe.instructions.isNotBlank()) {
                        Text("Instructions: ${recipe.instructions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Ingredients list
                    Text("Ingredients (${ingredients.size}):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    ingredients.forEach { ing ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${ing.foodName} (${ing.grams.toInt()}g)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${ing.kcal.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { vm.deleteRecipeIngredient(ing.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { selectedRecipeForIngredient = recipe },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Ingredient")
                        }
                        Button(
                            onClick = { vm.logRecipeServing(recipe, 1.0, vm.selectedMealType) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Log 1 Serving")
                        }
                    }
                }
            }
        }
    }

    // Create Recipe Bottom Sheet
    if (showCreateDialog) {
        val createRecipeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showCreateDialog = false },
            sheetState = createRecipeSheetState,
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
                    Text("Create Custom Recipe", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showCreateDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = recipeNameInput,
                    onValueChange = { recipeNameInput = it },
                    label = { Text("Recipe Name (e.g. Protein Oatmeal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = servingsInput,
                    onValueChange = { servingsInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Number of Servings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructionsInput,
                    onValueChange = { instructionsInput = it },
                    label = { Text("Cooking Notes / Instructions") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { showCreateDialog = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (recipeNameInput.isNotBlank()) {
                                vm.createRecipe(recipeNameInput, servingsInput.toDoubleOrNull() ?: 1.0, instructionsInput)
                                recipeNameInput = ""
                                servingsInput = "1"
                                instructionsInput = ""
                                showCreateDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = recipeNameInput.isNotBlank()
                    ) {
                        Text("Save Recipe")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Add Ingredient Bottom Sheet
    if (selectedRecipeForIngredient != null) {
        val targetRecipe = selectedRecipeForIngredient!!
        var chosenFood by remember { mutableStateOf<Food?>(null) }
        val filteredFoods = remember(foods, ingredientSearchQuery) {
            if (ingredientSearchQuery.isBlank()) foods.take(15)
            else foods.filter { it.name.contains(ingredientSearchQuery, ignoreCase = true) }
        }
        val ingredientSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

        ModalBottomSheet(
            onDismissRequest = { selectedRecipeForIngredient = null },
            sheetState = ingredientSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Ingredient to ${targetRecipe.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { selectedRecipeForIngredient = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = ingredientSearchQuery,
                    onValueChange = { ingredientSearchQuery = it },
                    label = { Text("Search ingredient") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ingredientGrams,
                    onValueChange = { ingredientGrams = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity in grams") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Food:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 240.dp)) {
                    items(filteredFoods) { food ->
                        Surface(
                            color = if (chosenFood?.id == food.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { chosenFood = food }
                        ) {
                            Text(
                                "${food.name} (${food.kcalPer100g.toInt()} kcal/100g)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { selectedRecipeForIngredient = null }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val g = ingredientGrams.toDoubleOrNull() ?: 100.0
                            if (chosenFood != null && g > 0) {
                                vm.addIngredientToRecipe(targetRecipe.id, chosenFood!!, g)
                                selectedRecipeForIngredient = null
                                ingredientSearchQuery = ""
                            }
                        },
                        enabled = chosenFood != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}



@Composable
private fun FoodCardItem(
    food: Food,
    isSaved: Boolean,
    onSelect: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (food.category.isNotBlank()) {
                    Text(
                        text = food.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(food.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)

                Text(
                    "${food.kcalPer100g.toInt()} kcal · P ${food.proteinPer100g}g · C ${food.carbsPer100g}g · F ${food.fatPer100g}g${if (food.fiberPer100g > 0) " · Fib ${food.fiberPer100g}g" else ""} per 100g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (food.state.isNotBlank() && food.state != "per 100g") {
                    Text(
                        food.state,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isSaved) {
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = "Save to database",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledIconButton(
                    onClick = onSelect,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Log food"
                    )
                }
            }
        }
    }
}

