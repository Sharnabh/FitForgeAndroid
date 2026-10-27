package com.fitforge.data

enum class PRType(val label: String, val badge: String) {
    WEIGHT_PR("Weight Record", "🏆 Weight PR"),
    E1RM_PR("Est. 1RM Record", "🎯 e1RM PR"),
    VOLUME_PR("Set Volume Record", "⚡ Volume PR")
}

data class ProgressionSuggestion(
    val exerciseName: String,
    val suggestedWeightKg: Double,
    val suggestedReps: Int,
    val rationale: String,
    val isOptional: Boolean = true
)

data class EstimatedCaloriesResult(
    val estimatedKcal: Double,
    val isFallbackWeight: Boolean,
    val bodyweightUsedKg: Double,
    val label: String = "Est. Calories Burned (~)",
    val disclaimer: String = "MET-based approximation. Actual strength training energy expenditure varies with work-to-rest ratio and muscle mass."
)

object WorkoutCalculations {

    /**
     * Calculates training volume (tonnage) for a single set with explicit load basis:
     * - Barbell, Machine, Cable, Smith: total external load (reps * weightKg).
     * - Dumbbell (per-hand standard): reps * weightKg * 2.0.
     * - Dumbbell (combined): reps * weightKg * 1.0.
     * - Weighted Bodyweight: reps * addedWeightKg.
     * - Bodyweight-only, Assisted Bodyweight: 0.0 kg external load volume.
     * - Timed, Distance, Cardio: 0.0 kg external volume (tracked in time/distance units).
     */
    fun calculateSetVolume(reps: Int, weightKg: Double, weightType: String): Double {
        if (reps <= 0) return 0.0
        val type = weightType.lowercase().trim()

        return when {
            // Cardio, timed, distance movements do not fabricate weight tonnage
            type == "timed" || type == "distance" || type == "cardio" -> 0.0

            // Pure bodyweight or assisted movements have no positive external load added
            type == "bodyweight" || type == "assisted" || type == "assisted_bodyweight" -> 0.0

            // Weighted bodyweight (e.g. weighted dips, pull-ups) counts the added load
            type == "weighted_bodyweight" || type == "bodyweight_weighted" || type == "weighted" -> {
                if (weightKg > 0.0) reps * weightKg else 0.0
            }

            // Per-hand dumbbell logging doubles to account for both arms
            type == "dumbbell" || type == "dumbbell_per_hand" -> {
                if (weightKg > 0.0) reps * weightKg * 2.0 else 0.0
            }

            // Combined dumbbell logging (e.g. two 20kg dumbbells recorded as 40kg total)
            type == "dumbbell_combined" || type == "combined_dumbbell" -> {
                if (weightKg > 0.0) reps * weightKg else 0.0
            }

            // Barbell, machine, cable, smith: total external load
            else -> {
                if (weightKg > 0.0) reps * weightKg else 0.0
            }
        }
    }

    /**
     * Returns a human-readable display string for the specific exercise movement metric.
     */
    fun formatMovementMetric(set: WorkoutSet): String {
        val type = set.weightType.lowercase().trim()
        return when {
            type == "timed" -> "${set.durationSeconds}s"
            type == "distance" -> "${set.weightKg} km"
            type == "bodyweight" -> if (set.weightKg > 0.0) "BW + ${set.weightKg.toInt()} kg" else "Bodyweight"
            type == "assisted" || type == "assisted_bodyweight" -> "-${set.weightKg.toInt()} kg (assisted)"
            type == "dumbbell" || type == "dumbbell_per_hand" -> "${set.weightKg.toInt()} kg / hand"
            type == "dumbbell_combined" -> "${set.weightKg.toInt()} kg total"
            else -> "${set.weightKg.toInt()} kg"
        }
    }

    /**
     * Estimated One-Rep Max (e1RM) using the widely validated Epley formula:
     * e1RM = weight * (1 + reps / 30)
     * Note: Statistical estimate, most valid for 2..10 reps on compound external resistance.
     */
    fun calculateE1RM(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        val estimate = weightKg * (1.0 + reps / 30.0)
        return Math.round(estimate * 10.0) / 10.0
    }

    /**
     * Formats an estimated 1RM for display with explicit "est." notation.
     */
    fun formatE1RM(weightKg: Double, reps: Int): String {
        val e1rm = calculateE1RM(weightKg, reps)
        return if (e1rm > 0.0) "${e1rm.toInt()} kg (est.)" else "—"
    }

    /**
     * Personal Record check comparing current set against historical sets.
     */
    fun checkPersonalRecords(
        currentSet: WorkoutSet,
        historicalSets: List<WorkoutSet>
    ): List<PRType> {
        if (currentSet.weightKg <= 0.0 || currentSet.reps <= 0) return emptyList()

        val past = historicalSets.filter {
            it.id != currentSet.id &&
            (it.exerciseId.isNotEmpty() && it.exerciseId == currentSet.exerciseId ||
             it.exercise.equals(currentSet.exercise, ignoreCase = true))
        }
        if (past.isEmpty()) return emptyList()

        val prs = mutableListOf<PRType>()

        val maxPastWeight = past.maxOfOrNull { it.weightKg } ?: 0.0
        if (currentSet.weightKg > maxPastWeight && maxPastWeight > 0.0) {
            prs.add(PRType.WEIGHT_PR)
        }

        val currentE1RM = calculateE1RM(currentSet.weightKg, currentSet.reps)
        val maxPastE1RM = past.maxOfOrNull { calculateE1RM(it.weightKg, it.reps) } ?: 0.0
        if (currentE1RM > maxPastE1RM && maxPastE1RM > 0.0) {
            prs.add(PRType.E1RM_PR)
        }

        val currentVol = calculateSetVolume(currentSet.reps, currentSet.weightKg, currentSet.weightType)
        val maxPastVol = past.maxOfOrNull { calculateSetVolume(it.reps, it.weightKg, it.weightType) } ?: 0.0
        if (currentVol > maxPastVol && maxPastVol > 0.0) {
            prs.add(PRType.VOLUME_PR)
        }

        return prs
    }

