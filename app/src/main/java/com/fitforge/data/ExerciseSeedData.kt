package com.fitforge.data

object ExerciseSeedData {
    val exercises = listOf(
        // Chest
        Exercise(
            id = "ex_barbell_bench_press",
            name = "Barbell Bench Press",
            muscleGroup = "Chest",
            secondaryMuscles = "Triceps, Anterior Deltoids",
            equipment = "Barbell",
            instructions = "1. Lie flat on the bench with eyes under the bar.\n2. Grip the bar slightly wider than shoulder-width.\n3. Unrack, lower the bar smoothly to mid-chest while tucking elbows at 45°.\n4. Press back up powerfully without bouncing off the ribcage.",
            metValue = 6.0
        ),
        Exercise(
            id = "ex_dumbbell_floor_press",
            name = "Dumbbell Floor Press",
            muscleGroup = "Chest",
            secondaryMuscles = "Triceps, Shoulders",
            equipment = "Dumbbell",
            instructions = "1. Lie on the floor holding dumbbells with arms extended.\n2. Lower elbows until the triceps lightly touch the floor at a 90° angle.\n3. Pause for a moment to eliminate momentum, then press up to lockout.",
            metValue = 5.0
        ),
        Exercise(
            id = "ex_pushups",
            name = "Push-ups",
            muscleGroup = "Chest",
            secondaryMuscles = "Core, Triceps, Shoulders",
            equipment = "Bodyweight",
            instructions = "1. Place hands shoulder-width apart, body straight from heels to head.\n2. Lower your chest until it is an inch off the floor.\n3. Push the floor away, keeping core tight and hips level throughout.",
            metValue = 4.0
        ),
        Exercise(
            id = "ex_incline_dumbbell_press",
            name = "Incline Dumbbell Press",
            muscleGroup = "Chest",
            secondaryMuscles = "Upper Chest, Triceps",
            equipment = "Dumbbell",
            instructions = "1. Set bench to a 30°–45° incline.\n2. Press dumbbells upward directly above clavicles.\n3. Lower with control until you feel a deep chest stretch, then drive back up.",
            metValue = 5.5
        ),

        // Back
        Exercise(
            id = "ex_barbell_deadlift",
            name = "Conventional Deadlift",
            muscleGroup = "Back",
            secondaryMuscles = "Hamstrings, Glutes, Core",
            equipment = "Barbell",
            instructions = "1. Stand with feet hip-width apart, bar over mid-foot.\n2. Hinge at hips, grip bar just outside knees.\n3. Engage lats, chest up, push through floor to stand tall.\n4. Return under control along shins.",
            metValue = 6.5
        ),
        Exercise(
            id = "ex_bent_over_row",
            name = "Bent-Over Barbell Row",
            muscleGroup = "Back",
            secondaryMuscles = "Biceps, Lats, Rhomboids",
            equipment = "Barbell",
            instructions = "1. Hinge hips back with torso around 45° and flat back.\n2. Pull barbell toward lower sternum / upper abdomen.\n3. Squeeze shoulder blades together at top, lower under full control.",
            metValue = 5.5
        ),
        Exercise(
            id = "ex_one_arm_db_row",
            name = "One-Arm Dumbbell Row",
            muscleGroup = "Back",
            secondaryMuscles = "Lats, Rear Deltoids, Biceps",
            equipment = "Dumbbell",
            instructions = "1. Place knee and hand on a flat bench, other foot on floor.\n2. Pull dumbbell toward your hip, keeping elbow close to the body.\n3. Control the descent to full lat stretch.",
            metValue = 5.0
        ),
        Exercise(
            id = "ex_pullups",
            name = "Pull-ups / Chin-ups",
            muscleGroup = "Back",
            secondaryMuscles = "Biceps, Forearms, Core",
            equipment = "Bodyweight",
            instructions = "1. Grip overhead bar slightly wider than shoulders (pronated or supinated).\n2. Pull elbows down and back until chin clears the bar.\n3. Lower slowly to dead hang.",
            metValue = 5.5
        ),

        // Legs
        Exercise(
            id = "ex_barbell_squat",
            name = "Barbell Back Squat",
            muscleGroup = "Legs",
            secondaryMuscles = "Quadriceps, Glutes, Adductors, Core",
            equipment = "Barbell",
            instructions = "1. Rest bar securely across upper traps.\n2. Break at hips and knees simultaneously, descending until thighs are parallel or below.\n3. Drive through whole foot to return to standing.",
            metValue = 6.0
        ),
        Exercise(
            id = "ex_goblet_squat",
            name = "Goblet Squat",
            muscleGroup = "Legs",
            secondaryMuscles = "Quadriceps, Core",
            equipment = "Dumbbell",
            instructions = "1. Hold a dumbbell vertically against chest with elbows pointing down.\n2. Squat deep between knees while keeping chest upright.\n3. Drive up through heels.",
            metValue = 5.0
        ),
        Exercise(
            id = "ex_romanian_deadlift",
            name = "Romanian Deadlift (RDL)",
            muscleGroup = "Legs",
            secondaryMuscles = "Hamstrings, Glutes, Erector Spinae",
            equipment = "Barbell",
            instructions = "1. Hold weight with soft knees.\n2. Push hips back as far as possible while keeping spine neutral.\n3. Feel strong stretch in hamstrings, then thrust hips forward to lock out.",
            metValue = 5.5
        ),
        Exercise(
            id = "ex_walking_lunges",
            name = "Dumbbell Walking Lunges",
            muscleGroup = "Legs",
            secondaryMuscles = "Quadriceps, Glutes, Balance",
            equipment = "Dumbbell",
            instructions = "1. Step forward with one leg, lowering hips until both knees form 90°.\n2. Keep front knee tracking over toes, back knee hovering above floor.\n3. Drive forward into next stride.",
            metValue = 5.5
        ),
        Exercise(
            id = "ex_standing_calf_raise",
            name = "Calf Raises",
            muscleGroup = "Legs",
            secondaryMuscles = "Gastrocnemius, Soleus",
            equipment = "Bodyweight",
            instructions = "1. Stand on edge of step on balls of feet.\n2. Lower heels for deep stretch.\n3. Press up onto toes, holding peak contraction for 1 second.",
            metValue = 3.5
        ),

        // Shoulders
        Exercise(
            id = "ex_overhead_press",
            name = "Overhead Barbell Press",
            muscleGroup = "Shoulders",
            secondaryMuscles = "Triceps, Upper Chest, Core",
            equipment = "Barbell",
            instructions = "1. Hold bar at collarbones, elbows slightly forward.\n2. Squeeze glutes and core, press bar vertically overhead around face.\n3. Lock out arms overhead with bar aligned with mid-foot.",
            metValue = 5.5
        ),
        Exercise(
            id = "ex_seated_dumbbell_press",
            name = "Seated Dumbbell Shoulder Press",
            muscleGroup = "Shoulders",
            secondaryMuscles = "Anterior Deltoids, Triceps",
            equipment = "Dumbbell",
            instructions = "1. Sit upright, bring dumbbells to shoulder height.\n2. Press dumbbells overhead until arms are extended.\n3. Lower with control to ear level.",
            metValue = 5.0
        ),
        Exercise(
            id = "ex_lateral_raises",
            name = "Dumbbell Lateral Raises",
            muscleGroup = "Shoulders",
            secondaryMuscles = "Lateral Deltoids, Traps",
            equipment = "Dumbbell",
            instructions = "1. Stand with light dumbbells at sides.\n2. Raise arms out to sides with slight bend in elbows until parallel to floor.\n3. Pause briefly at top, lower slowly without swinging.",
            metValue = 4.0
        ),
        Exercise(
            id = "ex_rear_delt_fly",
            name = "Dumbbell Rear-Delt Fly",
            muscleGroup = "Shoulders",
            secondaryMuscles = "Posterior Deltoids, Rhomboids",
            equipment = "Dumbbell",
            instructions = "1. Hinge forward at waist so torso is parallel to floor.\n2. Raise dumbbells outward in wide arc leading with elbows.\n3. Squeeze rear delts and lower smoothly.",
            metValue = 4.0
        ),

        // Arms
        Exercise(
            id = "ex_biceps_curl",
            name = "Dumbbell Biceps Curl",
            muscleGroup = "Arms",
            secondaryMuscles = "Biceps Brachii, Forearms",
            equipment = "Dumbbell",
            instructions = "1. Hold dumbbells at sides, palms facing forward.\n2. Curl weights up while keeping elbows pinned to ribs.\n3. Squeeze biceps at peak, lower with 2-second negative.",
            metValue = 4.0
        ),
        Exercise(
            id = "ex_hammer_curl",
            name = "Hammer Curls",
            muscleGroup = "Arms",
            secondaryMuscles = "Brachialis, Forearms",
            equipment = "Dumbbell",
            instructions = "1. Hold dumbbells with neutral grip (palms facing each other).\n2. Curl weights upward maintaining neutral wrist alignment.\n3. Lowers smoothly to sides.",
            metValue = 4.0
        ),
        Exercise(
            id = "ex_overhead_tricep_extension",
            name = "Overhead Triceps Extension",
            muscleGroup = "Arms",
            secondaryMuscles = "Triceps Long Head",
            equipment = "Dumbbell",
            instructions = "1. Hold one dumbbell with both hands overhead.\n2. Lower the weight behind your head by bending elbows.\n3. Extend arms back to starting position squeezing triceps.",
            metValue = 4.0
        ),
        Exercise(
            id = "ex_tricep_dips",
            name = "Bench / Parallel Bar Dips",
            muscleGroup = "Arms",
            secondaryMuscles = "Triceps, Chest, Shoulders",
            equipment = "Bodyweight",
            instructions = "1. Support yourself on bars or bench edge with straight arms.\n2. Lower body until elbows reach 90° angle.\n3. Push through palms to lock out.",
            metValue = 5.0
        ),

        // Core
        Exercise(
            id = "ex_plank",
            name = "Standard Forearm Plank",
            muscleGroup = "Core",
            secondaryMuscles = "Transverse Abdominis, Glutes, Shoulders",
            equipment = "Bodyweight",
            instructions = "1. Rest on forearms and toes, elbows under shoulders.\n2. Maintain straight line from heels to crown.\n3. Brace abs hard without letting lower back sag.",
            metValue = 3.5
        ),
        Exercise(
            id = "ex_hanging_leg_raise",
            name = "Hanging Knee / Leg Raise",
            muscleGroup = "Core",
            secondaryMuscles = "Lower Abs, Hip Flexors",
            equipment = "Bodyweight",
            instructions = "1. Hang from pull-up bar with arms extended.\n2. Curl knees or straight legs up toward chest by tilting pelvis.\n3. Lower with control without swinging.",
            metValue = 4.5
        ),

        // Cardio
        Exercise(
            id = "ex_brisk_walking",
            name = "Brisk Walking (Outdoor / Treadmill)",
            muscleGroup = "Cardio",
            secondaryMuscles = "Calves, Quads, Heart",
            equipment = "None",
            instructions = "1. Walk at energetic pace (approx. 5.5–6.5 km/h).\n2. Swing arms naturally and maintain upright posture.\n3. Great low-impact zone-2 fat burning activity.",
            metValue = 3.8
        ),
        Exercise(
            id = "ex_running_jogging",
            name = "Running / Jogging",
            muscleGroup = "Cardio",
            secondaryMuscles = "Full Body Cardiovascular",
            equipment = "None",
            instructions = "1. Steady aerobic pace (approx. 8–10 km/h).\n2. Land midfoot, keep rhythm consistent and breathing steady.",
            metValue = 8.0
        ),
        Exercise(
            id = "ex_jump_rope",
            name = "Jump Rope (Skipping)",
            muscleGroup = "Cardio",
            secondaryMuscles = "Calves, Shoulders, Coordination",
            equipment = "None",
            instructions = "1. Jump lightly on balls of feet.\n2. Turn rope using wrists, keeping jumps low (1–2 inches off ground).",
            metValue = 10.0
        ),
        Exercise(
            id = "ex_cycling",
            name = "Cycling / Stationary Bike",
            muscleGroup = "Cardio",
            secondaryMuscles = "Quads, Hamstrings, Glutes",
            equipment = "Machine",
            instructions = "1. Maintain 70–90 RPM cadence with moderate resistance.\n2. Keep upper body relaxed and push through pedals smoothly.",
            metValue = 7.0
        )
    )

