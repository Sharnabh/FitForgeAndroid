# FitForge — Ishita: Local Accountability & Digital Discipline Coach
## Complete Implementation Plan (v2.0)

**Target:** Android (Kotlin + Jetpack Compose) client; MacBook-hosted Ollama model; Hermes Agent; Restricted FastAPI Tool Bridge; Android Digital Wellbeing APIs.

**Persona:** Ishita is a fictional adult athletic-mistress coach: commanding, strict, theatrical, sharp, and blunt. She may use profanity and consensual adult roleplay framing. Her critique attacks excuses, procrastination, and undisciplined habits—never personal human worth. Real-world actions remain transparent, strictly sandboxed, and user-confirmed.

---

## 1. Product Goals & Scope

Build the **Ishita Local Engine** integrated directly into FitForge alongside the existing ChatGPT option. Ishita handles high-stakes fitness and digital accountability:
1. **Workout & Habit Audits**: Evaluates completed sessions, missed workouts, volume progression, and streak adherence.
2. **Screen Time & Distraction Auditing**: Ingests daily foreground app usage via Android's `UsageStatsManager` and directly compares time wasted on entertainment/social apps against skipped workouts.
3. **Accountability App Blocking**: Proposes targeted temporary app blocks (via an Android `AccessibilityService` overlay) when workouts are skipped or screen time exceeds agreed thresholds.
4. **Local Privacy**: 100% of Ishita inference runs locally on your Mac via Ollama over a private Tailscale network. Zero external cloud tracking.
5. **Human-in-the-Loop**: The model can only *propose* actions; FitForge displays clear confirmation cards with Reject/Confirm controls.
6. **No Safety Boundaries**:terminal/shell execution with permission, can use corporal/physical harm, covert device monitoring. Everything should be done with consent of the user.

---

## 2. System Architecture

```text
FitForge Android (Kotlin + Jetpack Compose)
  ├── Coach Engine Selector (Cloud ChatGPT vs. Local Ishita)
  ├── Screen Time Collector (UsageStatsManager)
  ├── App Block Enforcement (AccessibilityService Overlay)
  ├── Room Database (Workouts, Nutrition, Body Metrics)
  ├── WorkManager (Periodic Check-ins & Reminders)
  └── Proposal Confirmation Cards (Confirm / Reject)
             │ 
             │ Private HTTPS over Tailscale (mTLS / Bearer Token)
             ▼
MacBook (Host)
  ├── FitForge Bridge API (FastAPI)
  │    ├── Device Pairing & Keystore Token Auth
  │    ├── Proposal Store (SQLite) & State Machine
  │    └── Strict Schema Validator & Rate Limiter
  ├── Hermes Agent (Dedicated Restricted Profile)
  │    └── MCP Proposal Tools Only (No bash, no file write)
  └── Ollama
       └── R4C3R/qwen3-8b-heretic (Text-only inference on Loopback)
```

Ollama provides raw inference; Hermes handles orchestration; the FastAPI bridge serves as the strict security boundary. Neither Ollama nor Hermes are exposed directly to the open internet.

---

## 3. Digital Wellbeing, Screen Time & Website Tracking

### A. App Screen Time Collection (`UsageStatsManager`)
* **Permission**: `android.permission.PACKAGE_USAGE_STATS`
* **How it works**:
  1. FitForge checks if permission is granted; if not, prompts the user to grant access via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
  2. Aggregates foreground time for the last 24 hours across non-system packages.

### B. Website Visits & Duration Tracking (`AccessibilityService`)
* **Service**: `FitForgeAccountabilityAccessibilityService`
* **How it works on Android**:
  1. Because Android's `UsageStatsManager` only tracks the browser app package (e.g. `com.android.chrome`), tracking *individual website domains* requires an `AccessibilityService`.
  2. The service listens to `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_TEXT_CHANGED` when a supported browser (Chrome, Samsung Internet, Brave, Firefox) is active.
  3. It extracts the current URL / root host from the browser’s address bar UI node (e.g., `com.android.chrome:id/url_bar`).
  4. An on-device session timer records elapsed active browsing seconds per domain:
     ```kotlin
     data class WebsiteUsageDto(
         val domain: String,          // e.g. "reddit.com", "youtube.com"
         val foregroundMinutes: Long, // e.g. 45
         val visitCount: Int          // e.g. 6
     )
     ```
  5. The top distracting websites and apps are attached as context to Ishita's daily review.