    /**
     * Generates a progressive overload recommendation by evaluating ALL working sets
     * from the most recent completed session against the target rep range.
     * Compatible with external load exercises (Barbell, Dumbbell, Machine).
     */
    fun suggestProgression(
        exerciseName: String,
        recentSets: List<WorkoutSet>,
        muscleGroup: String = "",
        targetRepsMin: Int = 8,
        targetRepsMax: Int = 12,
        userIncrementKg: Double? = null
    ): ProgressionSuggestion? {
        val workingSets = recentSets.filter { it.exercise.equals(exerciseName, ignoreCase = true) }
        if (workingSets.isEmpty()) return null

        val lastSet = workingSets.last()
        val type = lastSet.weightType.lowercase().trim()

        // Distinguish non-load exercises
        if (type == "timed" || type == "distance" || type == "cardio") {
            val avgSeconds = workingSets.map { it.durationSeconds }.average().toInt()
            return ProgressionSuggestion(
                exerciseName = exerciseName,
                suggestedWeightKg = 0.0,
                suggestedReps = 0,
                rationale = "Timed movement. Aim to increase duration by +5–10s before adding resistance."
            )
        }

        if (type == "bodyweight" && lastSet.weightKg <= 0.0) {
            val allHitMax = workingSets.all { it.reps >= targetRepsMax }
            return if (allHitMax) {
                ProgressionSuggestion(
                    exerciseName = exerciseName,
                    suggestedWeightKg = 2.5,
                    suggestedReps = targetRepsMin,
                    rationale = "Completed all sets at bodyweight ($targetRepsMax+ reps). Ready for weighted progression (+2.5 kg)."
                )
            } else {
                ProgressionSuggestion(
                    exerciseName = exerciseName,
                    suggestedWeightKg = 0.0,
                    suggestedReps = (workingSets.map { it.reps }.average() + 1).toInt().coerceAtMost(targetRepsMax),
                    rationale = "Build bodyweight volume towards $targetRepsMax reps across all sets before adding load."
                )
            }
        }

        val currentWeight = lastSet.weightKg
        val avgReps = workingSets.map { it.reps }.average()
        val avgRir = workingSets.mapNotNull { it.rir }.average().takeIf { !it.isNaN() } ?: 1.5

        val isLowerBody = muscleGroup.contains("Leg", ignoreCase = true) ||
                exerciseName.contains("Squat", ignoreCase = true) ||
                exerciseName.contains("Deadlift", ignoreCase = true)
        val isDumbbell = type.contains("dumbbell")

        val standardIncrement = userIncrementKg ?: when {
            isDumbbell -> 2.0 // +2 kg per dumbbell
            isLowerBody -> 5.0 // +5 kg barbell lower body
            else -> 2.5 // +2.5 kg barbell upper body
        }

        val allSetsHitTop = workingSets.all { it.reps >= targetRepsMax }
        val allSetsWithinRange = workingSets.all { it.reps >= targetRepsMin }

        return when {
            // Case 1: All sets hit or exceeded top of target rep range with reserve -> Increase weight
            allSetsHitTop && avgRir >= 1.0 -> {
                ProgressionSuggestion(
                    exerciseName = exerciseName,
                    suggestedWeightKg = currentWeight + standardIncrement,
                    suggestedReps = targetRepsMin,
                    rationale = "Completed all ${workingSets.size} sets at $targetRepsMax reps (avg RIR ${"%.1f".format(avgRir)}). Suggest +${standardIncrement}kg next session."
                )
            }

            // Case 2: In target range -> Increase reps towards targetRepsMax
            allSetsWithinRange -> {
                val nextTargetRep = (avgReps.toInt() + 1).coerceAtMost(targetRepsMax)
                ProgressionSuggestion(
                    exerciseName = exerciseName,
                    suggestedWeightKg = currentWeight,
                    suggestedReps = nextTargetRep,
                    rationale = "Solid performance (${workingSets.size} sets in $targetRepsMin–$targetRepsMax range). Aim for $nextTargetRep reps across all sets at $currentWeight kg."
                )
            }

            // Case 3: Struggled or low RIR (<1) -> Consolidate current load
            else -> {
                ProgressionSuggestion(
                    exerciseName = exerciseName,
                    suggestedWeightKg = currentWeight,
                    suggestedReps = targetRepsMin,
                    rationale = "Focus on form and hitting $targetRepsMin clean reps at $currentWeight kg before adding load."
                )
            }
        }
    }

    /**
     * Calculates estimated calories burned using MET formula.
     * Flags fallback weight explicitly if no recent bodyweight was recorded.
     * Formula: kcal = (MET * 3.5 * weightKg) / 200 * durationMinutes
     */
    fun calculateEstimatedCalories(
        metValue: Double,
        durationMinutes: Int,
        latestBodyweightKg: Double?
    ): EstimatedCaloriesResult {
        val isFallback = latestBodyweightKg == null || latestBodyweightKg <= 0.0
        val weight = if (isFallback) 75.0 else latestBodyweightKg
        val minutes = durationMinutes.coerceAtLeast(1)
        val kcal = Math.round((metValue * 3.5 * weight) / 200.0 * minutes * 10.0) / 10.0

        return EstimatedCaloriesResult(
            estimatedKcal = kcal,
            isFallbackWeight = isFallback,
            bodyweightUsedKg = weight
        )
    }
}
