package com.fitforge

import com.fitforge.data.PRType
import com.fitforge.data.WorkoutCalculations
import com.fitforge.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCalculationsTest {

    // --- 1. Set Volume Calculation Tests ---

    @Test
    fun `calculateSetVolume for barbell calculates total external load`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 10, weightKg = 100.0, weightType = "barbell")
        assertEquals(1000.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for per-hand dumbbell doubles load for both arms`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 8, weightKg = 20.0, weightType = "dumbbell")
        // 8 reps * 20 kg * 2 arms = 320 kg total tonnage
        assertEquals(320.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for combined dumbbell calculates total weight directly`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 8, weightKg = 40.0, weightType = "dumbbell_combined")
        assertEquals(320.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for unweighted bodyweight returns zero external load`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 15, weightKg = 0.0, weightType = "bodyweight")
        assertEquals(0.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for weighted bodyweight counts added resistance`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 6, weightKg = 20.0, weightType = "weighted_bodyweight")
        assertEquals(120.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for assisted bodyweight returns zero external load`() {
        val volume = WorkoutCalculations.calculateSetVolume(reps = 8, weightKg = 15.0, weightType = "assisted")
        assertEquals(0.0, volume, 0.001)
    }

    @Test
    fun `calculateSetVolume for cardio and timed movements returns zero external load`() {
        val volumeTimed = WorkoutCalculations.calculateSetVolume(reps = 1, weightKg = 0.0, weightType = "timed")
        val volumeCardio = WorkoutCalculations.calculateSetVolume(reps = 1, weightKg = 0.0, weightType = "cardio")
        assertEquals(0.0, volumeTimed, 0.001)
        assertEquals(0.0, volumeCardio, 0.001)
    }

    @Test
    fun `calculateSetVolume with zero or negative reps returns zero`() {
        val zeroReps = WorkoutCalculations.calculateSetVolume(reps = 0, weightKg = 100.0, weightType = "barbell")
        val negReps = WorkoutCalculations.calculateSetVolume(reps = -5, weightKg = 100.0, weightType = "barbell")
        assertEquals(0.0, zeroReps, 0.001)
        assertEquals(0.0, negReps, 0.001)
    }

    // --- 2. Estimated 1RM (e1RM) Tests ---

    @Test
    fun `calculateE1RM with 1 rep returns exact weight`() {
        val e1rm = WorkoutCalculations.calculateE1RM(weightKg = 140.0, reps = 1)
        assertEquals(140.0, e1rm, 0.001)
    }

    @Test
    fun `calculateE1RM calculates accurate estimate using Epley formula`() {
        // 100kg x 10 reps = 100 * (1 + 10/30) = 133.33 -> rounded to 133.3
        val e1rm = WorkoutCalculations.calculateE1RM(weightKg = 100.0, reps = 10)
        assertEquals(133.3, e1rm, 0.05)
    }

    @Test
    fun `calculateE1RM with zero or invalid inputs returns zero`() {
        assertEquals(0.0, WorkoutCalculations.calculateE1RM(weightKg = 0.0, reps = 10), 0.001)
        assertEquals(0.0, WorkoutCalculations.calculateE1RM(weightKg = 100.0, reps = 0), 0.001)
    }

    @Test
    fun `formatE1RM includes explicit est notation`() {
        val formatted = WorkoutCalculations.formatE1RM(weightKg = 100.0, reps = 10)
        assertTrue(formatted.contains("(est.)"))
        assertTrue(formatted.contains("133"))
    }

    // --- 3. Progressive Overload Suggestion Tests ---

    @Test
    fun `suggestProgression recommends weight increase when all sets hit top of rep range`() {
        val sets = listOf(
            WorkoutSet(sessionId = 1, exercise = "Bench Press", setNumber = 1, reps = 12, weightKg = 80.0, rir = 2),
            WorkoutSet(sessionId = 1, exercise = "Bench Press", setNumber = 2, reps = 12, weightKg = 80.0, rir = 2),
            WorkoutSet(sessionId = 1, exercise = "Bench Press", setNumber = 3, reps = 12, weightKg = 80.0, rir = 1)
        )

        val suggestion = WorkoutCalculations.suggestProgression(
            exerciseName = "Bench Press",
            recentSets = sets,
            muscleGroup = "Chest",
            targetRepsMin = 8,
            targetRepsMax = 12
        )

        assertNotNull(suggestion)
        assertEquals(82.5, suggestion!!.suggestedWeightKg, 0.001) // +2.5kg for upper body
        assertEquals(8, suggestion.suggestedReps)
        assertTrue(suggestion.rationale.contains("+2.5kg"))
    }

    @Test
    fun `suggestProgression uses larger increment for lower body squats`() {
        val sets = listOf(
            WorkoutSet(sessionId = 1, exercise = "Barbell Squat", setNumber = 1, reps = 10, weightKg = 100.0, rir = 2),
            WorkoutSet(sessionId = 1, exercise = "Barbell Squat", setNumber = 2, reps = 10, weightKg = 100.0, rir = 1)
        )

        val suggestion = WorkoutCalculations.suggestProgression(
            exerciseName = "Barbell Squat",
            recentSets = sets,
            muscleGroup = "Legs",
            targetRepsMin = 6,
            targetRepsMax = 10
        )

        assertNotNull(suggestion)
        assertEquals(105.0, suggestion!!.suggestedWeightKg, 0.001) // +5.0kg for lower body squat
    }

    @Test
    fun `suggestProgression recommends rep progression when within rep range`() {
        val sets = listOf(
            WorkoutSet(sessionId = 1, exercise = "Overhead Press", setNumber = 1, reps = 9, weightKg = 50.0),
            WorkoutSet(sessionId = 1, exercise = "Overhead Press", setNumber = 2, reps = 8, weightKg = 50.0)
        )

        val suggestion = WorkoutCalculations.suggestProgression(
            exerciseName = "Overhead Press",
            recentSets = sets,
            muscleGroup = "Shoulders",
            targetRepsMin = 8,
            targetRepsMax = 12
        )

        assertNotNull(suggestion)
        assertEquals(50.0, suggestion!!.suggestedWeightKg, 0.001) // Same weight
        assertTrue(suggestion.suggestedReps in 9..10) // Suggest increasing reps towards 12
    }

    @Test
    fun `suggestProgression for bodyweight exercises suggests weighted progression once max reps achieved`() {
        val sets = listOf(
            WorkoutSet(sessionId = 1, exercise = "Pull Ups", setNumber = 1, reps = 12, weightKg = 0.0, weightType = "bodyweight"),
            WorkoutSet(sessionId = 1, exercise = "Pull Ups", setNumber = 2, reps = 12, weightKg = 0.0, weightType = "bodyweight"),
            WorkoutSet(sessionId = 1, exercise = "Pull Ups", setNumber = 3, reps = 12, weightKg = 0.0, weightType = "bodyweight")
        )

        val suggestion = WorkoutCalculations.suggestProgression(
            exerciseName = "Pull Ups",
            recentSets = sets,
            targetRepsMin = 8,
            targetRepsMax = 12
        )

        assertNotNull(suggestion)
        assertEquals(2.5, suggestion!!.suggestedWeightKg, 0.001) // Weighted progression
        assertTrue(suggestion.rationale.contains("Ready for weighted progression"))
    }

    @Test
    fun `suggestProgression returns null when no sets match exercise`() {
        val suggestion = WorkoutCalculations.suggestProgression(
            exerciseName = "Deadlift",
            recentSets = emptyList()
        )
        assertNull(suggestion)
    }

    // --- 4. Calorie Burn MET Tests ---

    @Test
    fun `calculateEstimatedCalories uses recorded bodyweight when available`() {
        val result = WorkoutCalculations.calculateEstimatedCalories(
            metValue = 6.0,
            durationMinutes = 60,
            latestBodyweightKg = 80.0
        )

        // MET 6.0 * 3.5 * 80 / 200 * 60 = 504 kcal
        assertEquals(504.0, result.estimatedKcal, 0.5)
        assertEquals(80.0, result.bodyweightUsedKg, 0.001)
        assertFalse(result.isFallbackWeight)
    }

    @Test
    fun `calculateEstimatedCalories flags fallback weight when no bodyweight recorded`() {
        val result = WorkoutCalculations.calculateEstimatedCalories(
            metValue = 6.0,
            durationMinutes = 60,
            latestBodyweightKg = null
        )

        // 75.0 kg default fallback: 6.0 * 3.5 * 75 / 200 * 60 = 472.5 kcal
        assertEquals(472.5, result.estimatedKcal, 0.5)
        assertEquals(75.0, result.bodyweightUsedKg, 0.001)
        assertTrue(result.isFallbackWeight)
    }

    // --- 5. Personal Record Detection Tests ---

    @Test
    fun `checkPersonalRecords detects weight, e1RM, and volume PRs correctly`() {
        val historicalSets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exercise = "Bench Press", setNumber = 1, reps = 5, weightKg = 90.0, weightType = "barbell"),
            WorkoutSet(id = 2, sessionId = 1, exercise = "Bench Press", setNumber = 2, reps = 5, weightKg = 90.0, weightType = "barbell")
        )

        // New set at 100kg x 6 reps -> higher weight, higher e1RM, higher volume
        val newSet = WorkoutSet(id = 3, sessionId = 2, exercise = "Bench Press", setNumber = 1, reps = 6, weightKg = 100.0, weightType = "barbell")

        val prs = WorkoutCalculations.checkPersonalRecords(newSet, historicalSets)
        assertTrue(prs.contains(PRType.WEIGHT_PR))
        assertTrue(prs.contains(PRType.E1RM_PR))
        assertTrue(prs.contains(PRType.VOLUME_PR))
    }

    @Test
    fun `checkPersonalRecords returns empty list when set is not a record`() {
        val historicalSets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exercise = "Bench Press", setNumber = 1, reps = 5, weightKg = 100.0, weightType = "barbell")
        )

        val lighterSet = WorkoutSet(id = 2, sessionId = 2, exercise = "Bench Press", setNumber = 1, reps = 3, weightKg = 80.0, weightType = "barbell")
        val prs = WorkoutCalculations.checkPersonalRecords(lighterSet, historicalSets)
        assertTrue(prs.isEmpty())
    }
}