    val routines = listOf(
        WorkoutRoutine(
            id = "push_day",
            name = "Push Day (Chest, Shoulders, Triceps)",
            description = "Hypertrophy pushing routine prioritizing chest, anterior/lateral deltoids, and tricep extension strength.",
            splitCategory = "Push/Pull/Legs"
        ),
        WorkoutRoutine(
            id = "pull_day",
            name = "Pull Day (Back & Biceps)",
            description = "Complete posterior chain routine targeting vertical & horizontal pulling and bicep flexion.",
            splitCategory = "Push/Pull/Legs"
        ),
        WorkoutRoutine(
            id = "legs_day",
            name = "Legs & Core Day",
            description = "Quad, hamstring, and core power session focused on compound leg movements.",
            splitCategory = "Push/Pull/Legs"
        ),
        WorkoutRoutine(
            id = "upper_body",
            name = "Upper Body Strength",
            description = "Balanced upper body workout featuring heavy presses and pulls for maximum muscle symmetry.",
            splitCategory = "Upper/Lower"
        ),
        WorkoutRoutine(
            id = "lower_body",
            name = "Lower Body & Posterior",
            description = "Squat and hinge-focused lower body strength workout for glute and leg development.",
            splitCategory = "Upper/Lower"
        ),
        WorkoutRoutine(
            id = "full_body_power",
            name = "Full Body Power Routine",
            description = "Time-efficient compound movement routine stimulating all major muscle groups.",
            splitCategory = "Full Body"
        )
    )

