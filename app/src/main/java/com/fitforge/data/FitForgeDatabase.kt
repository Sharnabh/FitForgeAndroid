package com.fitforge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Food::class,
        FoodLog::class,
        BodyEntry::class,
        WorkoutSession::class,
        WorkoutSet::class,
        Recipe::class,
        RecipeIngredient::class,
        Exercise::class,
        WorkoutRoutine::class,
        RoutineExercise::class,
        WorkoutSchedule::class
    ],
    version = 8,
    exportSchema = true
)
abstract class FitForgeDatabase : RoomDatabase() {
    abstract fun dao(): FitForgeDao

    companion object {
        @Volatile private var INSTANCE: FitForgeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, `equipment` TEXT NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_routines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `splitCategory` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `exerciseName` TEXT NOT NULL, `targetSets` INTEGER NOT NULL, `targetRepsMin` INTEGER NOT NULL, `targetRepsMax` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `notes` TEXT NOT NULL)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_schedules` (`dayOfWeek` INTEGER PRIMARY KEY NOT NULL, `routineId` TEXT, `routineName` TEXT NOT NULL, `notes` TEXT NOT NULL, `isRestDay` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safely add column exerciseId to workout_sets if missing
                try {
                    db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `exerciseId` TEXT NOT NULL DEFAULT ''")
                } catch (ignored: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                } catch (ignored: Exception) {}
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. FoodLog indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_logs_date` ON `food_logs` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_logs_foodId` ON `food_logs` (`foodId`)")

                // 2. BodyEntry index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_entries_date` ON `body_entries` (`date`)")

                // 3. WorkoutSession index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_date` ON `workout_sessions` (`date`)")

                // 4. RecipeIngredient foreign key & indices
                db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_ingredients_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` TEXT NOT NULL, `foodId` TEXT NOT NULL, `foodName` TEXT NOT NULL, `grams` REAL NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `carbs` REAL NOT NULL, `fat` REAL NOT NULL, `fiber` REAL NOT NULL, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `recipe_ingredients_new` (`id`, `recipeId`, `foodId`, `foodName`, `grams`, `kcal`, `protein`, `carbs`, `fat`, `fiber`) SELECT `id`, `recipeId`, `foodId`, `foodName`, `grams`, `kcal`, `protein`, `carbs`, `fat`, `fiber` FROM `recipe_ingredients` WHERE `recipeId` IN (SELECT `id` FROM `recipes`)")
                db.execSQL("DROP TABLE `recipe_ingredients`")
                db.execSQL("ALTER TABLE `recipe_ingredients_new` RENAME TO `recipe_ingredients`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipeId` ON `recipe_ingredients` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_foodId` ON `recipe_ingredients` (`foodId`)")

                // 5. RoutineExercise foreign key & indices
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_exercises_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `exerciseName` TEXT NOT NULL, `targetSets` INTEGER NOT NULL, `targetRepsMin` INTEGER NOT NULL, `targetRepsMax` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`routineId`) REFERENCES `workout_routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `routine_exercises_new` (`id`, `routineId`, `exerciseId`, `exerciseName`, `targetSets`, `targetRepsMin`, `targetRepsMax`, `orderIndex`, `notes`) SELECT `id`, `routineId`, `exerciseId`, `exerciseName`, `targetSets`, `targetRepsMin`, `targetRepsMax`, `orderIndex`, `notes` FROM `routine_exercises` WHERE `routineId` IN (SELECT `id` FROM `workout_routines`)")
                db.execSQL("DROP TABLE `routine_exercises`")
                db.execSQL("ALTER TABLE `routine_exercises_new` RENAME TO `routine_exercises`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)")

                // 6. WorkoutSet foreign key & indices
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `setNumber` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `weightType` TEXT NOT NULL, `rir` INTEGER, `durationSeconds` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `completed` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `workout_sets_new` (`id`, `sessionId`, `exercise`, `exerciseId`, `setNumber`, `reps`, `weightKg`, `weightType`, `rir`, `durationSeconds`, `distanceMeters`, `completed`) SELECT `id`, `sessionId`, `exercise`, `exerciseId`, `setNumber`, `reps`, `weightKg`, `weightType`, `rir`, `durationSeconds`, `distanceMeters`, `completed` FROM `workout_sets` WHERE `sessionId` IN (SELECT `id` FROM `workout_sessions`)")
                db.execSQL("DROP TABLE `workout_sets`")
                db.execSQL("ALTER TABLE `workout_sets_new` RENAME TO `workout_sets`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_sessionId` ON `workout_sets` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_exerciseId` ON `workout_sets` (`exerciseId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `body_entries` ADD COLUMN `photoPathBack` TEXT DEFAULT NULL")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `body_entries` ADD COLUMN `photoPathSide` TEXT DEFAULT NULL")
                } catch (ignored: Exception) {}
            }
        }

        fun get(context: Context): FitForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FitForgeDatabase::class.java,
                    "fitforge.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { INSTANCE = it }
            }
    }
}