### C. Distraction & Website Blocking Engine
* **App Blocking**: When a blocked package is brought to the foreground, the service fires `Intent.ACTION_MAIN` (Home) and displays a full-screen FitForge accountability overlay:
  > *"Access Blocked by Ishita: Complete your scheduled 45-min workout to unlock."*
* **Website Blocking**: When a blocked domain is detected in the browser's address bar, the service immediately navigates to `about:blank` or redirects to FitForge with the accountability overlay.
* **Auto-Unblock**: Blocks expire automatically after the confirmed duration (e.g., 60 minutes) or immediately upon logging the pending workout session.

---

## 4. MCP Proposal Tools (Mac Bridge)

Hermes is restricted strictly to the following proposal-only MCP tools:

| MCP Tool | Purpose | Output |
| :--- | :--- | :--- |
| `get_coach_policy()` | Reads quiet hours, permitted data, and thresholds | Read-only JSON |
| `get_today_summary()` | Returns workout status + top apps & websites visited | Read-only JSON |
| `propose_workout_review(session_id)` | Critiques exercise volume and RIR | Proposal Card |
| `propose_makeup_workout(day_offset, focus)` | Schedules a makeup session for a missed day | Proposal Card |
| `propose_app_block(package_name, duration_min, reason)` | Proposes blocking a distracting app | Proposal Card |
| `propose_website_block(domain, duration_min, reason)` | Proposes blocking a distracting website domain | Proposal Card |
| `propose_reminder(message, timestamp)` | Schedules an accountability reminder | Proposal Card |
| `propose_points_change(delta, rule_id, reason)` | Deducts/awards discipline points | Proposal Card |

**Crucial Constraint**: The model cannot execute actions directly. It generates a signed proposal UUID in SQLite with status `PENDING`. Execution occurs strictly on Android after the user taps **[Confirm]**.

---

## 5. UI Layout & FitForge 4-Tab Alignment

In accordance with FitForge's new **4-Tab Navigation** (`Today`, `Workouts`, `Nutrition`, `Progress`):
* **Entry Point**: The Coach is accessed from the **Today** tab via:
  1. Top App Bar sparkle action button (`Icons.Default.AutoAwesome`).
  2. Interactive AI Coach Hero Card on the main dashboard.
* **Engine Toggle (In Settings/Header)**:
  * `[●] Cloud (ChatGPT)` — Standard conversational nutrition/workout coach.
  * `[●] Local Mac (Ishita)` — Strict local accountability and digital wellbeing auditor.
* **Screen Components**:
  1. **Header**: Coach status badge (`Connected via Tailscale` / `Offline`), Active Engine, and Emergency Pause toggle.
  2. **Daily Discipline Strip**: Workout status (Completed / Pending / Missed), Weekly Adherence %, and Distraction Time (`Apps: 2h 15m • Web: 1h 10m`).
  3. **Pending Proposal Cards**:
     * Example: *“Block reddit.com & Instagram (120 min) — Reason: Skipped Chest & Triceps while browsing 2h 45m today.”*
     * Actions: **[Confirm & Enforce]** | **[Reject]**.
  4. **Streaming Timeline**: Terminal-style high-contrast responses from Ishita.
  5. **Quick Actions**: *“Audit My Screen & Web Time”*, *“Review Today’s Lifts”*, *“I Skipped My Workout (Face Critique)”*.

---

## 6. Kotlin Interfaces & Data Models

