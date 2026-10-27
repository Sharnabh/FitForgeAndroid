package com.fitforge.data.api

import android.util.Log
import com.fitforge.data.Exercise
import com.fitforge.data.RoutineExercise
import com.fitforge.data.WorkoutRoutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WgerRoutineBundle(
    val routine: WorkoutRoutine,
    val exercises: List<RoutineExercise>
)

object WgerApiClient {
    private const val TAG = "WgerApiClient"
    private const val BASE_URL = "https://wger.de/api/v2"
    private const val TIMEOUT_MS = 10000
    private const val USER_AGENT = "FitForge - Android - WorkoutTracker/1.2"

    // wger category IDs:
    // 10: Abs, 8: Arms, 12: Back, 14: Calves, 15: Cardio, 11: Chest, 9: Legs, 13: Shoulders
    val CATEGORY_NAME_TO_ID = mapOf(
        "Chest" to 11,
        "Back" to 12,
        "Legs" to 9,
        "Calves" to 14,
        "Shoulders" to 13,
        "Arms" to 8,
        "Core" to 10,
        "Abs" to 10,
        "Cardio" to 15
    )

    private val WGER_CATEGORY_ID_TO_MUSCLE_GROUP = mapOf(
        11 to "Chest",
        12 to "Back",
        9 to "Legs",
        14 to "Legs",
        13 to "Shoulders",
        8 to "Arms",
        10 to "Core",
        15 to "Cardio"
    )

