package com.fitforge.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupRestoreSummary(
    val foodLogsRestored: Int,
    val bodyEntriesRestored: Int,
    val workoutSessionsRestored: Int,
    val workoutSetsRestored: Int,
    val recipesRestored: Int,
    val routinesRestored: Int,
    val timestamp: Long
)

object FitForgeBackupManager {
    const val BACKUP_SCHEMA_VERSION = 1

    /**
     * Exports all user tracking history and custom workout/recipe data to a versioned JSON string.
     */
    suspend fun exportBackupToJson(dao: FitForgeDao): String {
        val root = JSONObject()
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "FitForge")

        // 1. Food Logs
        val foodLogsArray = JSONArray()
        dao.getAllFoodLogs().forEach { log ->
            val obj = JSONObject()
            obj.put("id", log.id)
            obj.put("date", log.date)
            obj.put("mealType", log.mealType)
            obj.put("foodId", log.foodId)
            obj.put("foodName", log.foodName)
            obj.put("grams", log.grams)
            obj.put("quantity", log.quantity)
            obj.put("unit", log.unit)
            obj.put("kcal", log.kcal)
            obj.put("protein", log.protein)
            obj.put("carbs", log.carbs)
            obj.put("fat", log.fat)
            obj.put("fiber", log.fiber)
            foodLogsArray.put(obj)
        }
        root.put("foodLogs", foodLogsArray)

