package com.fitforge.ishita.data

import android.content.Context
import android.content.SharedPreferences
import com.fitforge.data.chatgpt.CoachMessage
import com.fitforge.ishita.service.CoachNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class IshitaChatManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val client = OnDeviceIshitaClient.get(context)
    private val blockManager = AccountabilityBlockManager.get(context)
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _messagesFlow = MutableStateFlow<List<CoachMessage>>(emptyList())
    val messagesFlow: StateFlow<List<CoachMessage>> = _messagesFlow.asStateFlow()

    private val _isStreamingFlow = MutableStateFlow(false)
    val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow()

    private val _streamingStatusFlow = MutableStateFlow("Ishita is ready")
    val streamingStatusFlow: StateFlow<String> = _streamingStatusFlow.asStateFlow()

    var isScreenVisible: Boolean = false

    private var currentChatJob: Job? = null

    companion object {
        private const val PREFS_NAME = "fitforge_ishita_chat_history"
        private const val KEY_MESSAGES_JSON = "ishita_messages_json"

        const val DEFAULT_WELCOME_MESSAGE =
            "I am Ishita, your on-device fitness trainer powered by Gemmasutra Mini 2B. Everything runs 100% locally and privately on your phone. I'm here to help you stay consistent, build strength, and reach your fitness goals. How was your training today?"

        @Volatile
        private var INSTANCE: IshitaChatManager? = null

        fun get(context: Context): IshitaChatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IshitaChatManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        val loaded = loadMessages()
        if (loaded.isEmpty()) {
            val initial = listOf(
                CoachMessage(
                    isUser = false,
                    text = DEFAULT_WELCOME_MESSAGE
                )
            )
            _messagesFlow.value = initial
            persistMessages(initial)
        } else {
            _messagesFlow.value = loaded
        }
    }

    fun sendMessage(textToSend: String, accContext: AccountabilityContextDto) {
        if (textToSend.isBlank() || _isStreamingFlow.value) return

        val userMsg = CoachMessage(isUser = true, text = textToSend)
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = CoachMessage(id = assistantMsgId, isUser = false, text = "")

        val currentList = _messagesFlow.value.toMutableList()
        currentList.add(userMsg)
        currentList.add(assistantMsg)
        _messagesFlow.value = currentList
        persistMessages(currentList)

        _isStreamingFlow.value = true
        _streamingStatusFlow.value = "Running on-device inference (Gemmasutra 2B)..."

        currentChatJob?.cancel()
        currentChatJob = managerScope.launch {
            var accumulatedText = ""
            val hist = if (currentList.size >= 2) currentList.dropLast(2) else emptyList()

            client.streamChat(textToSend, accContext, hist).collect { event ->
                when (event) {
                    is IshitaEvent.Connected -> {
                        _streamingStatusFlow.value = "Model loaded • Generating verdict..."
                    }
                    is IshitaEvent.TextDelta -> {
                        accumulatedText += event.delta
                        _streamingStatusFlow.value = "Ishita is responding..."
                        val list = _messagesFlow.value.toMutableList()
                        val idx = list.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            list[idx] = list[idx].copy(text = accumulatedText)
                            _messagesFlow.value = list
                        }
                    }
                    is IshitaEvent.ProposalReceived -> {
                        val proposal = event.proposal
                        val penaltyType = proposal.type
                        val target = when (penaltyType) {
                            "BLOCK_APP" -> proposal.payload.optString("packageName").ifEmpty { proposal.payload.optString("appName") }
                            "BLOCK_WEBSITE" -> proposal.payload.optString("domain")
                            "SCHEDULE_MAKEUP" -> proposal.payload.optString("routineName")
                            "DISCIPLINE_TASK" -> proposal.payload.optString("task")
                            else -> null
                        }
                        val dur = proposal.payload.optInt("durationMinutes", 60)
                        blockManager.addPenalty(
                            AccountabilityPenalty(
                                id = proposal.id,
                                type = penaltyType,
                                title = proposal.title,
                                description = proposal.reason,
                                reason = proposal.reason,
                                status = PenaltyStatus.PENDING,
                                target = target,
                                durationMinutes = dur
                            )
                        )
                    }
                    is IshitaEvent.Error -> {
                        _isStreamingFlow.value = false
                        val list = _messagesFlow.value.toMutableList()
                        val idx = list.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            val currentText = list[idx].text
                            val errNote = if (currentText.isBlank()) "[Error: ${event.message}]" else "$currentText\n[Error: ${event.message}]"
                            list[idx] = list[idx].copy(text = errNote)
                            _messagesFlow.value = list
                            persistMessages(list)
                        }
                    }
                    is IshitaEvent.Done -> {
                        _isStreamingFlow.value = false
                        val list = _messagesFlow.value.toMutableList()
                        val idx = list.indexOfFirst { it.id == assistantMsgId }
                        var finalText = accumulatedText
                        if (idx != -1) {
                            finalText = list[idx].text.replace(OnDeviceIshitaClient.PROPOSAL_CLEANUP_REGEX, "").trim()
                            list[idx] = list[idx].copy(text = finalText)
                            _messagesFlow.value = list
                            persistMessages(list)
                        }

                        // Send push notification if user left the screen or app
                        if (!isScreenVisible && finalText.isNotBlank()) {
                            CoachNotificationHelper.showResponseNotification(
                                context = context,
                                coachTitle = "Ishita (Fitness Trainer)",
                                previewText = finalText,
                                engine = "ISHITA"
                            )
                        }
                    }
                }
            }
            _isStreamingFlow.value = false
        }
    }

    fun clearChat() {
        currentChatJob?.cancel()
        _isStreamingFlow.value = false
        val initial = listOf(
            CoachMessage(
                isUser = false,
                text = DEFAULT_WELCOME_MESSAGE
            )
        )
        _messagesFlow.value = initial
        persistMessages(initial)
    }

    private fun persistMessages(messages: List<CoachMessage>) {
        val array = JSONArray()
        messages.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("isUser", m.isUser)
                put("text", m.text)
                put("hasPhotoAttached", m.hasPhotoAttached)
                put("timestamp", m.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES_JSON, array.toString()).apply()
    }

    private fun loadMessages(): List<CoachMessage> {
        val raw = prefs.getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
        val list = mutableListOf<CoachMessage>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CoachMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        isUser = obj.getBoolean("isUser"),
                        text = obj.getString("text"),
                        hasPhotoAttached = obj.optBoolean("hasPhotoAttached", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}

