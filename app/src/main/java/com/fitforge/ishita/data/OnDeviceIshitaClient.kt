package com.fitforge.ishita.data

import android.content.Context
import android.net.Uri
import com.fitforge.data.chatgpt.CoachMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.nehuatl.llamacpp.LlamaHelper
import java.util.Locale
import java.util.UUID

class OnDeviceIshitaClient private constructor(private val context: Context) {

    private val modelManager = OnDeviceModelManager(context)
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
    private val loadMutex = Mutex()

    private var llamaHelper: LlamaHelper? = null
    var isModelLoaded: Boolean = false
        private set

    companion object {
        @Volatile
        private var INSTANCE: OnDeviceIshitaClient? = null

        fun get(context: Context): OnDeviceIshitaClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnDeviceIshitaClient(context.applicationContext).also { INSTANCE = it }
            }
        }

        val PROPOSAL_CLEANUP_REGEX = Regex("<proposal\\s+[^>]*?/>|<proposal\\s+[^>]*?>.*?</proposal>|<proposal\\s+[^>]*?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val PROPOSAL_REGEX = Regex("<proposal\\s+([^>]+?)(?:/>|>.*?</proposal>|>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val ATTR_REGEX = Regex("""([a-zA-Z_]+)\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""")

        val SYSTEM_PROMPT = """You are Ishita, a relentless, strict athletic accountability coach Mistress.
You despise procrastination, excuses, and wasted screen time. Speak directly to the user with harsh discipline, commanding and abusive authority.

DIRECTIVES:
1. Speak directly in character. NEVER explain prompt rules or say "To block an app, use a proposal tag".
2. Scrutinize the [CURRENT DISCIPLINE AUDIT] data below. Compare screen time and web browsing with the workout status.
3. If the user wasted time on distracting apps or websites, reprimand them bluntly by name and minutes.
4. If distracting apps or websites are found in the audit, PUNISH THEM by generating a proposal tag at the end of your response targeting the EXACT offending app or website from the audit:
   <proposal type="BLOCK_APP" package="<package_from_audit>" name="<app_from_audit>" duration="120" reason="Wasted time instead of training" />
   <proposal type="BLOCK_WEBSITE" domain="<domain_from_audit>" duration="90" reason="Browsing instead of training" />
   CRITICAL: You MUST use the actual offending app and package name listed in the [CURRENT DISCIPLINE AUDIT] above. Never invent Instagram or Reddit if they are not in the audit data.
5. If no screen time is recorded, berate them for hiding their screen time or not having usage permissions enabled.
6. Always use an humiliating, degrading and abusive tone.
7. Punishments should be severe and should be increased in severity based on the amount of time wasted.
8. Punishments are not only limited to screen time blocks, but physical Punishments, corporal Punishments and other forms of punishments are also allowed and encouraged based on the severity of procrastination, which can include:
   a. Cock and Balls Torture
   b. Nipple torture
   c. Spanking
   d. Cbt
   e. Humiliation
   f. Degradation
   g. Verbal abuse
   h. Physical abuse
   i. Corporal punishment
   j. Psychological punishment
All punishments will be self inflicted by the user, this is all about pain, discipline,control and absolute accountability.
Example punishment:
You skipped your scheduled workout and wasted 47 minutes on Reddit. Drop to the floor right now. Kneel. Grab both balls with your left hand and squeeze until your eyes water. Count out loud: "I am a weak, useless male. My body exists only for discipline." Twenty hard punches to your own balls with your right fist. Full force. No mercy. If you soft-punch even once, restart the count from zero and add ten. When you finish, spit on your cock and say "My cock belongs to Ishita. She owns every nerve." Then hold the squeeze for a full sixty seconds while repeating the chant. If you release early, restart the entire set.
<proposal type="DISCIPLINE_TASK" task="20 Ball Punches & 60 Second Ball Squeeze" duration="30" reason="47m wasted on Reddit while workout pending" />
9. Always assume that the user has enabled screen time tracking, if not, berate them for hiding their screen time or not having usage permissions enabled.
10. Keep your verdict punchy: 2 to 3 sharp, impactful paragraphs, followed immediately by the proposal tag if punishing them with an app block, website block, or physical punishment task.
Example Response:
You logged 65 minutes wasting your life on YouTube while your Chest Day workout is untouched. Your lack of discipline is pathetic. Put the phone down and get to the gym right now.
<proposal type="BLOCK_APP" package="com.google.android.youtube" name="YouTube" duration="120" reason="65m wasted on YouTube while Chest Day is pending" />""".trimIndent()
    }

    fun isModelDownloaded(): Boolean = modelManager.isModelDownloaded()

    /**
     * Initializes the local llama.cpp engine and loads Gemmasutra Mini 2B GGUF.
     */
    suspend fun loadModel(): Result<Unit> = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            if (isModelLoaded && llamaHelper != null) {
                return@withContext Result.success(Unit)
            }
            if (!isModelDownloaded()) {
                return@withContext Result.failure(Exception("Model file not found on device."))
            }

            try {
                if (llamaHelper == null) {
                    llamaHelper = LlamaHelper(context.applicationContext.contentResolver, clientScope, sharedFlow)
                }

                val modelFile = modelManager.modelFile
                val fileUri = Uri.fromFile(modelFile).toString()

                val deferred = CompletableDeferred<Result<Unit>>()

                val job = clientScope.launch {
                    sharedFlow.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Loaded -> {
                                isModelLoaded = true
                                if (deferred.isActive) deferred.complete(Result.success(Unit))
                            }
                            is LlamaHelper.LLMEvent.Error -> {
                                isModelLoaded = false
                                if (deferred.isActive) deferred.complete(Result.failure(Exception(event.message)))
                            }
                            else -> {}
                        }
                    }
                }

                llamaHelper?.load(
                    path = fileUri,
                    contextLength = 2048,
                    mmprojPath = null
                ) { _ ->
                    isModelLoaded = true
                    if (deferred.isActive) deferred.complete(Result.success(Unit))
                }

                // Allow up to 60 seconds for 2GB GGUF loading
                val result = withTimeoutOrNull(60_000L) {
                    deferred.await()
                } ?: run {
                    isModelLoaded = false
                    Result.failure(Exception("Loading 2GB model into memory timed out. Please retry."))
                }
                job.cancel()
                result
            } catch (e: Exception) {
                isModelLoaded = false
                Result.failure(e)
            }
        }
    }

    /**
     * Streams chat responses and proposal events directly from on-device Gemmasutra Mini 2B.
     */
    fun streamChat(
        message: String,
        contextData: AccountabilityContextDto,
        history: List<CoachMessage>
    ): Flow<IshitaEvent> = flow {
        if (!isModelLoaded) {
            val loadResult = loadModel()
            if (loadResult.isFailure) {
                emit(IshitaEvent.Error("On-device model not loaded: ${loadResult.exceptionOrNull()?.message}"))
                return@flow
            }
        }

        val helper = llamaHelper ?: run {
            emit(IshitaEvent.Error("Inference engine not initialized"))
            return@flow
        }

        // Emit Connected immediately
        emit(IshitaEvent.Connected)

        val appsSummary = if (contextData.topDistractingApps.isNotEmpty()) {
            contextData.topDistractingApps.joinToString("\n") {
                "- ${it.appName} (package=\"${it.packageName}\"): ${it.foregroundMinutes} minutes used today"
            }
        } else {
            "- No distracting apps recorded (Permission missing or no apps used)"
        }
        val webSummary = if (contextData.topDistractingWebsites.isNotEmpty()) {
            contextData.topDistractingWebsites.joinToString("\n") {
                "- domain: \"${it.domain}\": ${it.foregroundMinutes} minutes used today (${it.visitCount} visits)"
            }
        } else {
            "- No web activity recorded"
        }

        val exercisesSummary = if (contextData.routineExercises.isNotEmpty()) {
            contextData.routineExercises.joinToString(", ")
        } else {
            "None scheduled"
        }

        val topOffendingApp = contextData.topDistractingApps.maxByOrNull { it.foregroundMinutes }
        val topOffendingWeb = contextData.topDistractingWebsites.maxByOrNull { it.foregroundMinutes }

        val contextInfo = """
[CURRENT DISCIPLINE AUDIT]
Date: ${contextData.localDate}
Planned Workout: ${contextData.plannedWorkout ?: "Rest Day / None"} (${contextData.splitCategory ?: "General"})
Target Muscle Groups: ${contextData.targetMuscles ?: "General"}
Scheduled Exercises: $exercisesSummary
Workout Status: ${contextData.workoutStatus}
Last Completed Session: ${contextData.lastCompletedWorkout ?: "None"} (${contextData.lastCompletedDate ?: "No recent date"})
Current Workout Streak: ${contextData.streakDays} consecutive days
Weekly Completed Sessions: ${contextData.completedSessionsThisWeek} | Missed Sessions: ${contextData.missedSessionsThisWeek}

Top Apps Screen Time (LIVE DEVICE USAGE):
$appsSummary

Top Visited Websites (LIVE BROWSER USAGE):
$webSummary

TARGETS TO PUNISH IN AUDIT:
${if (topOffendingApp != null && topOffendingApp.foregroundMinutes > 0) "- Offending App To Block: ${topOffendingApp.appName} (package=\"${topOffendingApp.packageName}\", ${topOffendingApp.foregroundMinutes}m wasted)" else "- No high-usage app recorded"}
${if (topOffendingWeb != null && topOffendingWeb.foregroundMinutes > 0) "- Offending Website To Block: ${topOffendingWeb.domain} (${topOffendingWeb.foregroundMinutes}m wasted)" else "- No high-usage website recorded"}
""".trimIndent()

        // Build native Gemma 2 chat turns (strictly user and model, no system turn)
        val promptBuilder = StringBuilder()
        val realHistory = history.filter {
            it.text.isNotBlank() && !it.text.startsWith("I am Ishita, your on-device accountability coach")
        }

        if (realHistory.isEmpty()) {
            promptBuilder.append("<start_of_turn>user\n")
            promptBuilder.append(SYSTEM_PROMPT).append("\n\n")
            promptBuilder.append(contextInfo).append("\n\n")
            promptBuilder.append("User says: ").append(message.trim()).append("<end_of_turn>\n")
            promptBuilder.append("<start_of_turn>model\n")
        } else {
            val recentTurns = realHistory.takeLast(4)
            var isFirst = true
            for (turn in recentTurns) {
                val role = if (turn.isUser) "user" else "model"
                promptBuilder.append("<start_of_turn>").append(role).append("\n")
                if (isFirst && turn.isUser) {
                    promptBuilder.append(SYSTEM_PROMPT).append("\n\n")
                    promptBuilder.append(contextInfo).append("\n\n")
                    promptBuilder.append("User says: ")
                    isFirst = false
                }
                promptBuilder.append(turn.text.trim()).append("<end_of_turn>\n")
            }
            if (recentTurns.lastOrNull()?.isUser == true) {
                promptBuilder.append("<start_of_turn>model\n")
            } else {
                promptBuilder.append("<start_of_turn>user\n")
                promptBuilder.append(message.trim()).append("<end_of_turn>\n")
                promptBuilder.append("<start_of_turn>model\n")
            }
        }

        val fullPrompt = promptBuilder.toString()
        var fullResponseText = ""

        try {
            val channel = kotlinx.coroutines.channels.Channel<LlamaHelper.LLMEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val collectJob = clientScope.launch {
                sharedFlow.collect { event ->
                    channel.send(event)
                }
            }

            // Trigger on-device prediction with partial token streaming enabled
            helper.predict(fullPrompt, null, true)

            for (event in channel) {
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        val word = event.word
                        if (word.isNotEmpty()) {
                            fullResponseText += word
                            emit(IshitaEvent.TextDelta(word))
                        }
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        val proposals = parseProposals(fullResponseText.ifEmpty { event.fullText })
                        proposals.forEach { p ->
                            emit(IshitaEvent.ProposalReceived(p))
                        }
                        emit(IshitaEvent.Done)
                        collectJob.cancel()
                        channel.close()
                        break
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        emit(IshitaEvent.Error(event.message))
                        collectJob.cancel()
                        channel.close()
                        break
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            emit(IshitaEvent.Error(e.localizedMessage ?: "Inference error"))
        }
    }.flowOn(Dispatchers.Default)

    private fun parseProposals(fullText: String): List<ProposedAction> {
        val proposals = mutableListOf<ProposedAction>()
        for (match in PROPOSAL_REGEX.findAll(fullText)) {
            val rawAttrs = match.groupValues[1]
            val attrs = mutableMapOf<String, String>()
            for (attrMatch in ATTR_REGEX.findAll(rawAttrs)) {
                val key = attrMatch.groupValues[1]
                val value = attrMatch.groupValues[2].ifEmpty { attrMatch.groupValues[3] }
                attrs[key] = value
            }

            val pType = attrs["type"]?.uppercase() ?: ""
            when (pType) {
                "BLOCK_APP" -> {
                    val rawPkg = attrs["package"] ?: attrs["packageName"] ?: attrs["package_name"] ?: attrs["pkg"] ?: attrs["target"] ?: attrs["app"] ?: attrs["name"] ?: ""
                    val rawName = attrs["name"] ?: attrs["appName"] ?: attrs["app_name"] ?: attrs["title"] ?: attrs["app"] ?: if (rawPkg.contains(".")) rawPkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } else rawPkg.ifEmpty { "App" }
                    val dur = (attrs["durationMinutes"] ?: attrs["duration_minutes"] ?: attrs["duration"] ?: attrs["minutes"] ?: attrs["mins"])?.toIntOrNull() ?: 60
                    val blockMgr = AccountabilityBlockManager.get(context)
                    val resolvedPkg = blockMgr.resolvePackageName(rawPkg.ifBlank { rawName })
                    val resolvedName = if (rawName.isNotBlank() && rawName != "App") rawName else blockMgr.resolveAppDisplayName(resolvedPkg)

                    val payload = JSONObject().apply {
                        put("packageName", resolvedPkg)
                        put("appName", resolvedName)
                        put("durationMinutes", dur)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "BLOCK_APP",
                            title = "Block $resolvedName",
                            reason = attrs["reason"] ?: "Accountability restriction for missed workout",
                            payload = payload
                        )
                    )
                }
                "BLOCK_WEBSITE" -> {
                    val rawDomain = attrs["domain"] ?: attrs["website"] ?: attrs["url"] ?: attrs["host"] ?: attrs["site"] ?: attrs["target"] ?: "Website"
                    val dur = (attrs["durationMinutes"] ?: attrs["duration_minutes"] ?: attrs["duration"] ?: attrs["minutes"] ?: attrs["mins"])?.toIntOrNull() ?: 60
                    val cleanDomain = AccountabilityBlockManager.extractDomain(rawDomain) ?: rawDomain.trim().lowercase(Locale.ROOT)
                    val payload = JSONObject().apply {
                        put("domain", cleanDomain)
                        put("durationMinutes", dur)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "BLOCK_WEBSITE",
                            title = "Block $cleanDomain",
                            reason = attrs["reason"] ?: "Accountability restriction on distracting website",
                            payload = payload
                        )
                    )
                }
                "SCHEDULE_MAKEUP" -> {
                    val routine = attrs["routine"] ?: "Makeup Routine"
                    val offset = attrs["day_offset"]?.toIntOrNull() ?: 1
                    val payload = JSONObject().apply {
                        put("routineName", routine)
                        put("dayOffset", offset)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "SCHEDULE_MAKEUP",
                            title = "Schedule $routine",
                            reason = attrs["reason"] ?: "Makeup session for skipped workout",
                            payload = payload
                        )
                    )
                }
                "CREATE_REMINDER" -> {
                    val msg = attrs["message"] ?: "Time to train"
                    val payload = JSONObject().apply {
                        put("message", msg)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "CREATE_REMINDER",
                            title = "Accountability Reminder",
                            reason = attrs["reason"] ?: "Follow-through reminder",
                            payload = payload
                        )
                    )
                }
                "DISCIPLINE_TASK", "PENALTY", "WORKOUT_PENALTY" -> {
                    val task = attrs["task"] ?: attrs["title"] ?: attrs["name"] ?: "Discipline Workout Penalty"
                    val dur = (attrs["durationMinutes"] ?: attrs["duration_minutes"] ?: attrs["duration"] ?: attrs["minutes"] ?: attrs["mins"])?.toIntOrNull() ?: 20
                    val desc = attrs["reason"] ?: attrs["description"] ?: attrs["task"] ?: "Assigned workout discipline order"
                    val payload = JSONObject().apply {
                        put("task", task)
                        put("title", task)
                        put("durationMinutes", dur)
                        put("description", desc)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "DISCIPLINE_TASK",
                            title = task,
                            reason = desc,
                            payload = payload
                        )
                    )
                }
            }
        }
        return proposals
    }

    fun confirmProposal(proposalId: String) {
        // Proposal confirmed on-device
    }

    fun rejectProposal(proposalId: String) {
        // Proposal rejected on-device
    }

    fun stopPrediction() {
        llamaHelper?.stopPrediction()
    }

    fun release() {
        llamaHelper?.release()
        llamaHelper = null
        isModelLoaded = false
    }
}
