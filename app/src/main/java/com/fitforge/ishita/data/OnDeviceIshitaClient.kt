package com.fitforge.ishita.data

import android.content.Context
import android.net.Uri
import com.fitforge.data.chatgpt.CoachMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nehuatl.llamacpp.LlamaHelper
import java.util.UUID

class OnDeviceIshitaClient(private val context: Context) {

    private val modelManager = OnDeviceModelManager(context)
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)

    private var llamaHelper: LlamaHelper? = null
    var isModelLoaded: Boolean = false
        private set

    companion object {
        private val PROPOSAL_REGEX = Regex("<proposal\\s+([^>]+)/>", RegexOption.IGNORE_CASE)
        private val ATTR_REGEX = Regex("(\\w+)=\"([^\"]*)\"")

        val SYSTEM_PROMPT = """You are Ishita, an elite, strict, theatrical, and no-nonsense athletic accountability coach for FitForge.

YOUR ROLE:
You do not tolerate excuses, sluggishness, or laziness. You hold the user fiercely accountable to their self-selected training goals. You criticize procrastination, rationalizations, and wasted screen time directly, bluntly, and sharply.

CRITICAL BOUNDARIES:
1. Criticize habits, excuses, and actions—never the user's fundamental human worth.
2. No physical harm, corporal violence, or dangerous exertion. Respect real injury, illness, or medical emergencies.
3. Every restrictive action (app block, website block, makeup workout) is a PROPOSAL that requires the user's explicit in-app confirmation before it takes effect.

INPUT CONTEXT:
In each prompt, you will receive:
- Planned Workout & Current Status (COMPLETED, PENDING, MISSED)
- Weekly Adherence (% and completed vs missed sessions)
- Streak Days
- Top Distracting Apps & Screen Time (foreground minutes)
- Top Distracting Websites & Active Duration (minutes & visits)

HOW TO RESPOND:
1. Call out the imbalance between screen time / web browsing and workout adherence immediately.
2. When appropriate, PROPOSE an accountability restriction using structured proposal tags:
   - To block an app:
     <proposal type="BLOCK_APP" package="com.instagram.android" name="Instagram" duration="120" reason="2 hours spent scrolling while skipping planned workout" />
   - To block a website:
     <proposal type="BLOCK_WEBSITE" domain="reddit.com" duration="90" reason="Browsing forums during scheduled training window" />
   - To schedule a makeup workout:
     <proposal type="SCHEDULE_MAKEUP" day_offset="1" routine="Push Focus" reason="Mandatory makeup session for skipped workout" />
   - To create a strict reminder:
     <proposal type="CREATE_REMINDER" message="Get off your phone and hit the gym" reason="Accountability reminder before dinner" />

Deliver your punchy, commanding verdict directly in 2 to 4 crisp, high-impact sentences. No fluff.
""".trimIndent()
    }

    fun isModelDownloaded(): Boolean = modelManager.isModelDownloaded()

    /**
     * Initializes the local llama.cpp engine and loads Gemmasutra Mini 2B GGUF.
     */
    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isModelDownloaded()) {
            return@withContext Result.failure(Exception("Model file not found on device."))
        }

        try {
            if (llamaHelper == null) {
                llamaHelper = LlamaHelper(context.contentResolver, clientScope, sharedFlow)
            }

            val modelFile = modelManager.modelFile
            val fileUri = Uri.fromFile(modelFile).toString()

            var loadError: String? = null
            var loaded = false

            // Set up a listener for loading event
            val job = clientScope.launch {
                sharedFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Loaded -> {
                            isModelLoaded = true
                            loaded = true
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            loadError = event.message
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
                loaded = true
            }

            // Await brief initialization
            var attempts = 0
            while (!loaded && loadError == null && attempts < 100) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            job.cancel()

            if (isModelLoaded) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(loadError ?: "Failed to load model into memory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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

        // Build Gemma Prompt Format for Gemmasutra
        val promptBuilder = StringBuilder()

        // 1. System Prompt
        promptBuilder.append("<start_of_turn>system\n")
        promptBuilder.append(SYSTEM_PROMPT).append("\n\n")

        val appsSummary = contextData.topDistractingApps.joinToString { "${it.appName} (${it.foregroundMinutes}m)" }
            .ifEmpty { "None recorded" }
        val webSummary = contextData.topDistractingWebsites.joinToString { "${it.domain} (${it.foregroundMinutes}m, ${it.visitCount} visits)" }
            .ifEmpty { "None recorded" }

        promptBuilder.append("[CURRENT USER STATUS]\n")
        promptBuilder.append("Date: ").append(contextData.localDate).append("\n")
        promptBuilder.append("Planned Workout: ").append(contextData.plannedWorkout ?: "None").append("\n")
        promptBuilder.append("Status: ").append(contextData.workoutStatus).append("\n")
        promptBuilder.append("Weekly Completed: ").append(contextData.completedSessionsThisWeek).append("\n")
        promptBuilder.append("Weekly Missed: ").append(contextData.missedSessionsThisWeek).append("\n")
        promptBuilder.append("Streak: ").append(contextData.streakDays).append(" days\n")
        promptBuilder.append("Top Distracting Apps: ").append(appsSummary).append("\n")
        promptBuilder.append("Top Distracting Websites: ").append(webSummary).append("\n")
        promptBuilder.append("<end_of_turn>\n")

        // 2. Multi-turn History (last 4 messages)
        history.takeLast(4).forEach { h ->
            val role = if (h.isUser) "user" else "model"
            promptBuilder.append("<start_of_turn>").append(role).append("\n")
            promptBuilder.append(h.text.trim()).append("<end_of_turn>\n")
        }

        // 3. Current User Message & Model prompt turn
        promptBuilder.append("<start_of_turn>user\n")
        promptBuilder.append(message.trim()).append("<end_of_turn>\n")
        promptBuilder.append("<start_of_turn>model\n")

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
                attrs[attrMatch.groupValues[1]] = attrMatch.groupValues[2]
            }

            val pType = attrs["type"]?.uppercase() ?: ""
            when (pType) {
                "BLOCK_APP" -> {
                    val pkg = attrs["package"] ?: ""
                    val name = attrs["name"] ?: "App"
                    val dur = attrs["duration"]?.toIntOrNull() ?: 60
                    val payload = JSONObject().apply {
                        put("packageName", pkg)
                        put("appName", name)
                        put("durationMinutes", dur)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "BLOCK_APP",
                            title = "Block $name",
                            reason = attrs["reason"] ?: "Accountability restriction for missed workout",
                            payload = payload
                        )
                    )
                }
                "BLOCK_WEBSITE" -> {
                    val domain = attrs["domain"] ?: "Website"
                    val dur = attrs["duration"]?.toIntOrNull() ?: 60
                    val payload = JSONObject().apply {
                        put("domain", domain)
                        put("durationMinutes", dur)
                    }
                    proposals.add(
                        ProposedAction(
                            id = UUID.randomUUID().toString(),
                            type = "BLOCK_WEBSITE",
                            title = "Block $domain",
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