    val routineExercises = listOf(
        // Push Day
        RoutineExercise(0, "push_day", "ex_barbell_bench_press", "Barbell Bench Press", targetSets = 4, targetRepsMin = 6, targetRepsMax = 8, orderIndex = 1, notes = "Heavy compound push. Warm up thoroughly."),
        RoutineExercise(0, "push_day", "ex_incline_dumbbell_press", "Incline Dumbbell Press", targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, orderIndex = 2, notes = "Weight is per dumbbell. Control negative."),
        RoutineExercise(0, "push_day", "ex_overhead_press", "Barbell Overhead Press (OHP)", targetSets = 3, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 3, notes = "Brace core tight, do not arch lower back."),
        RoutineExercise(0, "push_day", "ex_lateral_raise", "Dumbbell Lateral Raise", targetSets = 3, targetRepsMin = 12, targetRepsMax = 15, orderIndex = 4, notes = "Strict form, lead with elbows."),
        RoutineExercise(0, "push_day", "ex_tricep_dips", "Triceps Dips", targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, orderIndex = 5, notes = "Keep torso upright to target triceps."),

        // Pull Day
        RoutineExercise(0, "pull_day", "ex_barbell_deadlift", "Conventional Deadlift", targetSets = 3, targetRepsMin = 5, targetRepsMax = 5, orderIndex = 1, notes = "Maintain flat neutral spine."),
        RoutineExercise(0, "pull_day", "ex_pullups", "Pull-ups / Chin-ups", targetSets = 3, targetRepsMin = 6, targetRepsMax = 10, orderIndex = 2, notes = "Full hang at bottom, chin over bar at top."),
        RoutineExercise(0, "pull_day", "ex_bent_over_row", "Bent-Over Barbell Row", targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, orderIndex = 3, notes = "Pull to lower ribs, squeeze scapulae."),
        RoutineExercise(0, "pull_day", "ex_barbell_curl", "Barbell Bicep Curl", targetSets = 3, targetRepsMin = 10, targetRepsMax = 12, orderIndex = 4, notes = "No body swing, full extension."),
        RoutineExercise(0, "pull_day", "ex_hammer_curl", "Dumbbell Hammer Curl", targetSets = 3, targetRepsMin = 10, targetRepsMax = 12, orderIndex = 5, notes = "Weight per dumbbell. Targets brachialis."),

        // Legs Day
        RoutineExercise(0, "legs_day", "ex_barbell_back_squat", "Barbell Back Squat", targetSets = 4, targetRepsMin = 6, targetRepsMax = 8, orderIndex = 1, notes = "Hit parallel depth, knees track toes."),
        RoutineExercise(0, "legs_day", "ex_romanian_deadlift", "Romanian Deadlift (RDL)", targetSets = 3, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 2, notes = "Push hips back, feel hamstring stretch."),
        RoutineExercise(0, "legs_day", "ex_walking_lunges", "Walking Lunges (Dumbbell / Bodyweight)", targetSets = 3, targetRepsMin = 10, targetRepsMax = 12, orderIndex = 3, notes = "10 reps per leg."),
        RoutineExercise(0, "legs_day", "ex_calf_raise", "Standing Calf Raise", targetSets = 4, targetRepsMin = 12, targetRepsMax = 15, orderIndex = 4, notes = "Hold peak contraction for 1 second."),
        RoutineExercise(0, "legs_day", "ex_hanging_leg_raise", "Hanging Leg Raise / Knee Raise", targetSets = 3, targetRepsMin = 12, targetRepsMax = 15, orderIndex = 5, notes = "Tilt pelvis up toward ribcage."),

        // Full Body Power
        RoutineExercise(0, "full_body_power", "ex_barbell_back_squat", "Barbell Back Squat", targetSets = 3, targetRepsMin = 6, targetRepsMax = 8, orderIndex = 1, notes = "Primary lower quad compound."),
        RoutineExercise(0, "full_body_power", "ex_barbell_bench_press", "Barbell Bench Press", targetSets = 3, targetRepsMin = 6, targetRepsMax = 8, orderIndex = 2, notes = "Primary upper push compound."),
        RoutineExercise(0, "full_body_power", "ex_bent_over_row", "Bent-Over Barbell Row", targetSets = 3, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 3, notes = "Primary upper pull compound."),
        RoutineExercise(0, "full_body_power", "ex_overhead_press", "Barbell Overhead Press (OHP)", targetSets = 3, targetRepsMin = 8, targetRepsMax = 10, orderIndex = 4, notes = "Vertical push shoulder strength."),
        RoutineExercise(0, "full_body_power", "ex_plank", "Plank (Front Core Hold)", targetSets = 3, targetRepsMin = 45, targetRepsMax = 60, orderIndex = 5, notes = "Hold 45-60s with rigid core.")
    )
}
