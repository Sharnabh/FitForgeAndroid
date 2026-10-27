package com.fitforge.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val source: String = "FitForge starter dataset",
    val state: String = "as logged",
    val servingUnit: String = "g",
    val gramsPerPiece: Double = 100.0
)

@Entity(
    tableName = "food_logs",
    indices = [
        Index(value = ["date"]),
        Index(value = ["foodId"])
    ]
)
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String = "Snack", // "Breakfast", "Lunch", "Dinner", "Snack"
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val quantity: Double = 0.0, // e.g. 2.0 (for 2 rotis/slices) or 0.0 if logged by weight
    val unit: String = "g", // "slice", "roti", "piece", "egg", "cup", "scoop", "g"
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0
)

fun Food.getDefaultServingUnitAndWeight(): Pair<String, Double> {
    val lower = name.lowercase()
    return when {
        lower.contains("bread") || lower.contains("toast") -> "slice" to 30.0
        lower.contains("roti") || lower.contains("chapati") || lower.contains("phulka") -> "roti" to 40.0
        lower.contains("paratha") -> "paratha" to 80.0
        lower.contains("naan") -> "naan" to 90.0
        lower.contains("puri") || lower.contains("poori") -> "puri" to 35.0
        lower.contains("egg white") -> "egg white" to 33.0
        lower.contains("egg") -> "egg" to 50.0
        lower.contains("banana") -> "banana" to 118.0
        lower.contains("apple") -> "apple" to 180.0
        lower.contains("orange") -> "orange" to 130.0
        lower.contains("cookie") || lower.contains("biscuit") -> "piece" to 20.0
        lower.contains("idli") -> "idli" to 50.0
        lower.contains("dosa") -> "dosa" to 80.0
        lower.contains("scoop") || lower.contains("whey") || lower.contains("powder") -> "scoop" to 32.0
        lower.contains("peanut butter") || lower.contains("mayo") || lower.contains("butter") -> "tbsp" to 16.0
        lower.contains("sweet potato") || lower.contains("potato") -> "piece" to 150.0
        lower.contains("rice") || lower.contains("oat") || lower.contains("lentil") || lower.contains("dal") || lower.contains("curd") || lower.contains("yogurt") -> "cup" to 150.0
        lower.contains("milk") -> "glass" to 240.0
        servingUnit != "g" && gramsPerPiece > 0.0 -> servingUnit to gramsPerPiece
        gramsPerPiece > 0.0 && gramsPerPiece != 100.0 -> "piece" to gramsPerPiece
        else -> "piece" to 100.0
    }
}

fun formatFoodPortion(log: FoodLog): String {
    return if (log.quantity > 0.0 && log.unit != "g") {
        val qtyStr = if (log.quantity % 1.0 == 0.0) "${log.quantity.toInt()}" else "${log.quantity}"
        val pluralUnit = if (log.quantity > 1.0 && !log.unit.endsWith("s")) "${log.unit}s" else log.unit
        "$qtyStr $pluralUnit (${log.grams.toInt()}g)"
    } else {
        "${log.grams.toInt()}g"
    }
}

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey val id: String,
    val name: String,
    val servings: Double = 1.0,
    val instructions: String = ""
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["foodId"])
    ]
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0
)

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String, // Chest, Back, Legs, Shoulders, Arms, Core, Cardio
    val secondaryMuscles: String = "",
    val equipment: String, // Barbell, Dumbbell, Bodyweight, Cable, Machine, None
    val measurementType: String = "weight_reps", // weight_reps, bodyweight_reps, time, distance
    val instructions: String,
    val metValue: Double = 5.0, // For MET calorie expenditure calculation
    val source: String = "Free Exercise DB"
)

@Entity(tableName = "workout_routines")
data class WorkoutRoutine(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val splitCategory: String = "General" // Push/Pull/Legs, Upper/Lower, Full Body
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["exerciseId"])
    ]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: String,
    val exerciseId: String,
    val exerciseName: String,
    val targetSets: Int = 3,
    val targetRepsMin: Int = 8,
    val targetRepsMax: Int = 12,
    val orderIndex: Int = 0,
    val notes: String = ""
)

@Entity(tableName = "workout_schedules")
data class WorkoutSchedule(
    @PrimaryKey val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val dayName: String = "", // "Monday", "Tuesday", etc.
    val routineId: String? = null,
    val routineName: String? = null,
    val isRestDay: Boolean = false,
    val targetDurationMinutes: Int = 45,
    val defaultRestSeconds: Int = 90
)

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["date"])
    ]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val dayName: String,
    val durationMinutes: Int = 45,
    val distanceKm: Double = 0.0,
    val estimatedCaloriesBurned: Double = 0.0,
    val completed: Boolean = false,
    val notes: String = ""
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["exerciseId"])
    ]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exercise: String,
    val exerciseId: String = "",
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val weightType: String = "barbell", // barbell, dumbbell, bodyweight, machine, cable
    val rir: Int? = null, // Reps in Reserve (0 = failure, 1, 2, 3+)
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val completed: Boolean = true
)

@Entity(
    tableName = "body_entries",
    indices = [
        Index(value = ["date"])
    ]
)
data class BodyEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val waistCm: Double? = null,
    val neckCm: Double? = null,
    val chestCm: Double? = null,
    val photoPath: String? = null,
    val note: String = ""
)