    /**
     * Searches or fetches exercises from wger online database.
     * Uses the public wger /exerciseinfo/ endpoint with English translations.
     */
    suspend fun searchExercises(
        query: String = "",
        categoryFilter: String? = null,
        limit: Int = 50
    ): Result<List<Exercise>> = withContext(Dispatchers.IO) {
        try {
            val catId = if (!categoryFilter.isNullOrBlank() && categoryFilter != "All") {
                CATEGORY_NAME_TO_ID[categoryFilter]
            } else null

            val urlBuilder = StringBuilder("$BASE_URL/exerciseinfo/?language=2&limit=$limit")
            if (catId != null) {
                urlBuilder.append("&category=$catId")
            }

            val url = URL(urlBuilder.toString())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("wger API returned HTTP $responseCode"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()

            val exercises = mutableListOf<Exercise>()
            val queryClean = query.trim().lowercase()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optInt("id", 0)
                if (id == 0) continue

                // Find English translation (language = 2) or fallback to first translation
                val translations = item.optJSONArray("translations") ?: JSONArray()
                var name: String? = null
                var descriptionHtml: String? = null

                for (tIdx in 0 until translations.length()) {
                    val tObj = translations.getJSONObject(tIdx)
                    val lang = tObj.optInt("language", 0)
                    if (lang == 2) {
                        name = tObj.optString("name", "")
                        descriptionHtml = tObj.optString("description", "")
                        break
                    }
                }

                if (name.isNullOrBlank() && translations.length() > 0) {
                    val fallback = translations.getJSONObject(0)
                    name = fallback.optString("name", "")
                    descriptionHtml = fallback.optString("description", "")
                }

                if (name.isNullOrBlank()) continue

                // Clean HTML tags from description
                val cleanDescription = descriptionHtml
                    ?.replace(Regex("<[^>]*>"), " ")
                    ?.replace("&nbsp;", " ")
                    ?.replace("&amp;", "&")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?: "Perform with controlled movement and full range of motion."

                // Filter by search query if present
                if (queryClean.isNotBlank()) {
                    val matches = name.lowercase().contains(queryClean) ||
                            cleanDescription.lowercase().contains(queryClean)
                    if (!matches) continue
                }

                // Category & Muscle Group
                val catObj = item.optJSONObject("category")
                val categoryId = catObj?.optInt("id", 0) ?: 0
                val muscleGroup = WGER_CATEGORY_ID_TO_MUSCLE_GROUP[categoryId]
                    ?: catObj?.optString("name", "Full Body")
                    ?: "Full Body"

                // Muscles (Primary)
                val musclesArr = item.optJSONArray("muscles") ?: JSONArray()
                val primaryMusclesList = mutableListOf<String>()
                for (m in 0 until musclesArr.length()) {
                    val mObj = musclesArr.getJSONObject(m)
                    val mName = mObj.optString("name_en", mObj.optString("name", ""))
                    if (mName.isNotBlank()) primaryMusclesList.add(mName)
                }

                // Muscles (Secondary)
                val secMusclesArr = item.optJSONArray("muscles_secondary") ?: JSONArray()
                val secMusclesList = mutableListOf<String>()
                for (m in 0 until secMusclesArr.length()) {
                    val mObj = secMusclesArr.getJSONObject(m)
                    val mName = mObj.optString("name_en", mObj.optString("name", ""))
                    if (mName.isNotBlank()) secMusclesList.add(mName)
                }
                val secondaryMuscles = secMusclesList.joinToString(", ")

                // Equipment
                val equipArr = item.optJSONArray("equipment") ?: JSONArray()
                val equipList = mutableListOf<String>()
                for (e in 0 until equipArr.length()) {
                    val eObj = equipArr.getJSONObject(e)
                    val eName = eObj.optString("name", "")
                    if (eName.isNotBlank()) equipList.add(eName)
                }
                val equipment = if (equipList.isNotEmpty()) equipList.joinToString(", ") else "Bodyweight"

                val isCardio = muscleGroup == "Cardio" || categoryId == 15
                val measurementType = when {
                    isCardio -> "time"
                    equipment.contains("Body", ignoreCase = true) -> "bodyweight_reps"
                    else -> "weight_reps"
                }

                val metValue = when {
                    isCardio -> 7.0
                    equipment.contains("Body", ignoreCase = true) -> 4.5
                    else -> 5.5
                }

                exercises.add(
                    Exercise(
                        id = "wger_$id",
                        name = name,
                        muscleGroup = muscleGroup,
                        secondaryMuscles = secondaryMuscles,
                        equipment = equipment,
                        measurementType = measurementType,
                        instructions = cleanDescription.ifBlank { "Perform with strict form and full contraction." },
                        metValue = metValue,
                        source = "wger"
                    )
                )
            }

            Log.d(TAG, "Fetched ${exercises.size} exercises from wger for query='$query', cat='$categoryFilter'")
            Result.success(exercises)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search wger: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches user's custom routines from wger if API key is provided,
     * or returns official wger community templates.
     */
    suspend fun fetchRoutines(apiKey: String? = null): Result<List<WgerRoutineBundle>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey?.trim()
        if (!trimmedKey.isNullOrBlank()) {
            try {
                val url = URL("$BASE_URL/routine/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Authorization", "Token $trimmedKey")
                    setRequestProperty("Accept", "application/json")
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val results = root.optJSONArray("results") ?: JSONArray()
                    val bundles = mutableListOf<WgerRoutineBundle>()

                    for (i in 0 until results.length()) {
                        val rObj = results.getJSONObject(i)
                        val id = rObj.optInt("id", i)
                        val name = rObj.optString("name", "wger Routine #$id")
                        val desc = rObj.optString("description", "Imported from your wger account")

                        val routine = WorkoutRoutine(
                            id = "wger_user_routine_$id",
                            name = name,
                            description = desc,
                            splitCategory = "wger Account"
                        )
                        bundles.add(WgerRoutineBundle(routine, emptyList()))
                    }
                    if (bundles.isNotEmpty()) {
                        return@withContext Result.success(bundles)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "User routine fetch failed, falling back to community routines: ${e.message}")
            }
        }

        // Return curated wger community routines
        Result.success(getCommunityWgerRoutines())
    }

    /**
     * Popular community routines built on wger exercise recommendations.
     */
    fun getCommunityWgerRoutines(): List<WgerRoutineBundle> {
        val p1 = WorkoutRoutine(
            id = "wger_routine_hypertrophy_4day",
            name = "wger 4-Day Hypertrophy Split",
            description = "High-volume hypertrophy routine inspired by the wger community database.",
            splitCategory = "wger Community"
        )
        val p1Exercises = listOf(
            RoutineExercise(routineId = p1.id, exerciseId = "wger_ex_1", exerciseName = "Bench Press", targetSets = 4, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 0),
            RoutineExercise(routineId = p1.id, exerciseId = "wger_ex_2", exerciseName = "Incline Dumbbell Press", targetSets = 3, targetRepsMin = 10, targetRepsMax = 12, orderIndex = 1),
            RoutineExercise(routineId = p1.id, exerciseId = "wger_ex_3", exerciseName = "Overhead Barbell Press", targetSets = 3, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 2),
            RoutineExercise(routineId = p1.id, exerciseId = "wger_ex_4", exerciseName = "Cable Triceps Pushdown", targetSets = 3, targetRepsMin = 12, targetRepsMax = 15, orderIndex = 3)
        )

        val p2 = WorkoutRoutine(
            id = "wger_routine_strength_5x5",
            name = "wger 5x5 Strength Foundation",
            description = "Classic compound 5x5 linear progression program for raw strength and power.",
            splitCategory = "wger Community"
        )
        val p2Exercises = listOf(
            RoutineExercise(routineId = p2.id, exerciseId = "wger_ex_5", exerciseName = "Barbell Squat", targetSets = 5, targetRepsMin = 5, targetRepsMax = 5, orderIndex = 0),
            RoutineExercise(routineId = p2.id, exerciseId = "wger_ex_6", exerciseName = "Barbell Bench Press", targetSets = 5, targetRepsMin = 5, targetRepsMax = 5, orderIndex = 1),
            RoutineExercise(routineId = p2.id, exerciseId = "wger_ex_7", exerciseName = "Barbell Bent-Over Row", targetSets = 5, targetRepsMin = 5, targetRepsMax = 5, orderIndex = 2)
        )

        val p3 = WorkoutRoutine(
            id = "wger_routine_calisthenics",
            name = "wger Bodyweight Calisthenics",
            description = "Zero-equipment full body routine utilizing bodyweight movements.",
            splitCategory = "wger Community"
        )
        val p3Exercises = listOf(
            RoutineExercise(routineId = p3.id, exerciseId = "wger_ex_8", exerciseName = "Push-ups", targetSets = 4, targetRepsMin = 12, targetRepsMax = 20, orderIndex = 0),
            RoutineExercise(routineId = p3.id, exerciseId = "wger_ex_9", exerciseName = "Pull-ups", targetSets = 4, targetRepsMin = 6, targetRepsMax = 12, orderIndex = 1),
            RoutineExercise(routineId = p3.id, exerciseId = "wger_ex_10", exerciseName = "Bodyweight Squat", targetSets = 4, targetRepsMin = 15, targetRepsMax = 25, orderIndex = 2),
            RoutineExercise(routineId = p3.id, exerciseId = "wger_ex_11", exerciseName = "Plank", targetSets = 3, targetRepsMin = 45, targetRepsMax = 60, orderIndex = 3)
        )

        return listOf(
            WgerRoutineBundle(p1, p1Exercises),
            WgerRoutineBundle(p2, p2Exercises),
            WgerRoutineBundle(p3, p3Exercises)
        )
    }
}