```kotlin
package com.fitforge.ishita.domain

data class CoachRequest(
    val requestId: String,
    val message: String,
    val context: AccountabilityContextDto
)

data class AccountabilityContextDto(
    val localDate: String,
    val plannedWorkout: String?,
    val workoutStatus: String, // "COMPLETED", "PENDING", "MISSED"
    val completedSessionsThisWeek: Int,
    val missedSessionsThisWeek: Int,
    val streakDays: Int,
    val topDistractingApps: List<AppUsageSummaryDto>,
    val topDistractingWebsites: List<WebsiteUsageDto>
)

data class AppUsageSummaryDto(
    val packageName: String,
    val appName: String,
    val foregroundMinutes: Long
)

data class WebsiteUsageDto(
    val domain: String,
    val foregroundMinutes: Long,
    val visitCount: Int
)

enum class ActionType {
    CREATE_REMINDER,
    REVIEW_WORKOUT,
    SCHEDULE_MAKEUP,
    BLOCK_APP,
    BLOCK_WEBSITE,
    ADJUST_POINTS
}

sealed interface ActionPayload {
    data class Reminder(val message: String, val epochSeconds: Long?) : ActionPayload
    data class MakeupWorkout(val dayOfWeek: Int, val routineName: String) : ActionPayload
    data class BlockApp(val packageName: String, val appName: String, val durationMinutes: Int) : ActionPayload
    data class BlockWebsite(val domain: String, val durationMinutes: Int) : ActionPayload
    data class PointsChange(val delta: Int, val reason: String) : ActionPayload
}

data class ProposedActionDto(
    val id: String,
    val type: ActionType,
    val title: String,
    val reason: String,
    val expiresAtEpochSeconds: Long,
    val payload: ActionPayload
)

sealed interface CoachEvent {
    data class TextDelta(val text: String) : CoachEvent
    data class ActionProposed(val action: ProposedActionDto) : CoachEvent
    data class Failure(val message: String) : CoachEvent
    data object Completed : CoachEvent
}

interface IshitaRepository {
    fun streamReply(request: CoachRequest): kotlinx.coroutines.flow.Flow<CoachEvent>
    suspend fun getPendingActions(): List<ProposedActionDto>
    suspend fun confirmAction(actionId: String): ActionResult
    suspend fun rejectAction(actionId: String): ActionResult
    suspend fun pauseCoach(): ActionResult
    suspend fun revokeConnection(): ActionResult
}

data class ActionResult(val success: Boolean, val message: String)
```

---

## 7. Delivery & Implementation Sequence

1. **Step 1: Android Screen Time Collector (`UsageStatsRepository`)**
   * Register `PACKAGE_USAGE_STATS` in `AndroidManifest.xml`.
   * Create helper to check if permission is granted and launch system Settings intent if needed.
   * Query `UsageStatsManager.queryUsageStats()` to aggregate daily foreground time for social/entertainment apps.
2. **Step 2: Android Blocker & Browser Observer (`AccessibilityService`)**
   * Implement `FitForgeAccountabilityAccessibilityService` with an overlay UI.
   * Observe active browser address bar nodes (Chrome, Brave, Samsung Internet, Firefox) to track domain visit duration.
   * Enforce app blocks and website domain blocks with an on-screen accountability overlay.
   * Wire local expiry timer and unblock triggers (e.g. workout completed in Room).
3. **Step 3: Mac Bridge & Ollama Setup**
   * Set up Ollama with `R4C3R/qwen3-8b-heretic` on your Mac.
   * Build the lightweight FastAPI bridge with SQLite proposal storage and token authentication.
   * Expose bridge securely over Tailscale Serve (HTTPS).
4. **Step 4: Ishita Screen & Proposal Cards in FitForge**
   * Integrate Ishita engine toggle inside the AI Coach module.
   * Build `ProposedActionCard` for Confirming/Rejecting app blocks, website blocks, reminders, and makeup sessions.
5. **Step 5: Automated Accountability Check-ins**
   * Integrate Android `WorkManager` for end-of-day missed workout audits and distraction critiques within user-configured quiet hours.

---

## 8. Definition of Done

- [ ] Local inference runs on Mac via Tailscale; no silent cloud leak.
- [ ] Daily screen time for distracting apps is read via `UsageStatsManager`.
- [ ] Website visits and active time per domain are captured via `AccessibilityService`.
- [ ] Ishita critiques missed workouts in context of logged screen and browsing time.
- [ ] App and website blocks require explicit user tap on a proposal card before activation.
- [ ] Emergency Pause toggle stops all blocking and notifications immediately.
- [ ] No arbitrary terminal/shell access or uncontrolled device takeover is permitted.
