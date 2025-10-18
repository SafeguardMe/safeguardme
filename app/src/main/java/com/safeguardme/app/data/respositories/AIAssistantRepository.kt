package com.safeguardme.app.data.repositories

import android.util.Log
import com.safeguardme.app.BuildConfig
import com.safeguardme.app.data.models.ChatMessage
import com.safeguardme.app.data.models.RiskAssessment
import com.safeguardme.app.data.models.RiskLevel
import com.safeguardme.app.data.models.RiskSignalContext
import com.safeguardme.app.data.models.SafetyCoachPlan
import com.safeguardme.app.data.models.SafetyEvidence
import com.safeguardme.app.data.models.SafetySession
import com.safeguardme.app.data.models.Sender
import com.safeguardme.app.data.source.ChatCompletionRequest
import com.safeguardme.app.data.source.OpenAIChatMessage
import com.safeguardme.app.data.source.OpenAIService
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_TRIGGER_SAFETY = "TRIGGER_SAFETY"
private const val ACTION_GENERATE_REPORT = "GENERATE_REPORT"
private const val ACTION_SCHEDULE_CHECK_IN = "SCHEDULE_CHECK_IN"

@Singleton
class AIAssistantRepository @Inject constructor(
    private val openAIService: OpenAIService
) {


    data class AssistantContext(
        val profile: com.safeguardme.app.data.models.User? = null,
        val lastIncident: com.safeguardme.app.data.models.Incident? = null,
        val currentSession: SafetySession? = null
    )

    data class AssistantReply(
        val message: String,
        val actions: List<AssistantAction> = emptyList()
    )

    data class AssistantAction(
        val type: String,
        val payload: Map<String, String?> = emptyMap()
    )

    suspend fun fetchAssistantReply(
        history: List<ChatMessage>
    ): Result<String> {
        if (!openAIService.isConfigured()) {
            return Result.failure(IllegalStateException(MISSING_KEY_MESSAGE))
        }

        val conversation = buildConversation(history)

        val request = ChatCompletionRequest(
            model = BuildConfig.OPENAI_MODEL,
            messages = conversation,
            temperature = 0.3,
            maxTokens = 600
        )

        return openAIService.createChatCompletion(request).mapCatching { response ->
            val rawReply = response.primaryText()
            if (rawReply.isBlank()) {
                throw IllegalStateException(EMPTY_RESPONSE_MESSAGE)
            }
            rawReply
        }.onFailure { error ->
            Log.e(TAG, "OpenAI chat completion failed", error)
        }
    }

    suspend fun generateSafetyCoachPlan(
        session: SafetySession,
        evidence: List<SafetyEvidence>
    ): Result<SafetyCoachPlan> {
        if (!openAIService.isConfigured()) {
            return Result.failure(IllegalStateException(MISSING_KEY_MESSAGE))
        }

        val messages = listOf(
            OpenAIChatMessage("system", COACH_PROMPT.trim()),
            OpenAIChatMessage("user", buildCoachUserMessage(session, evidence))
        )

        val request = ChatCompletionRequest(
            model = BuildConfig.OPENAI_MODEL,
            messages = messages,
            temperature = 0.2,
            maxTokens = 700
        )

        return openAIService.createChatCompletion(request).mapCatching { response ->
            parseCoachPlan(response.primaryText())
        }
    }

    suspend fun assessRisk(signal: RiskSignalContext): Result<RiskAssessment> {
        if (!openAIService.isConfigured()) {
            return Result.failure(IllegalStateException(MISSING_KEY_MESSAGE))
        }

        val messages = listOf(
            OpenAIChatMessage("system", RISK_PROMPT.trim()),
            OpenAIChatMessage("user", buildRiskUserMessage(signal))
        )

        val request = ChatCompletionRequest(
            model = BuildConfig.OPENAI_MODEL,
            messages = messages,
            temperature = 0.0,
            maxTokens = 400
        )

        return openAIService.createChatCompletion(request).mapCatching { response ->
            parseRiskAssessment(response.primaryText(), signal)
        }
    }

    fun isConfigured(): Boolean {
        val configured = openAIService.isConfigured()
        if (!configured) {
            Log.w(TAG, "OpenAI not configured (BuildConfig.OPENAI_API_KEY length=${BuildConfig.OPENAI_API_KEY.length})")
        }
        return configured
    }

    companion object {
        private const val TAG = "AIAssistantRepository"
        private const val MAX_HISTORY_MESSAGES = 12
        private const val EMPTY_RESPONSE_MESSAGE = "Empty response from OpenAI"
        const val MISSING_KEY_MESSAGE = "OpenAI API key is not configured. Add OPENAI_API_KEY to your local.properties."

        private val SYSTEM_PROMPT = """
            You are SafeguardMe Assistant, a trauma-informed digital safety companion that supports
            people experiencing domestic violence or abuse. Provide concise, empathetic, and practical
            guidance grounded in South African context. Avoid legal jargon, encourage professional help,
            and prioritise user safety. Always remind users to call emergency numbers (10111/112) if they
            are in immediate danger. If unsure, ask gentle clarifying questions and offer relevant
            resources (legal aid, shelters, counselling, GBV hotlines). Keep tone calm, validating, and
            empowering. Never provide medical, legal, or financial guarantees—recommend consulting
            qualified professionals when necessary.
        """.trimIndent()

        private val COACH_PROMPT = """
            You are an emergency aftercare coach helping SafeguardMe summarise an incident session
            and prepare trauma-informed follow-up support. Respond ONLY with JSON containing:
            {
              "summary": string,
              "immediate_actions": [string],
              "follow_up_messages": [string],
              "next_check_in": epoch_millis_or_null,
              "recovery_prompts": [string],
              "risk_level": one of [UNKNOWN, LOW, MODERATE, HIGH, CRITICAL],
              "risk_score": integer 0-100,
              "risk_factors": [string]
            }
            Keep tone empowering, concise, and safety-first.
        """.trimIndent()

        private val RISK_PROMPT = """
            You classify SafeguardMe realtime signals to decide emergency escalation urgency.
            Respond ONLY with JSON containing:
            {
              "risk_level": one of [UNKNOWN, LOW, MODERATE, HIGH, CRITICAL],
              "risk_score": integer 0-100,
              "factors": [string],
              "recommended_action": string
            }
            Stay factual and strictly within the provided data.
        """.trimIndent()
    }

    private fun buildConversation(history: List<ChatMessage>): List<OpenAIChatMessage> {
        val trimmedHistory = history
            .filter { it.text.isNotBlank() && !it.isTyping }
            .takeLast(MAX_HISTORY_MESSAGES)

        val roleMapped = trimmedHistory.map { message ->
            val role = when (message.sender) {
                Sender.USER -> "user"
                Sender.BOT -> "assistant"
            }
            OpenAIChatMessage(
                role = role,
                content = message.text.take(2000)
            )
        }

        return listOf(OpenAIChatMessage("system", SYSTEM_PROMPT)) + roleMapped
    }

    private fun buildCoachUserMessage(
        session: SafetySession,
        evidence: List<SafetyEvidence>
    ): String {
        val builder = StringBuilder()
        builder.appendLine("Session summary request:")
        builder.appendLine("Session id: ${session.id}")
        builder.appendLine("Duration minutes: ${session.getDurationMinutes()}")
        builder.appendLine("Evidence count: ${evidence.size}")
        builder.appendLine("Trigger: ${session.triggerMethod?.name ?: "UNKNOWN"}")
        builder.appendLine("Emergency contacted: ${session.emergencyContacted}")
        session.metadata.forEach { (key, value) ->
            builder.appendLine("meta_$key: $value")
        }
        builder.appendLine("Evidence timeline:")
        evidence.sortedBy { it.timestamp }.forEach { item ->
            builder.append("- ${item.type.name} at ${item.timestamp} :: ")
            builder.appendLine(item.description.take(200))
        }
        if (session.summary != null) {
            builder.appendLine("Existing summary: ${session.summary}")
        }
        return builder.toString()
    }

    private fun buildRiskUserMessage(signal: RiskSignalContext): String {
        val builder = StringBuilder()
        builder.appendLine("Assess this signal:")
        builder.appendLine("Timestamp: ${signal.timestamp}")
        builder.appendLine("Source: ${signal.source}")
        builder.appendLine("Description: ${signal.description}")
        signal.gesture?.let { builder.appendLine("Gesture: $it") }
        signal.voiceKeyword?.let { builder.appendLine("Voice keyword: $it") }
        signal.shakeIntensity?.let { builder.appendLine("Shake intensity g: $it") }
        signal.volumeBurstCount?.let { builder.appendLine("Volume burst count: $it") }
        signal.locationAccuracyMeters?.let { builder.appendLine("Location accuracy m: $it") }
        signal.distanceFromSafeZoneMeters?.let { builder.appendLine("Safe zone distance m: $it") }
        signal.batteryLevel?.let { builder.appendLine("Battery level %: $it") }
        signal.additionalMetadata.forEach { (key, value) ->
            builder.appendLine("meta_$key: $value")
        }
        return builder.toString()
    }

    private fun parseCoachPlan(raw: String): SafetyCoachPlan {
        val json = JSONObject(extractJsonBlock(raw))
        val summary = json.optString("summary")
        val actions = json.optJSONArray("immediate_actions")?.toStringList() ?: emptyList()
        val followUps = json.optJSONArray("follow_up_messages")?.toStringList() ?: emptyList()
        val recovery = json.optJSONArray("recovery_prompts")?.toStringList() ?: emptyList()
        val nextCheckIn = if (json.isNull("next_check_in")) null else json.optLong("next_check_in")
        val riskLevel = json.optString("risk_level")
        val riskScore = json.optInt("risk_score", 0)
        val riskFactors = json.optJSONArray("risk_factors")?.toStringList() ?: emptyList()

        return SafetyCoachPlan(
            summary = summary,
            immediateActions = actions,
            followUpMessages = followUps,
            nextCheckInAt = nextCheckIn,
            recoveryPrompts = recovery,
            riskLevel = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.UNKNOWN),
            riskScore = riskScore,
            riskFactors = riskFactors
        )
    }

    private fun parseRiskAssessment(raw: String, signal: RiskSignalContext): RiskAssessment {
        val json = JSONObject(extractJsonBlock(raw))
        val riskLevel = json.optString("risk_level")
        val score = json.optInt("risk_score", 0)
        val factors = json.optJSONArray("factors")?.toStringList() ?: emptyList()
        val action = json.optString("recommended_action").takeIf { it.isNotBlank() }

        return RiskAssessment(
            timestamp = signal.timestamp,
            source = signal.source.name,
            level = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.UNKNOWN),
            score = score,
            factors = factors,
            recommendedAction = action
        )
    }

    private fun extractJsonBlock(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1)
        }
        return raw.trim()
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i)?.takeIf { it.isNotBlank() }?.let { list += it }
        }
        return list
    }
}
