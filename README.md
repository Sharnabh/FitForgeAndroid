# FitForge — Modern Android Fitness & Nutrition Tracker

FitForge is a privacy-conscious, offline-first personal fitness and nutrition tracking app built with modern Kotlin, Jetpack Compose Material 3, Room, and optional AI coaching.

---

## 🌟 Key Features

### 🏋️‍♂️ Workout & Exercise Tracking
- **800+ Exercise Catalog**: Bundled comprehensive database with Free Exercise DB movements and wger API integration.
- **Set-by-Set Logging**: Track weight, reps, load basis (barbell, dumbbell per-hand vs. combined, weighted vs. assisted bodyweight, machine, cable), RIR (Reps in Reserve), and set duration.
- **Intelligent Progressive Overload Engine**: Analyzes all working sets against target rep ranges with equipment-tailored increment suggestions and explicit rationale.
- **Personal Records (PRs)**: Automatically detects Weight PRs, Estimated 1RM PRs (validated Epley formula), and Set Volume PRs.
- **Weekly Workout Scheduler**: Plan multi-day workout splits, manage custom and pre-defined routines, and review weekly adherence.
- **Session Rest Timers & Audio Cues**: Configurable between-set and between-exercise countdown timers.

### 🥗 Nutrition & Macro Logging
- **Multi-Source Food Search**: Concurrent search across USDA FoodData Central and OpenFoodFacts API v3, including barcode scanning.
- **Quality Nutrition Mapping**: Distinguishes between explicitly reported zero nutrients (e.g. water, diet beverages) and missing nutrient fields, applying Atwater calorie fallbacks when appropriate.
- **Daily Dashboard**: Macro targets (calories, protein, carbs, fat, fiber) with intuitive portion sizing (slices, rotis, pieces, cups, scoops).
- **Custom Food & Recipe Management**: Save meals, custom foods, and recipe ingredient breakdowns.

### 📈 Body Composition & Trends
- **Progress Tracking**: Daily weight, waist, neck, chest measurements, and private progress photo attachments.
- **Offline Interactive Charts**: Custom Canvas weight progression trends with rolling averages.

### 🤖 AI Fitness Coach
- **ChatGPT Account Integration**: Secure OAuth PKCE sign-in to ChatGPT accounts using Keystore-backed `EncryptedSharedPreferences`.
- **Privacy First**: Explicit user consent dialog and granular toggles for workout, nutrition, and body composition data sharing. Local photo paths are never uploaded.
- **Real-Time Streaming**: Responsive chat interface with Markdown rendering and stop/cancel support.

### 🔒 Privacy, Security & Data Safety
- **Android Keystore Encryption**: ChatGPT access, refresh, and ID tokens are encrypted with `AES256_GCM` via `MasterKey`.
- **Auto Backup Exclusions**: Encrypted token preferences are strictly excluded from Android cloud backups and adb transfers via custom data extraction and backup XML rules.
- **Robust Room Database Migrations**: Versioned, non-destructive Room migrations (v1 through v7) with foreign key cascade relationships (`RoutineExercise -> WorkoutRoutine`, `WorkoutSet -> WorkoutSession`, `RecipeIngredient -> Recipe`) and query performance indexes.
- **Data Export & Restore**: User-controlled full JSON backup and restore with checksum verification.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: 100% Jetpack Compose with Material 3 Dark Mode styling and drag-to-expand bottom sheets (no disruptive popups).
- **Architecture**: MVI / MVVM pattern with Kotlin Coroutines and StateFlow.
- **Local Persistence**: Room Database 2.8+ with KSP schema export and SQLite Foreign Key Cascades.
- **Security**: AndroidX Security Crypto 1.1.0 (`EncryptedSharedPreferences`).
- **Networking**: Kotlin Coroutines + `HttpURLConnection` streaming for minimal dependency overhead.
- **Target SDK**: Android 15 (API 35), Min SDK: Android 8.0 (API 26).

---

## 🚀 Building & Running

### Requirements
- Android Studio Ladybug / Meerkat or later
- JDK 17 or JDK 21
- Android SDK 35+

### Build from Command Line
```bash
# Compile and run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug
```

---

## 📄 License
This project is for personal and demonstration use. Exercise datasets and nutritional sources are attributed to Free Exercise DB, wger, USDA FoodData Central, and OpenFoodFacts under their respective licenses.
