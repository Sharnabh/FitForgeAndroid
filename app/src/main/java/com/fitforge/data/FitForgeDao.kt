package com.fitforge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FitForgeDao {
    // Foods
    @Query("SELECT * FROM foods ORDER BY name")
    fun observeFoods(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY name LIMIT 100")
    suspend fun searchFoods(query: String): List<Food>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun foodCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(items: List<Food>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: Food)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: String)

    // Food Logs
    @Insert
    suspend fun insertFoodLog(log: FoodLog)

    @Query("SELECT * FROM food_logs WHERE date = :date ORDER BY id DESC")
    fun observeFoodLogs(date: String): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE date = :date AND mealType = :mealType ORDER BY id DESC")
    fun observeFoodLogsByMeal(date: String, mealType: String): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE date >= :fromDate AND date <= :toDate ORDER BY date")
    fun observeFoodLogsRange(fromDate: String, toDate: String): Flow<List<FoodLog>>

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteFoodLog(id: Long)

    // Recipes & Ingredients
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipes(items: List<Recipe>)

    @Query("SELECT * FROM recipes ORDER BY name")
    fun observeRecipes(): Flow<List<Recipe>>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeIngredient(ingredient: RecipeIngredient)

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY id ASC")
    fun observeRecipeIngredients(recipeId: String): Flow<List<RecipeIngredient>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY id ASC")
    suspend fun getRecipeIngredients(recipeId: String): List<RecipeIngredient>

    @Query("DELETE FROM recipe_ingredients WHERE id = :id")
    suspend fun deleteRecipeIngredient(id: Long)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteRecipeIngredients(recipeId: String)

    // Exercises Catalogue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name")
    fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup ORDER BY name")
    fun observeExercisesByMuscle(muscleGroup: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' OR muscleGroup LIKE '%' || :query || '%' OR equipment LIKE '%' || :query || '%' ORDER BY name")
    suspend fun searchExercises(query: String): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    // Workout Sessions & Sets
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSession(session: WorkoutSession): Long

    @Update
    suspend fun updateWorkoutSession(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getWorkoutSessionById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    fun observeWorkoutSessionById(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY id ASC")
    suspend fun getWorkoutSessionsForDate(date: String): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY id ASC")
    fun observeWorkoutSessionsForDate(date: String): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY id DESC LIMIT 1")
    suspend fun getWorkoutSession(date: String): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY id DESC LIMIT 1")
    fun observeWorkoutSession(date: String): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC, id DESC")
    fun observeWorkoutSessions(): Flow<List<WorkoutSession>>

    @Query("UPDATE workout_sessions SET completed = :completed WHERE id = :id")
    suspend fun setWorkoutCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteWorkoutSession(id: Long)

    @Insert
    suspend fun insertWorkoutSet(set: WorkoutSet)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exercise, setNumber")
    fun observeWorkoutSets(sessionId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exercise, setNumber")
    suspend fun getWorkoutSets(sessionId: Long): List<WorkoutSet>

    @Query("SELECT MAX(setNumber) FROM workout_sets WHERE sessionId = :sessionId AND ((exerciseId != '' AND exerciseId = :exerciseId) OR exercise = :exerciseName)")
    suspend fun getMaxSetNumber(sessionId: Long, exerciseId: String, exerciseName: String): Int?

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun deleteWorkoutSetsForSession(sessionId: Long)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteWorkoutSet(id: Long)

    @Query("SELECT * FROM workout_sets WHERE exercise = :exerciseName ORDER BY id ASC")
    suspend fun getHistoricalSetsForExercise(exerciseName: String): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets ORDER BY id ASC")
    fun observeAllWorkoutSets(): Flow<List<WorkoutSet>>

    // Backup & Restore Bulk Queries
    @Query("SELECT * FROM foods")
    suspend fun getAllFoods(): List<Food>

    @Query("SELECT * FROM food_logs")
    suspend fun getAllFoodLogs(): List<FoodLog>

    @Query("SELECT * FROM body_entries")
    suspend fun getAllBodyEntries(): List<BodyEntry>

    @Query("SELECT * FROM workout_sessions")
    suspend fun getAllWorkoutSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_sets")
    suspend fun getAllWorkoutSets(): List<WorkoutSet>

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipes(): List<Recipe>

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun getAllRecipeIngredients(): List<RecipeIngredient>

    @Query("SELECT * FROM workout_routines")
    suspend fun getAllWorkoutRoutines(): List<WorkoutRoutine>

    @Query("SELECT * FROM routine_exercises")
    suspend fun getAllRoutineExercises(): List<RoutineExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFoodLogs(logs: List<FoodLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBodyEntries(entries: List<BodyEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreWorkoutSessions(sessions: List<WorkoutSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreWorkoutSets(sets: List<WorkoutSet>)

    // Workout Routines & Plans
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutRoutines(routines: List<WorkoutRoutine>)

    @Query("SELECT * FROM workout_routines ORDER BY splitCategory, name")
    fun observeWorkoutRoutines(): Flow<List<WorkoutRoutine>>

    @Query("SELECT COUNT(*) FROM workout_routines")
    suspend fun routineCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(exercises: List<RoutineExercise>)

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    fun observeRoutineExercises(routineId: String): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getRoutineExercises(routineId: String): List<RoutineExercise>

    @Query("DELETE FROM workout_routines WHERE id = :id")
    suspend fun deleteWorkoutRoutine(id: String)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    // Workout Schedules & Consistency
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedule(schedule: WorkoutSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WorkoutSchedule>)

    @Query("SELECT * FROM workout_schedules ORDER BY dayOfWeek ASC")
    fun observeWeeklySchedule(): Flow<List<WorkoutSchedule>>

    @Query("SELECT * FROM workout_schedules ORDER BY dayOfWeek ASC")
    suspend fun getWeeklyScheduleList(): List<WorkoutSchedule>

    @Query("SELECT * FROM workout_schedules WHERE dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getScheduleForDay(dayOfWeek: Int): WorkoutSchedule?

    @Query("SELECT COUNT(*) FROM workout_schedules")
    suspend fun scheduleCount(): Int

    // Body Entries
    @Insert
    suspend fun insertBodyEntry(entry: BodyEntry)

    @Update
    suspend fun updateBodyEntry(entry: BodyEntry)

    @Query("SELECT * FROM body_entries ORDER BY date DESC, id DESC")
    fun observeBodyEntries(): Flow<List<BodyEntry>>

    @Query("SELECT * FROM body_entries ORDER BY date DESC, id DESC LIMIT 1")
    fun observeLatestBodyEntry(): Flow<BodyEntry?>

    @Query("DELETE FROM body_entries WHERE id = :id")
    suspend fun deleteBodyEntry(id: Long)
}
