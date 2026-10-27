package com.fitforge.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object ExerciseAssetImporter {
    private const val TAG = "ExerciseAssetImporter"
    private const val ASSET_FILENAME = "exercises.json"

    private val MUSCLE_GROUP_MAP = mapOf(
        "chest" to "Chest",
        "middle back" to "Back",
        "lower back" to "Back",
        "lats" to "Back",
        "traps" to "Back",
        "quadriceps" to "Legs",
        "hamstrings" to "Legs",
        "glutes" to "Legs",
        "calves" to "Legs",
        "abductors" to "Legs",
        "adductors" to "Legs",
        "shoulders" to "Shoulders",
        "neck" to "Shoulders",
        "biceps" to "Arms",
        "triceps" to "Arms",
        "forearms" to "Arms",
        "abdominals" to "Core"
    )

    private val EQUIPMENT_DISPLAY_MAP = mapOf(
        "body only" to "Bodyweight",
        "barbell" to "Barbell",
        "dumbbell" to "Dumbbell",
        "cable" to "Cable",
        "machine" to "Machine",
        "kettlebells" to "Kettlebell",
        "e-z curl bar" to "EZ Bar",
        "bands" to "Resistance Bands",
        "medicine ball" to "Medicine Ball",
        "exercise ball" to "Exercise Ball",
        "foam roll" to "Foam Roller",
        "other" to "Other"
    )

    /**
     * Reads and parses all exercises from assets/exercises.json.
     * Returns a list of [Exercise] entities ready for Room insertion.
     */
    suspend fun loadFromAssets(context: Context): List<Exercise> = withContext(Dispatchers.IO) {
        val exercises = mutableListOf<Exercise>()
        try {
            val jsonString = context.assets.open(ASSET_FILENAME).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }

            val jsonArray = JSONArray(jsonString)
            val count = jsonArray.length()

            for (i in 0 until count) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "ex_$i")
                val name = obj.optString("name", "Exercise $i")
                val category = obj.optString("category", "strength").lowercase()
                val equipmentRaw = obj.optString("equipment", "other").lowercase()
                val equipment = EQUIPMENT_DISPLAY_MAP[equipmentRaw] ?: equipmentRaw.replaceFirstChar { it.uppercase() }

                // Determine primary muscle group
                val primaryMusclesArr = obj.optJSONArray("primaryMuscles")
                val primaryMuscleRaw = if (primaryMusclesArr != null && primaryMusclesArr.length() > 0) {
                    primaryMusclesArr.optString(0, "").lowercase()
                } else ""

                val muscleGroup = when {
                    category == "cardio" -> "Cardio"
                    MUSCLE_GROUP_MAP.containsKey(primaryMuscleRaw) -> MUSCLE_GROUP_MAP[primaryMuscleRaw]!!
                    primaryMuscleRaw.isNotBlank() -> primaryMuscleRaw.replaceFirstChar { it.uppercase() }
                    else -> "Full Body"
                }

                // Secondary muscles
                val secondaryMusclesArr = obj.optJSONArray("secondaryMuscles")
                val secondaryMuscles = if (secondaryMusclesArr != null) {
                    val list = mutableListOf<String>()
                    for (j in 0 until secondaryMusclesArr.length()) {
                        val m = secondaryMusclesArr.optString(j, "")
                        if (m.isNotBlank()) list.add(m.replaceFirstChar { it.uppercase() })
                    }
                    list.joinToString(", ")
                } else ""

                // Instructions
                val instructionsArr = obj.optJSONArray("instructions")
                val instructions = if (instructionsArr != null && instructionsArr.length() > 0) {
                    val steps = mutableListOf<String>()
                    for (k in 0 until instructionsArr.length()) {
                        val step = instructionsArr.optString(k, "").trim()
                        if (step.isNotBlank()) steps.add("${k + 1}. $step")
                    }
                    steps.joinToString("\n")
                } else {
                    "Perform movement with controlled tempo and full range of motion."
                }

                // Measurement Type
                val measurementType = when {
                    category == "cardio" || name.contains("Running", ignoreCase = true) || name.contains("Cycling", ignoreCase = true) -> "time"
                    equipmentRaw == "body only" -> "bodyweight_reps"
                    else -> "weight_reps"
                }

                // MET value for calorie burn estimates
                val metValue = when (category) {
                    "cardio" -> 7.5
                    "plyometrics" -> 6.5
                    "powerlifting", "olympic weightlifting", "strongman" -> 6.0
                    "stretching" -> 2.5
                    else -> if (equipmentRaw == "body only") 4.5 else 5.5
                }

                exercises.add(
                    Exercise(
                        id = id,
                        name = name,
                        muscleGroup = muscleGroup,
                        secondaryMuscles = secondaryMuscles,
                        equipment = equipment,
                        measurementType = measurementType,
                        instructions = instructions,
                        metValue = metValue,
                        source = "Free Exercise DB"
                    )
                )
            }
            Log.d(TAG, "Successfully parsed ${exercises.size} exercises from $ASSET_FILENAME")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing exercises from assets: ${e.message}", e)
        }
        exercises
    }
}
