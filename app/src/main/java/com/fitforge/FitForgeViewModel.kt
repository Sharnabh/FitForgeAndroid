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


const val DEFAULT_CALORIE_TARGET = 1700.0
const val DEFAULT_PROTEIN_TARGET = 130.0
const val DEFAULT_CARB_TARGET = 170.0
const val DEFAULT_FAT_TARGET = 55.0

val MEAL_TYPES = listOf("Breakfast", "Lunch", "Dinner", "Snack")

enum class FoodSourceFilter(val label: String) {
    ALL("All Sources"),
    USDA("USDA FDC"),
    OFF("OpenFoodFacts"),
    SAVED("Saved in App")
}


class FitForgeViewModel(
    private val dao: FitForgeDao,
    private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("fitforge_prefs", Context.MODE_PRIVATE)
    val today = LocalDate.now().toString()

    // Database Observables
    val foods = dao.observeFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todayLogs = dao.observeFoodLogs(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentLogs = dao.observeFoodLogsRange(LocalDate.now().minusDays(14).toString(), today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bodyEntries = dao.observeBodyEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val latestBody = dao.observeLatestBodyEntry().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recipes = dao.observeRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exercises = dao.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workoutRoutines = dao.observeWorkoutRoutines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workoutSessions = dao.observeWorkoutSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySessions = dao.observeWorkoutSessionsForDate(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySession = dao.observeWorkoutSession(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val allWorkoutSets = dao.observeAllWorkoutSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val weeklySchedule = dao.observeWeeklySchedule().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active workout session selection & multi-session support
    var activeSessionId by mutableStateOf<Long?>(null)
        private set

    fun setActiveSession(sessionId: Long) {
        activeSessionId = sessionId
        refreshTodaySession()
    }

    // Current workout sets for today
    private val _todaySets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val todaySets: StateFlow<List<WorkoutSet>> = _todaySets.asStateFlow()

    // Recipe Ingredients Map: recipeId -> list
    private val _recipeIngredients = MutableStateFlow<Map<String, List<RecipeIngredient>>>(emptyMap())
    val recipeIngredients: StateFlow<Map<String, List<RecipeIngredient>>> = _recipeIngredients.asStateFlow()

    // Online & API Search State
    var searchQuery by mutableStateOf("")
        private set
    var selectedFilter by mutableStateOf(FoodSourceFilter.ALL)
        private set
    var selectedMealType by mutableStateOf("Breakfast")
        private set
    var onlineFoods by mutableStateOf<List<Food>>(emptyList())
        private set
    var isSearchingOnline by mutableStateOf(false)
        private set
    var searchStatusMessage by mutableStateOf<String?>(null)
        private set
    var usdaApiKey by mutableStateOf(prefs.getString("usda_api_key", "j7rbFLBtmchnQnmjGCk8zHfwM6nrLJhPTqFN6qCC") ?: "j7rbFLBtmchnQnmjGCk8zHfwM6nrLJhPTqFN6qCC")
        private set

    // ChatGPT SIWC & AI Coach State
    var chatGptSession by mutableStateOf<ChatGptSession?>(null)
        private set
    var isChatGptAuthenticating by mutableStateOf(false)
        private set
    var authStatusMessage by mutableStateOf<String?>(null)
        private set

    var isChatGptScreenVisible by mutableStateOf(false)

    private fun loadCoachMessages(): List<CoachMessage> {
        val raw = prefs.getString("chatgpt_coach_messages_json", null) ?: return emptyList()
        val list = mutableListOf<CoachMessage>()
        try {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CoachMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        isUser = obj.getBoolean("isUser"),
                        text = obj.getString("text"),
                        hasPhotoAttached = obj.optBoolean("hasPhotoAttached", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun persistCoachMessages(messages: List<CoachMessage>) {
        val array = org.json.JSONArray()
        messages.forEach { m ->
            val obj = org.json.JSONObject().apply {
                put("id", m.id)
                put("isUser", m.isUser)
                put("text", m.text)
                put("hasPhotoAttached", m.hasPhotoAttached)
                put("timestamp", m.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("chatgpt_coach_messages_json", array.toString()).apply()
    }

    var coachMessages by mutableStateOf<List<CoachMessage>>(loadCoachMessages())
        private set
    var isCoachStreaming by mutableStateOf(false)
        private set
    var coachError by mutableStateOf<String?>(null)
        private set
    var attachProgressPhoto by mutableStateOf(false) // Per-message opt-in
    var hasPendingAuth by mutableStateOf(false)
        private set
    var availableChatGptModels by mutableStateOf<List<ChatGptModel>>(emptyList())
        private set
    var selectedChatGptModel by mutableStateOf<String?>(null)
        private set
    var isLoadingModels by mutableStateOf(false)
        private set

    // Privacy & Data Sharing Consent
    var hasAiConsent by mutableStateOf(prefs.getBoolean("ai_coach_consent_granted", false))
        private set
    var showAiConsentDialog by mutableStateOf(false)

    fun grantAiConsent() {
        hasAiConsent = true
        prefs.edit().putBoolean("ai_coach_consent_granted", true).apply()
        showAiConsentDialog = false
    }

    // Granular Data Sharing Controls
    var shareNutritionContext by mutableStateOf(prefs.getBoolean("ai_share_nutrition", true))
        private set
    var shareWorkoutContext by mutableStateOf(prefs.getBoolean("ai_share_workout", true))
        private set
    var shareBodyContext by mutableStateOf(prefs.getBoolean("ai_share_body", true))
        private set

    fun toggleShareNutrition() {
        shareNutritionContext = !shareNutritionContext
        prefs.edit().putBoolean("ai_share_nutrition", shareNutritionContext).apply()
    }

    fun toggleShareWorkout() {
        shareWorkoutContext = !shareWorkoutContext
        prefs.edit().putBoolean("ai_share_workout", shareWorkoutContext).apply()
    }

    fun toggleShareBody() {
        shareBodyContext = !shareBodyContext
        prefs.edit().putBoolean("ai_share_body", shareBodyContext).apply()
    }

    fun clearCoachHistory() {
        coachJob?.cancel()
        isCoachStreaming = false
        coachMessages = emptyList()
        coachError = null
        prefs.edit().remove("chatgpt_coach_messages_json").apply()
    }

    fun stopCoachStreaming() {
        coachJob?.cancel()
        isCoachStreaming = false
    }

    private var coachJob: kotlinx.coroutines.Job? = null

    fun selectChatGptModel(slug: String) {
        selectedChatGptModel = slug
    }

    // wger Online Integration State
    var wgerExercises by mutableStateOf<List<Exercise>>(emptyList())
        private set
    var isSearchingWger by mutableStateOf(false)
        private set
    var wgerError by mutableStateOf<String?>(null)
        private set
    var wgerRoutines by mutableStateOf<List<WgerRoutineBundle>>(emptyList())
        private set
    var isFetchingWgerRoutines by mutableStateOf(false)
        private set
    var wgerApiKey by mutableStateOf(prefs.getString("wger_api_key", "") ?: "")
        private set
    private var wgerSearchJob: Job? = null

    // Workout Timers & Rest Countdown State
    var isWorkoutTimerRunning by mutableStateOf(false)
        private set
    var workoutDurationSeconds by mutableStateOf(0)
        private set
    private var workoutTimerJob: Job? = null

    var isSetTimerRunning by mutableStateOf(false)
        private set
    var activeSetDurationSeconds by mutableStateOf(0)
        private set
    private var setTimerJob: Job? = null

    var isRestTimerRunning by mutableStateOf(false)
        private set
    var restSecondsRemaining by mutableStateOf(0)
        private set
    var restTotalTargetSeconds by mutableStateOf(90)
        private set
    private var restTimerJob: Job? = null

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            if (dao.foodCount() == 0 && SeedData.foods.isNotEmpty()) dao.insertFoods(SeedData.foods)
            if (dao.exerciseCount() < 500) {
                val assetExercises = ExerciseAssetImporter.loadFromAssets(context)
                if (assetExercises.isNotEmpty()) {
                    dao.insertExercises(assetExercises)
                } else if (dao.exerciseCount() == 0) {
                    dao.insertExercises(ExerciseSeedData.exercises)
                }
            }
            if (dao.routineCount() == 0) {
                dao.insertWorkoutRoutines(ExerciseSeedData.routines)
                dao.insertRoutineExercises(ExerciseSeedData.routineExercises)
            }
            if (dao.scheduleCount() == 0) {
                dao.insertSchedules(WorkoutScheduleDefaults.defaultWeeklySchedule)
            }
            if (SeedData.recipes.isNotEmpty()) {
                val currentRecipes = dao.observeRecipes().first()
                if (currentRecipes.isEmpty()) {
                    dao.insertRecipes(SeedData.recipes)
                    SeedData.recipeIngredients.forEach { dao.insertRecipeIngredient(it) }
                }
            }
            refreshRecipeIngredients()
            refreshTodaySession()
            chatGptSession = ChatGptAuthManager.getSession(context)
            hasPendingAuth = ChatGptAuthManager.hasPendingAuth(context)
            if (chatGptSession != null) {
                loadChatGptModels()
            }
        }
    }

    fun setApiKey(newKey: String) {
        val trimmed = newKey.trim()
        val key = if (trimmed.isBlank()) "j7rbFLBtmchnQnmjGCk8zHfwM6nrLJhPTqFN6qCC" else trimmed
        usdaApiKey = key
        prefs.edit().putString("usda_api_key", key).apply()
    }

    fun updateSearchQuery(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.isBlank()) {
            onlineFoods = emptyList()
            searchStatusMessage = null
            isSearchingOnline = false
            return
        }
        // Debounce: trigger live API search 400ms after user pauses typing
        if (q.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(400)
                searchOnline()
            }
        }
    }

    fun setFilter(filter: FoodSourceFilter) {
        selectedFilter = filter
    }

    fun setMealType(meal: String) {
        selectedMealType = meal
    }

    fun searchOnline() {
        searchJob?.cancel()
        val query = searchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            isSearchingOnline = true
            searchStatusMessage = "Searching foods..."
            try {
                val result = FoodApiClient.searchBoth(query, usdaApiKey)
                val combined = mutableListOf<Food>()
                combined.addAll(result.usdaFoods)
                combined.addAll(result.openFoodFactsFoods)
                onlineFoods = combined

                if (combined.isEmpty()) {
                    searchStatusMessage = "No foods found for \"$query\"."
                } else {
                    searchStatusMessage = "Found ${combined.size} foods matching \"$query\""
                }
            } catch (e: Exception) {
                searchStatusMessage = "Search error: ${e.localizedMessage ?: "Network error"}"
            } finally {
                isSearchingOnline = false
            }
        }
    }

    fun saveFoodToDb(food: Food) {
        viewModelScope.launch {
            dao.insertFood(food)
        }
    }

    // Correct macro calculation with portion multiplier
    // Multiplier = grams / 100.0
    // Calories = kcalPer100g * Multiplier
    fun addFoodLog(
        food: Food,
        grams: Double,
        quantity: Double = 0.0,
        unit: String = "g",
        meal: String = selectedMealType
    ) {
        if (grams <= 0) return
        val multiplier = grams / 100.0
        viewModelScope.launch {
            dao.insertFood(food) // Cache to local database
            dao.insertFoodLog(
                FoodLog(
                    date = today,
                    mealType = meal,
                    foodId = food.id,
                    foodName = food.name,
                    grams = Math.round(grams * 10.0) / 10.0,
                    quantity = if (quantity > 0.0) Math.round(quantity * 10.0) / 10.0 else Math.round(grams * 10.0) / 10.0,
                    unit = unit,
                    kcal = Math.round(food.kcalPer100g * multiplier * 10.0) / 10.0,
                    protein = Math.round(food.proteinPer100g * multiplier * 10.0) / 10.0,
                    carbs = Math.round(food.carbsPer100g * multiplier * 10.0) / 10.0,
                    fat = Math.round(food.fatPer100g * multiplier * 10.0) / 10.0,
                    fiber = Math.round(food.fiberPer100g * multiplier * 10.0) / 10.0
                )
            )
        }
    }

    fun deleteLog(id: Long) = viewModelScope.launch { dao.deleteFoodLog(id) }

    // Recipes
    fun refreshRecipeIngredients() {
        viewModelScope.launch {
            val list = dao.observeRecipes().first()
            val map = mutableMapOf<String, List<RecipeIngredient>>()
            for (r in list) {
                map[r.id] = dao.getRecipeIngredients(r.id)
            }
            _recipeIngredients.value = map
        }
    }

    fun createRecipe(name: String, servings: Double, instructions: String) {
        viewModelScope.launch {
            val id = "recipe_${System.currentTimeMillis()}"
            dao.insertRecipe(Recipe(id = id, name = name, servings = servings.coerceAtLeast(1.0), instructions = instructions))
            refreshRecipeIngredients()
        }
    }

    fun deleteRecipe(recipeId: String) {
        viewModelScope.launch {
            dao.deleteRecipe(recipeId)
            dao.deleteRecipeIngredients(recipeId)
            refreshRecipeIngredients()
        }
    }

    fun addIngredientToRecipe(recipeId: String, food: Food, grams: Double) {
        if (grams <= 0) return
        val multiplier = grams / 100.0
        viewModelScope.launch {
            dao.insertRecipeIngredient(
                RecipeIngredient(
                    recipeId = recipeId,
                    foodId = food.id,
                    foodName = food.name,
                    grams = grams,
                    kcal = Math.round(food.kcalPer100g * multiplier * 10.0) / 10.0,
                    protein = Math.round(food.proteinPer100g * multiplier * 10.0) / 10.0,
                    carbs = Math.round(food.carbsPer100g * multiplier * 10.0) / 10.0,
                    fat = Math.round(food.fatPer100g * multiplier * 10.0) / 10.0,
                    fiber = Math.round(food.fiberPer100g * multiplier * 10.0) / 10.0
                )
            )
            refreshRecipeIngredients()
        }
    }

    fun deleteRecipeIngredient(ingredientId: Long) {
        viewModelScope.launch {
            dao.deleteRecipeIngredient(ingredientId)
            refreshRecipeIngredients()
        }
    }

    // Log one serving of a custom recipe to today's diary
    fun logRecipeServing(recipe: Recipe, servingsToLog: Double = 1.0, meal: String = selectedMealType) {
        val ingredients = _recipeIngredients.value[recipe.id] ?: emptyList()
        val totalKcal = ingredients.sumOf { it.kcal }
        val totalProtein = ingredients.sumOf { it.protein }
        val totalCarbs = ingredients.sumOf { it.carbs }
        val totalFat = ingredients.sumOf { it.fat }
        val totalFiber = ingredients.sumOf { it.fiber }
        val totalGrams = ingredients.sumOf { it.grams }

        val ratio = servingsToLog / recipe.servings.coerceAtLeast(1.0)
        viewModelScope.launch {
            dao.insertFoodLog(
                FoodLog(
                    date = today,
                    mealType = meal,
                    foodId = recipe.id,
                    foodName = recipe.name,
                    grams = Math.round(totalGrams * ratio * 10.0) / 10.0,
                    quantity = servingsToLog,
                    unit = "serving",
                    kcal = Math.round(totalKcal * ratio * 10.0) / 10.0,
                    protein = Math.round(totalProtein * ratio * 10.0) / 10.0,
                    carbs = Math.round(totalCarbs * ratio * 10.0) / 10.0,
                    fat = Math.round(totalFat * ratio * 10.0) / 10.0,
                    fiber = Math.round(totalFiber * ratio * 10.0) / 10.0
                )
            )
        }
    }

    fun addBody(
        weight: Double,
        waist: Double?,
        photoPath: String? = null,
        photoPathBack: String? = null,
        photoPathSide: String? = null,
        neck: Double? = null,
        chest: Double? = null,
        note: String = ""
    ) {
        if (weight <= 0) return
        viewModelScope.launch {
            dao.insertBodyEntry(
                BodyEntry(
                    date = today,
                    weightKg = weight,
                    waistCm = waist,
                    neckCm = neck,
                    chestCm = chest,
                    photoPath = photoPath,
                    photoPathBack = photoPathBack,
                    photoPathSide = photoPathSide,
                    note = note
                )
            )
        }
    }

    fun updateBody(entry: BodyEntry) {
        if (entry.weightKg <= 0) return
        viewModelScope.launch {
            dao.updateBodyEntry(entry)
        }
    }

    fun deleteBody(id: Long) {
        viewModelScope.launch {
            dao.deleteBodyEntry(id)
        }
    }

    // Workout Logging & MET energy expenditure calculation
    fun calculateMetCalories(metValue: Double, durationMinutes: Int): Double {
        return WorkoutCalculations.calculateEstimatedCalories(
            metValue = metValue,
            durationMinutes = durationMinutes,
            latestBodyweightKg = latestBody.value?.weightKg
        ).estimatedKcal
    }

    fun getEstimatedCaloriesBurn(metValue: Double, durationMinutes: Int): EstimatedCaloriesResult {
        return WorkoutCalculations.calculateEstimatedCalories(
            metValue = metValue,
            durationMinutes = durationMinutes,
            latestBodyweightKg = latestBody.value?.weightKg
        )
    }

    fun createNewWorkoutSession(
        dayName: String = "Workout Session",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val newId = dao.insertWorkoutSession(
                WorkoutSession(
                    date = today,
                    dayName = dayName,
                    completed = false,
                    notes = notes
                )
            )
            activeSessionId = newId
            refreshTodaySession()
        }
    }

    fun startOrUpdateWorkoutSession(
        dayName: String,
        durationMinutes: Int,
        distanceKm: Double,
        estimatedKcal: Double,
        completed: Boolean,
        notes: String = "",
        targetSessionId: Long? = null
    ) {
        viewModelScope.launch {
            val sid = targetSessionId ?: activeSessionId
            val existing = if (sid != null) dao.getWorkoutSessionById(sid) else null
            if (existing == null) {
                val newId = dao.insertWorkoutSession(
                    WorkoutSession(
                        date = today,
                        dayName = dayName,
                        durationMinutes = durationMinutes,
                        distanceKm = distanceKm,
                        estimatedCaloriesBurned = estimatedKcal,
                        completed = completed,
                        notes = notes
                    )
                )
                activeSessionId = newId
            } else {
                dao.updateWorkoutSession(
                    existing.copy(
                        dayName = dayName,
                        durationMinutes = durationMinutes,
                        distanceKm = distanceKm,
                        estimatedCaloriesBurned = estimatedKcal,
                        completed = completed,
                        notes = notes
                    )
                )
            }
            refreshTodaySession()
        }
    }

    fun refreshTodaySession() {
        viewModelScope.launch {
            val sid = activeSessionId
            val session = if (sid != null) {
                dao.getWorkoutSessionById(sid)
            } else {
                val sessionsToday = dao.getWorkoutSessionsForDate(today)
                sessionsToday.lastOrNull { !it.completed } ?: sessionsToday.lastOrNull()
            }
            if (session != null) {
                activeSessionId = session.id
                _todaySets.value = dao.getWorkoutSets(session.id)
            } else {
                _todaySets.value = emptyList()
            }
        }
    }

    // Active Workout Routine & Multi-Exercise Progression
    var activeRoutine by mutableStateOf<WorkoutRoutine?>(null)
        private set
    val activeRoutineExercises = mutableStateListOf<RoutineExercise>()
    var currentExerciseIndex by mutableStateOf(0)
        private set
    val completedExerciseNames = mutableStateListOf<String>()

    suspend fun getRoutineExercises(routineId: String): List<RoutineExercise> = dao.getRoutineExercises(routineId)

    fun loadRoutineIntoSession(routine: WorkoutRoutine) {
        viewModelScope.launch {
            activeRoutine = routine
            activeRoutineExercises.clear()
            completedExerciseNames.clear()
            currentExerciseIndex = 0
            val exs = dao.getRoutineExercises(routine.id)
            activeRoutineExercises.addAll(exs)

            val sid = activeSessionId
            val existing = if (sid != null) dao.getWorkoutSessionById(sid) else null
            if (existing == null || existing.completed) {
                val newId = dao.insertWorkoutSession(
                    WorkoutSession(
                        date = today,
                        dayName = routine.name,
                        durationMinutes = 50,
                        estimatedCaloriesBurned = calculateMetCalories(6.0, 50),
                        completed = false
                    )
                )
                activeSessionId = newId
            } else {
                dao.updateWorkoutSession(existing.copy(dayName = routine.name))
            }
            refreshTodaySession()
        }
    }

    fun addExerciseToActiveSession(name: String, notes: String = "") {
        val nextOrder = activeRoutineExercises.size + 1
        activeRoutineExercises.add(
            RoutineExercise(
                routineId = activeRoutine?.id ?: "ad_hoc_session",
                exerciseId = "",
                exerciseName = name,
                targetSets = 3,
                targetRepsMin = 8,
                targetRepsMax = 12,
                orderIndex = nextOrder,
                notes = notes
            )
        )
    }

    fun markExerciseCompleted(exerciseName: String, restBetweenExercisesSeconds: Int = 120) {
        if (!completedExerciseNames.contains(exerciseName)) {
            completedExerciseNames.add(exerciseName)
        }
        if (activeRoutineExercises.isNotEmpty()) {
            val nextUnfinishedIndex = activeRoutineExercises.indexOfFirst { !completedExerciseNames.contains(it.exerciseName) }
            if (nextUnfinishedIndex != -1) {
                currentExerciseIndex = nextUnfinishedIndex
            } else if (currentExerciseIndex < activeRoutineExercises.size - 1) {
                currentExerciseIndex++
            }
        }
        startRestCountdown(restBetweenExercisesSeconds)
    }

    fun unmarkExerciseCompleted(exerciseName: String) {
        completedExerciseNames.remove(exerciseName)
    }

    fun selectActiveExercise(index: Int) {
        if (index in 0 until activeRoutineExercises.size) {
            currentExerciseIndex = index
        }
    }

    fun addWorkoutSet(
        exercise: String,
        reps: Int,
        weightKg: Double,
        weightType: String = "barbell",
        rir: Int? = null,
        durationSeconds: Int = 0,
        exerciseId: String = "",
        targetSessionId: Long? = null
    ) {
        viewModelScope.launch {
            val sid = targetSessionId ?: activeSessionId ?: run {
                val newId = dao.insertWorkoutSession(
                    WorkoutSession(date = today, dayName = "Strength & Conditioning", completed = false)
                )
                activeSessionId = newId
                newId
            }
            // Derive next set number within this specific exercise instance using max existing set number
            val maxSet = dao.getMaxSetNumber(sid, exerciseId, exercise) ?: 0
            val setNumber = maxSet + 1

            dao.insertWorkoutSet(
                WorkoutSet(
                    sessionId = sid,
                    exercise = exercise,
                    exerciseId = exerciseId,
                    setNumber = setNumber,
                    reps = reps,
                    weightKg = weightKg,
                    weightType = weightType,
                    rir = rir,
                    durationSeconds = durationSeconds,
                    completed = true
                )
            )
            refreshTodaySession()
        }
    }

    fun deleteWorkoutSet(setId: Long) {
        viewModelScope.launch {
            dao.deleteWorkoutSet(setId)
            refreshTodaySession()
        }
    }

    // Workout Duration Timer
    fun startWorkoutTimer() {
        if (isWorkoutTimerRunning) return
        isWorkoutTimerRunning = true
        workoutTimerJob?.cancel()
        workoutTimerJob = viewModelScope.launch {
            while (isWorkoutTimerRunning) {
                delay(1000)
                workoutDurationSeconds++
            }
        }
    }

    fun pauseWorkoutTimer() {
        isWorkoutTimerRunning = false
        workoutTimerJob?.cancel()
    }

    fun resumeWorkoutTimer() {
        startWorkoutTimer()
    }

    fun finishWorkoutSession() {
        pauseWorkoutTimer()
        val minutes = ((workoutDurationSeconds + 30) / 60).coerceAtLeast(1)
        viewModelScope.launch {
            val sid = activeSessionId
            val session = if (sid != null) dao.getWorkoutSessionById(sid) else dao.getWorkoutSession(today)
            if (session != null) {
                dao.updateWorkoutSession(session.copy(durationMinutes = minutes, completed = true))
            } else {
                val newId = dao.insertWorkoutSession(
                    WorkoutSession(date = today, dayName = "Workout Session", durationMinutes = minutes, completed = true)
                )
                activeSessionId = newId
            }
            refreshTodaySession()
        }
    }

    // User-controlled Database Backup & Safe Restore
    var backupStatusMessage by mutableStateOf<String?>(null)
        private set

    fun setBackupStatus(msg: String?) {
        backupStatusMessage = msg
    }

    suspend fun exportDatabaseBackup(): String {
        return FitForgeBackupManager.exportBackupToJson(dao)
    }

    fun restoreDatabaseBackup(jsonString: String) {
        viewModelScope.launch {
            FitForgeBackupManager.restoreBackupFromJson(dao, jsonString)
                .onSuccess { summary ->
                    backupStatusMessage = "Restored: ${summary.foodLogsRestored} foods, ${summary.workoutSetsRestored} sets, ${summary.bodyEntriesRestored} weigh-ins."
                    refreshTodaySession()
                }
                .onFailure { err ->
                    backupStatusMessage = "Restore failed: ${err.localizedMessage ?: "Invalid backup file"}"
                }
        }
    }

    // Active Set Duration Timer
    fun startSetTimer() {
        activeSetDurationSeconds = 0
        isSetTimerRunning = true
        setTimerJob?.cancel()
        setTimerJob = viewModelScope.launch {
            while (isSetTimerRunning) {
                delay(1000)
                activeSetDurationSeconds++
            }
        }
    }

    fun stopSetTimer(): Int {
        val seconds = activeSetDurationSeconds
        isSetTimerRunning = false
        setTimerJob?.cancel()
        return seconds
    }

    fun resetSetTimer() {
        isSetTimerRunning = false
        setTimerJob?.cancel()
        activeSetDurationSeconds = 0
    }

    // Rest Countdown Timer
    fun startRestCountdown(seconds: Int = restTotalTargetSeconds) {
        val target = if (seconds > 0) seconds else 90
        restTotalTargetSeconds = target
        restSecondsRemaining = target
        isRestTimerRunning = true
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (restSecondsRemaining > 0 && isRestTimerRunning) {
                delay(1000)
                restSecondsRemaining--
            }
            isRestTimerRunning = false
        }
    }

    fun addRestSeconds(seconds: Int) {
        restSecondsRemaining = (restSecondsRemaining + seconds).coerceAtLeast(0)
        if (!isRestTimerRunning && restSecondsRemaining > 0) {
            isRestTimerRunning = true
            restTimerJob = viewModelScope.launch {
                while (restSecondsRemaining > 0 && isRestTimerRunning) {
                    delay(1000)
                    restSecondsRemaining--
                }
                isRestTimerRunning = false
            }
        }
    }

    fun skipRestCountdown() {
        isRestTimerRunning = false
        restTimerJob?.cancel()
        restSecondsRemaining = 0
    }

    // Schedule & Custom Routine Management
    fun updateDaySchedule(
        dayOfWeek: Int,
        dayName: String,
        routineId: String?,
        routineName: String?,
        isRestDay: Boolean,
        targetDurationMinutes: Int,
        defaultRestSeconds: Int
    ) {
        viewModelScope.launch {
            dao.insertOrUpdateSchedule(
                WorkoutSchedule(
                    dayOfWeek = dayOfWeek,
                    dayName = dayName,
                    routineId = routineId,
                    routineName = routineName,
                    isRestDay = isRestDay,
                    targetDurationMinutes = targetDurationMinutes,
                    defaultRestSeconds = defaultRestSeconds
                )
            )
        }
    }

    fun saveCustomRoutine(
        name: String,
        splitCategory: String,
        description: String,
        exercises: List<RoutineExercise>
    ) {
        viewModelScope.launch {
            val routineId = "custom_routine_${System.currentTimeMillis()}"
            val routine = WorkoutRoutine(
                id = routineId,
                name = name,
                splitCategory = splitCategory,
                description = description
            )
            dao.insertWorkoutRoutines(listOf(routine))
            val routineExercises = exercises.mapIndexed { idx, item ->
                item.copy(routineId = routineId, orderIndex = idx + 1)
            }
            dao.insertRoutineExercises(routineExercises)
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            dao.deleteWorkoutRoutine(routineId)
            dao.deleteRoutineExercises(routineId)
            dao.getWeeklyScheduleList().filter { it.routineId == routineId }.forEach { s ->
                dao.insertOrUpdateSchedule(s.copy(routineId = null, routineName = null))
            }
        }
    }

    fun logRetroactiveWorkout(
        date: String,
        dayName: String,
        durationMinutes: Int,
        sets: List<RetroactiveSetEntry>
    ) {
        viewModelScope.launch {
            var session = dao.getWorkoutSession(date)
            if (session == null) {
                val sid = dao.insertWorkoutSession(
                    WorkoutSession(
                        date = date,
                        dayName = dayName,
                        durationMinutes = durationMinutes,
                        completed = true
                    )
                )
                session = dao.getWorkoutSession(date)
            } else {
                dao.updateWorkoutSession(
                    session.copy(
                        completed = true,
                        durationMinutes = durationMinutes,
                        dayName = if (session.dayName.isBlank()) dayName else session.dayName
                    )
                )
            }
            val sid = session?.id ?: return@launch
            dao.deleteWorkoutSetsForSession(sid)
            val exerciseSetCounts = mutableMapOf<String, Int>()

            sets.forEach { setEntry ->
                val count = exerciseSetCounts.getOrDefault(setEntry.exercise.lowercase(), 0) + 1
                exerciseSetCounts[setEntry.exercise.lowercase()] = count
                dao.insertWorkoutSet(
                    WorkoutSet(
                        sessionId = sid,
                        exercise = setEntry.exercise,
                        setNumber = count,
                        reps = setEntry.reps,
                        weightKg = setEntry.weightKg,
                        weightType = setEntry.weightType,
                        rir = setEntry.rir,
                        durationSeconds = setEntry.durationSeconds,
                        completed = true
                    )
                )
            }
            refreshTodaySession()
        }
    }

    suspend fun getExistingSetsForDate(date: String): List<WorkoutSet> {
        val session = dao.getWorkoutSession(date) ?: return emptyList()
        return dao.getWorkoutSets(session.id)
    }

    // wger Online Integration Methods
    fun updateWgerApiKey(newKey: String) {
        val trimmed = newKey.trim()
        wgerApiKey = trimmed
        prefs.edit().putString("wger_api_key", trimmed).apply()
    }

    fun searchWger(query: String, categoryFilter: String? = null) {
        wgerSearchJob?.cancel()
        wgerSearchJob = viewModelScope.launch {
            isSearchingWger = true
            wgerError = null
            val result = WgerApiClient.searchExercises(query, categoryFilter)
            result.onSuccess { list ->
                wgerExercises = list
                isSearchingWger = false
            }.onFailure { err ->
                wgerError = err.message ?: "Failed to connect to wger"
                isSearchingWger = false
            }
        }
    }

    fun importWgerExercise(exercise: Exercise, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            dao.insertExercises(listOf(exercise))
            onDone?.invoke()
        }
    }

    fun fetchWgerRoutines() {
        viewModelScope.launch {
            isFetchingWgerRoutines = true
            val result = WgerApiClient.fetchRoutines(wgerApiKey.ifBlank { null })
            result.onSuccess { bundles ->
                wgerRoutines = bundles
                isFetchingWgerRoutines = false
            }.onFailure {
                isFetchingWgerRoutines = false
            }
        }
    }

    fun importWgerRoutine(bundle: WgerRoutineBundle, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            dao.insertWorkoutRoutines(listOf(bundle.routine))
            if (bundle.exercises.isNotEmpty()) {
                dao.insertRoutineExercises(bundle.exercises)
            }
            onDone?.invoke()
        }
    }

    // ChatGPT AI Coach Methods
    fun startChatGptAuth() {
        isChatGptAuthenticating = true
        authStatusMessage = "Opening browser for Sign in with ChatGPT..."
        viewModelScope.launch {
            val result = ChatGptAuthManager.startSignInFlow(
                context = context,
                onBrowserLaunch = { browserIntent ->
                    try {
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        authStatusMessage = "Failed to launch browser: ${e.localizedMessage}"
                    }
                }
            )

            isChatGptAuthenticating = false
            hasPendingAuth = ChatGptAuthManager.hasPendingAuth(context)
            result.onSuccess { session ->
                chatGptSession = session
                authStatusMessage = "Successfully connected as ${session.email}!"
                loadChatGptModels()
            }.onFailure { error ->
                authStatusMessage = "Authorization failed: ${error.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun retryPendingAuth() {
        isChatGptAuthenticating = true
        authStatusMessage = "Retrying token exchange with ChatGPT..."
        viewModelScope.launch {
            val result = ChatGptAuthManager.retryPendingTokenExchange(context)
            isChatGptAuthenticating = false
            hasPendingAuth = ChatGptAuthManager.hasPendingAuth(context)
            result.onSuccess { session ->
                chatGptSession = session
                authStatusMessage = "Successfully connected as ${session.email}!"
                loadChatGptModels()
            }.onFailure { error ->
                authStatusMessage = "Exchange failed: ${error.localizedMessage ?: "Network error"}"
            }
        }
    }

    fun completeWithManualCallback(urlOrCode: String) {
        val trimmed = urlOrCode.trim()
        if (trimmed.isBlank()) return
        isChatGptAuthenticating = true
        authStatusMessage = "Exchanging authorization code..."
        viewModelScope.launch {
            val result = ChatGptAuthManager.completeWithCallbackUrl(context, trimmed)
            isChatGptAuthenticating = false
            hasPendingAuth = ChatGptAuthManager.hasPendingAuth(context)
            result.onSuccess { session ->
                chatGptSession = session
                authStatusMessage = "Successfully connected as ${session.email}!"
                loadChatGptModels()
            }.onFailure { error ->
                authStatusMessage = "Exchange failed: ${error.localizedMessage ?: "Invalid code or URL"}"
            }
        }
    }

    fun loadChatGptModels() {
        viewModelScope.launch {
            val session = chatGptSession ?: return@launch
            isLoadingModels = true
            val token = ChatGptAuthManager.getValidAccessToken(context)
            if (token == null) {
                isLoadingModels = false
                signOutChatGpt()
                authStatusMessage = "Session expired. Please reconnect ChatGPT."
                return@launch
            }
            val res = ChatGptClient.fetchAvailableModels(token)
            isLoadingModels = false
            res.onSuccess { models ->
                availableChatGptModels = models
                if (models.isNotEmpty()) {
                    val current = selectedChatGptModel
                    if (current == null || !models.any { it.slug == current }) {
                        selectedChatGptModel = models.first().slug
                    }
                }
            }
        }
    }

    fun signOutChatGpt() {
        ChatGptAuthManager.signOut(context)
        chatGptSession = null
        authStatusMessage = null
        hasPendingAuth = false
        availableChatGptModels = emptyList()
        selectedChatGptModel = null
    }

    fun toggleAttachPhoto() {
        attachProgressPhoto = !attachProgressPhoto
    }

    fun clearCoachChat() {
        coachJob?.cancel()
        isCoachStreaming = false
        coachMessages = emptyList()
        coachError = null
        prefs.edit().remove("chatgpt_coach_messages_json").apply()
    }

    fun buildFitnessContext(): String {
        return buildString {
            appendLine("Date: $today")

            // 1. Body Measurements (Privacy filtered & local file paths removed)
            if (shareBodyContext) {
                val body = latestBody.value
                if (body != null) {
                    appendLine("Body Measurements: Weight ${body.weightKg} kg" +
                        (body.waistCm?.let { ", Waist $it cm" } ?: "") +
                        (body.neckCm?.let { ", Neck $it cm" } ?: "") +
                        (body.chestCm?.let { ", Chest $it cm" } ?: ""))
                } else {
                    appendLine("Body Measurements: No recent body measurement logged today.")
                }
            }

            // 2. Nutrition Logs (Privacy filtered)
            if (shareNutritionContext) {
                val currentLogs = todayLogs.value
                val kcal = currentLogs.sumOf { it.kcal }
                val protein = currentLogs.sumOf { it.protein }
                val carbs = currentLogs.sumOf { it.carbs }
                val fat = currentLogs.sumOf { it.fat }
                val fiber = currentLogs.sumOf { it.fiber }

                appendLine("Nutrition Today: Total Calories: ${kcal.toInt()} kcal (Target: ${DEFAULT_CALORIE_TARGET.toInt()}), Protein: ${protein.toInt()}g (Target: ${DEFAULT_PROTEIN_TARGET.toInt()}), Carbs: ${carbs.toInt()}g, Fat: ${fat.toInt()}g, Fiber: ${fiber.toInt()}g")
                appendLine("Meals Logged:")
                for (m in MEAL_TYPES) {
                    val mealItems = currentLogs.filter { it.mealType == m }
                    if (mealItems.isNotEmpty()) {
                        val mKcal = mealItems.sumOf { it.kcal }.toInt()
                        val mProt = mealItems.sumOf { it.protein }.toInt()
                        val names = mealItems.joinToString(", ") { "${it.foodName} (${formatFoodPortion(it)})" }
                        appendLine("  • $m ($mKcal kcal, ${mProt}g P): $names")
                    } else {
                        appendLine("  • $m: None logged")
                    }
                }
            }

            // 3. Workout Sessions & Sets (Privacy filtered)
            if (shareWorkoutContext) {
                val session = todaySession.value
                val sets = todaySets.value
                if (session != null) {
                    appendLine("Today's Workout: ${session.dayName}, Duration: ${session.durationMinutes} min, Burned: ${session.estimatedCaloriesBurned.toInt()} kcal (~est), Completed: ${session.completed}")
                    if (sets.isNotEmpty()) {
                        appendLine("Sets Logged (${sets.size} total):")
                        sets.groupBy { it.exercise }.forEach { (ex, sList) ->
                            val setsStr = sList.joinToString(", ") { "${it.weightKg}kg x ${it.reps} reps" }
                            appendLine("  • $ex: $setsStr")
                        }
                    }
                } else {
                    appendLine("Today's Workout: None logged yet.")
                }
            }
        }
    }

    fun askCoach(userPrompt: String) {
        val prompt = userPrompt.trim()
        if (prompt.isBlank() || isCoachStreaming) return

        // Explicit user consent check before sending data remotely to OpenAI
        if (!hasAiConsent) {
            showAiConsentDialog = true
            return
        }

        val session = chatGptSession ?: return
        val contextSummary = buildFitnessContext()
        val photosToAttach = if (attachProgressPhoto) {
            latestBody.value?.let { b ->
                listOfNotNull(b.photoPath, b.photoPathBack, b.photoPathSide).filter { it.isNotBlank() }
            } ?: emptyList()
        } else emptyList()

        // Opt-in per-request: reset immediately so subsequent messages do not automatically re-upload photo
        attachProgressPhoto = false

        val userMessage = CoachMessage(
            isUser = true,
            text = prompt,
            hasPhotoAttached = photosToAttach.isNotEmpty()
        )
        val placeholder = CoachMessage(
            isUser = false,
            text = ""
        )

        coachMessages = coachMessages + userMessage + placeholder
        persistCoachMessages(coachMessages)
        isCoachStreaming = true
        coachError = null

        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            val token = ChatGptAuthManager.getValidAccessToken(context)
            if (token == null) {
                isCoachStreaming = false
                signOutChatGpt()
                coachError = "Your ChatGPT session has expired. Please sign in again in AI Coach."
                // Remove empty placeholder message
                if (coachMessages.isNotEmpty() && !coachMessages.last().isUser) {
                    coachMessages = coachMessages.dropLast(1)
                }
                return@launch
            }
            var modelToUse = selectedChatGptModel
            if (modelToUse.isNullOrBlank()) {
                val modelsRes = ChatGptClient.fetchAvailableModels(token)
                modelsRes.onSuccess { list ->
                    availableChatGptModels = list
                    modelToUse = list.firstOrNull()?.slug
                    selectedChatGptModel = modelToUse
                }
            }
            var accumulatedText = ""

            val result = ChatGptClient.streamCoachResponse(
                context = context,
                accessToken = token,
                userPrompt = prompt,
                fitnessContext = contextSummary,
                photoPaths = photosToAttach,
                conversationHistory = coachMessages.dropLast(2),
                modelSlug = modelToUse,
                onDelta = { delta ->
                    accumulatedText += delta
                    val updatedList = coachMessages.toMutableList()
                    if (updatedList.isNotEmpty() && !updatedList.last().isUser) {
                        updatedList[updatedList.lastIndex] = updatedList.last().copy(text = accumulatedText)
                        coachMessages = updatedList
                    }
                }
            )

            isCoachStreaming = false
            if (result.isFailure) {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to get AI coach response"
                coachError = err
                val updatedList = coachMessages.toMutableList()
                if (updatedList.isNotEmpty() && !updatedList.last().isUser) {
                    if (accumulatedText.isNotBlank()) {
                        updatedList[updatedList.lastIndex] = updatedList.last().copy(
                            text = "$accumulatedText\n\n⚠️ Error during response: $err"
                        )
                    } else {
                        // Remove empty placeholder rather than leaving a confusing blank bubble
                        updatedList.removeAt(updatedList.lastIndex)
                    }
                    coachMessages = updatedList
                    persistCoachMessages(coachMessages)
                }
            } else {
                persistCoachMessages(coachMessages)
                if (!isChatGptScreenVisible && accumulatedText.isNotBlank()) {
                    com.fitforge.ishita.service.CoachNotificationHelper.showResponseNotification(
                        context = context,
                        coachTitle = "AI Coach (ChatGPT)",
                        previewText = accumulatedText,
                        engine = "CHATGPT"
                    )
                }
            }
        }
    }

    class Factory(
        private val dao: FitForgeDao,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FitForgeViewModel(dao, context) as T
    }
}