        // 2. Body Entries
        val bodyArray = JSONArray()
        dao.getAllBodyEntries().forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("date", b.date)
            obj.put("weightKg", b.weightKg)
            if (b.waistCm != null) obj.put("waistCm", b.waistCm)
            if (b.photoPath != null) obj.put("photoPath", b.photoPath)
            if (b.photoPathBack != null) obj.put("photoPathBack", b.photoPathBack)
            if (b.photoPathSide != null) obj.put("photoPathSide", b.photoPathSide)
            bodyArray.put(obj)
        }
        root.put("bodyEntries", bodyArray)

        // 3. Workout Sessions
        val sessionArray = JSONArray()
        dao.getAllWorkoutSessions().forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("date", s.date)
            obj.put("dayName", s.dayName)
            obj.put("durationMinutes", s.durationMinutes)
            obj.put("distanceKm", s.distanceKm)
            obj.put("estimatedCaloriesBurned", s.estimatedCaloriesBurned)
            obj.put("completed", s.completed)
            obj.put("notes", s.notes)
            sessionArray.put(obj)
        }
        root.put("workoutSessions", sessionArray)

        // 4. Workout Sets
        val setArray = JSONArray()
        dao.getAllWorkoutSets().forEach { set ->
            val obj = JSONObject()
            obj.put("id", set.id)
            obj.put("sessionId", set.sessionId)
            obj.put("exercise", set.exercise)
            obj.put("exerciseId", set.exerciseId)
            obj.put("setNumber", set.setNumber)
            obj.put("reps", set.reps)
            obj.put("weightKg", set.weightKg)
            obj.put("weightType", set.weightType)
            if (set.rir != null) obj.put("rir", set.rir)
            obj.put("durationSeconds", set.durationSeconds)
            obj.put("completed", set.completed)
            setArray.put(obj)
        }
        root.put("workoutSets", setArray)

        // 5. Custom Recipes & Ingredients
        val recipeArray = JSONArray()
        dao.getAllRecipes().forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("servings", r.servings)
            obj.put("instructions", r.instructions)
            recipeArray.put(obj)
        }
        root.put("recipes", recipeArray)

        val ingredientArray = JSONArray()
        dao.getAllRecipeIngredients().forEach { ing ->
            val obj = JSONObject()
            obj.put("id", ing.id)
            obj.put("recipeId", ing.recipeId)
            obj.put("foodId", ing.foodId)
            obj.put("foodName", ing.foodName)
            obj.put("grams", ing.grams)
            obj.put("kcal", ing.kcal)
            obj.put("protein", ing.protein)
            obj.put("carbs", ing.carbs)
            obj.put("fat", ing.fat)
            obj.put("fiber", ing.fiber)
            ingredientArray.put(obj)
        }
        root.put("recipeIngredients", ingredientArray)

        // 6. Custom Routines
        val routineArray = JSONArray()
        dao.getAllWorkoutRoutines().forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("description", r.description)
            obj.put("splitCategory", r.splitCategory)
            routineArray.put(obj)
        }
        root.put("workoutRoutines", routineArray)

        val routineExerciseArray = JSONArray()
        dao.getAllRoutineExercises().forEach { re ->
            val obj = JSONObject()
            obj.put("id", re.id)
            obj.put("routineId", re.routineId)
            obj.put("exerciseId", re.exerciseId)
            obj.put("exerciseName", re.exerciseName)
            obj.put("targetSets", re.targetSets)
            obj.put("targetRepsMin", re.targetRepsMin)
            obj.put("targetRepsMax", re.targetRepsMax)
            obj.put("orderIndex", re.orderIndex)
            obj.put("notes", re.notes)
            routineExerciseArray.put(obj)
        }
        root.put("routineExercises", routineExerciseArray)

        return root.toString(2)
    }

    /**
     * Restores structured data from a JSON backup without destructive wipes.
     */
    suspend fun restoreBackupFromJson(dao: FitForgeDao, jsonString: String): Result<BackupRestoreSummary> = runCatching {
        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schemaVersion", 1)
        if (schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup version $schemaVersion. Please update FitForge.")
        }

        var foodLogsCount = 0
        if (root.has("foodLogs")) {
            val arr = root.getJSONArray("foodLogs")
            val list = mutableListOf<FoodLog>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FoodLog(
                        id = o.optLong("id", 0L),
                        date = o.getString("date"),
                        mealType = o.optString("mealType", "Snack"),
                        foodId = o.optString("foodId", ""),
                        foodName = o.getString("foodName"),
                        grams = o.optDouble("grams", 100.0),
                        quantity = o.optDouble("quantity", 0.0),
                        unit = o.optString("unit", "g"),
                        kcal = o.optDouble("kcal", 0.0),
                        protein = o.optDouble("protein", 0.0),
                        carbs = o.optDouble("carbs", 0.0),
                        fat = o.optDouble("fat", 0.0),
                        fiber = o.optDouble("fiber", 0.0)
                    )
                )
            }
            dao.restoreFoodLogs(list)
            foodLogsCount = list.size
        }

        var bodyEntriesCount = 0
        if (root.has("bodyEntries")) {
            val arr = root.getJSONArray("bodyEntries")
            val list = mutableListOf<BodyEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    BodyEntry(
                        id = o.optLong("id", 0L),
                        date = o.getString("date"),
                        weightKg = o.getDouble("weightKg"),
                        waistCm = if (o.has("waistCm")) o.getDouble("waistCm") else null,
                        photoPath = if (o.has("photoPath")) o.getString("photoPath") else null,
                        photoPathBack = if (o.has("photoPathBack")) o.getString("photoPathBack") else null,
                        photoPathSide = if (o.has("photoPathSide")) o.getString("photoPathSide") else null
                    )
                )
            }
            dao.restoreBodyEntries(list)
            bodyEntriesCount = list.size
        }

        var sessionCount = 0
        if (root.has("workoutSessions")) {
            val arr = root.getJSONArray("workoutSessions")
            val list = mutableListOf<WorkoutSession>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WorkoutSession(
                        id = o.optLong("id", 0L),
                        date = o.getString("date"),
                        dayName = o.getString("dayName"),
                        durationMinutes = o.optInt("durationMinutes", 0),
                        distanceKm = o.optDouble("distanceKm", 0.0),
                        estimatedCaloriesBurned = o.optDouble("estimatedCaloriesBurned", 0.0),
                        completed = o.optBoolean("completed", true),
                        notes = o.optString("notes", "")
                    )
                )
            }
            dao.restoreWorkoutSessions(list)
            sessionCount = list.size
        }

        var setCount = 0
        if (root.has("workoutSets")) {
            val arr = root.getJSONArray("workoutSets")
            val list = mutableListOf<WorkoutSet>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WorkoutSet(
                        id = o.optLong("id", 0L),
                        sessionId = o.getLong("sessionId"),
                        exercise = o.getString("exercise"),
                        exerciseId = o.optString("exerciseId", ""),
                        setNumber = o.optInt("setNumber", 1),
                        reps = o.optInt("reps", 10),
                        weightKg = o.optDouble("weightKg", 0.0),
                        weightType = o.optString("weightType", "barbell"),
                        rir = if (o.has("rir")) o.getInt("rir") else null,
                        durationSeconds = o.optInt("durationSeconds", 0),
                        completed = o.optBoolean("completed", true)
                    )
                )
            }
            dao.restoreWorkoutSets(list)
            setCount = list.size
        }

        var recipeCount = 0
        if (root.has("recipes")) {
            val arr = root.getJSONArray("recipes")
            val list = mutableListOf<Recipe>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Recipe(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        servings = o.optDouble("servings", 1.0),
                        instructions = o.optString("instructions", "")
                    )
                )
            }
            dao.insertRecipes(list)
            recipeCount = list.size
        }

        if (root.has("recipeIngredients")) {
            val arr = root.getJSONArray("recipeIngredients")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertRecipeIngredient(
                    RecipeIngredient(
                        id = o.optLong("id", 0L),
                        recipeId = o.getString("recipeId"),
                        foodId = o.optString("foodId", ""),
                        foodName = o.getString("foodName"),
                        grams = o.optDouble("grams", 100.0),
                        kcal = o.optDouble("kcal", 0.0),
                        protein = o.optDouble("protein", 0.0),
                        carbs = o.optDouble("carbs", 0.0),
                        fat = o.optDouble("fat", 0.0),
                        fiber = o.optDouble("fiber", 0.0)
                    )
                )
            }
        }

        var routineCount = 0
        if (root.has("workoutRoutines")) {
            val arr = root.getJSONArray("workoutRoutines")
            val list = mutableListOf<WorkoutRoutine>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WorkoutRoutine(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        splitCategory = o.optString("splitCategory", "General")
                    )
                )
            }
            dao.insertWorkoutRoutines(list)
            routineCount = list.size
        }

        if (root.has("routineExercises")) {
            val arr = root.getJSONArray("routineExercises")
            val list = mutableListOf<RoutineExercise>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    RoutineExercise(
                        id = o.optLong("id", 0L),
                        routineId = o.getString("routineId"),
                        exerciseId = o.optString("exerciseId", ""),
                        exerciseName = o.getString("exerciseName"),
                        targetSets = o.optInt("targetSets", 3),
                        targetRepsMin = o.optInt("targetRepsMin", 8),
                        targetRepsMax = o.optInt("targetRepsMax", 12),
                        orderIndex = o.optInt("orderIndex", i + 1),
                        notes = o.optString("notes", "")
                    )
                )
            }
            dao.insertRoutineExercises(list)
        }

        BackupRestoreSummary(
            foodLogsRestored = foodLogsCount,
            bodyEntriesRestored = bodyEntriesCount,
            workoutSessionsRestored = sessionCount,
            workoutSetsRestored = setCount,
            recipesRestored = recipeCount,
            routinesRestored = routineCount,
            timestamp = System.currentTimeMillis()
        )
    }
}
