// ui/viewmodels/AIAssistanceViewModel.kt
package com.safeguardme.app.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeguardme.app.data.models.ChatMessage
import com.safeguardme.app.data.models.Sender
import com.safeguardme.app.data.repositories.AIAssistantRepository
import com.safeguardme.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIAssistanceViewModel @Inject constructor(
    private val aiAssistantRepository: AIAssistantRepository
) : ViewModel() {

    // Chat state
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // AI status (Phase 1: Mock, Phase 2: Live)
    private val _isAIActive = MutableStateFlow(false)
    val isAIActive: StateFlow<Boolean> = _isAIActive.asStateFlow()

    // Status indicator
    val statusText: StateFlow<String> = combine(_isTyping, _isAIActive) { typing, live ->
        when {
            typing -> "SafeguardMe Assistant is typing..."
            live -> "SafeguardMe Assistant - Live AI"
            else -> "SafeguardMe Assistant - FAQ Mode"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SafeguardMe Assistant")

    // Quick topic suggestions
    val quickTopics = listOf(
        "Legal Help" to "I need information about legal rights and protections",
        "Emotional Support" to "I'm feeling overwhelmed and need emotional support",
        "Safety Tips" to "What are some safety planning strategies?",
        "Emergency Help" to "I'm in immediate danger, what should I do?",
        "Evidence Collection" to "How do I safely document incidents?",
        "Hotlines" to "What support hotlines are available?"
    )

    // FAQ Database (Phase 1 Static Responses)
    private val faqDatabase = mapOf(
        // Legal Help
        "legal" to "🏛️ **Legal Rights & Protection**\n\n" +
                "• You have the right to be safe and protected\n" +
                "• Protection orders can be obtained from magistrate courts\n" +
                "• Legal Aid South Africa provides free legal assistance\n" +
                "• Document all incidents with dates, times, and details\n" +
                "• Keep evidence secure (photos, messages, medical records)\n\n" +
                "**Contact:** Legal Aid SA: 0800 110 110",

        "rights" to "⚖️ **Your Legal Rights**\n\n" +
                "• Right to safety and security of person\n" +
                "• Right to dignity and protection from violence\n" +
                "• Right to apply for protection orders\n" +
                "• Right to open criminal cases\n" +
                "• Right to free legal representation\n" +
                "• Right to privacy and confidentiality\n\n" +
                "Remember: Abuse is never your fault.",

        // Emotional Support
        "emotional" to "💙 **You Are Not Alone**\n\n" +
                "• Your feelings are valid and normal\n" +
                "• Healing takes time - be patient with yourself\n" +
                "• Consider professional counseling support\n" +
                "• Connect with trusted friends and family\n" +
                "• Practice self-care and stress management\n" +
                "• Remember: You deserve love and respect\n\n" +
                "**Crisis Support:** LifeLine 0861 322 322",

        "support" to "🤝 **Getting Emotional Support**\n\n" +
                "• POWA (People Opposing Women Abuse): 011 642 4345\n" +
                "• Childline: 116\n" +
                "• LifeLine: 0861 322 322\n" +
                "• SADAG (Depression/Anxiety): 0800 567 567\n" +
                "• Rape Crisis: 021 447 9762\n\n" +
                "These services are free and confidential.",

        // Safety Tips
        "safety" to "🛡️ **Safety Planning Strategies**\n\n" +
                "• Trust your instincts - they're usually right\n" +
                "• Have a safety plan and practice it\n" +
                "• Keep important documents accessible\n" +
                "• Have emergency contacts readily available\n" +
                "• Keep some money and keys hidden\n" +
                "• Know the location of nearest safe places\n\n" +
                "Safety planning saves lives.",

        "plan" to "📋 **Creating a Safety Plan**\n\n" +
                "1. **Recognize warning signs** of escalating violence\n" +
                "2. **Identify safe areas** in your home and community\n" +
                "3. **Plan escape routes** from home and work\n" +
                "4. **Pack emergency bag** (documents, money, clothes)\n" +
                "5. **Establish code words** with trusted people\n" +
                "6. **Practice your plan** when it's safe to do so\n\n" +
                "Update your plan regularly.",

        // Emergency Help
        "emergency" to "🚨 **Immediate Danger - ACT NOW**\n\n" +
                "**Call immediately:**\n" +
                "• Police: 10111\n" +
                "• Emergency Services: 112\n" +
                "• Gender-Based Violence Command Centre: 0800 428 428\n\n" +
                "**If you can't call:**\n" +
                "• SMS 'Help' to 31531\n" +
                "• Use SafeguardMe emergency features\n" +
                "• Get to a safe public place\n\n" +
                "**Your safety is the priority.**",

        "danger" to "⚠️ **If You're In Immediate Danger**\n\n" +
                "1. **Get to safety** - leave if possible\n" +
                "2. **Call for help** - 10111 or 112\n" +
                "3. **Go to public places** - shops, police station\n" +
                "4. **Alert trusted contacts** immediately\n" +
                "5. **Don't return home** until it's safe\n" +
                "6. **Seek medical attention** if injured\n\n" +
                "Trust your instincts. You know your situation best.",

        // Evidence Collection
        "evidence" to "📸 **Documenting Incidents Safely**\n\n" +
                "• Take photos of injuries immediately\n" +
                "• Keep a detailed incident diary\n" +
                "• Save threatening messages/emails\n" +
                "• Get medical records if injured\n" +
                "• Use SafeguardMe to store evidence securely\n" +
                "• Tell trusted friends about incidents\n\n" +
                "Evidence strengthens legal cases.",

        "document" to "📝 **What to Document**\n\n" +
                "• **Date and time** of each incident\n" +
                "• **Detailed description** of what happened\n" +
                "• **Photos** of injuries or property damage\n" +
                "• **Witness information** if available\n" +
                "• **Police case numbers** if reported\n" +
                "• **Medical reports** and treatment records\n\n" +
                "Store copies in multiple safe locations.",

        // Hotlines
        "hotlines" to "📞 **24/7 Support Hotlines**\n\n" +
                "**Crisis Lines:**\n" +
                "• GBV Command Centre: 0800 428 428\n" +
                "• LifeLine: 0861 322 322\n" +
                "• Childline: 116\n\n" +
                "**Specialized Support:**\n" +
                "• POWA: 011 642 4345\n" +
                "• Rape Crisis: 021 447 9762\n" +
                "• SADAG: 0800 567 567\n\n" +
                "All services are free and confidential.",

        "help" to "🆘 **Getting Help**\n\n" +
                "**Immediate danger:** Call 10111 or 112\n" +
                "**Support & counseling:** 0800 428 428\n" +
                "**Legal advice:** Legal Aid SA 0800 110 110\n" +
                "**Mental health:** SADAG 0800 567 567\n\n" +
                "You deserve support. These professionals are trained to help with domestic violence situations."
    )

    init {
        // Add welcome message
        addWelcomeMessage()

        if (aiAssistantRepository.isConfigured()) {
            _isAIActive.value = true
        }

        Log.d(TAG, "OpenAI configured: ${aiAssistantRepository.isConfigured()} (key length=${BuildConfig.OPENAI_API_KEY.length})")
    }

    private fun addWelcomeMessage() {
        val liveModeTip = if (aiAssistantRepository.isConfigured()) {
            "Toggle the AI icon to enable live OpenAI-powered support when you need more detailed guidance."
        } else {
            "To unlock live AI support, add an OpenAI API key to your local.properties file."
        }

        val welcomeMessage = ChatMessage(
            sender = Sender.BOT,
            text = "👋 Hello! I'm your SafeguardMe Assistant. I'm here to provide support, information, and resources about domestic violence.\n\n" +
                    "I can help with:\n" +
                    "• Legal rights and protections\n" +
                    "• Safety planning strategies\n" +
                    "• Emotional support resources\n" +
                    "• Emergency contacts and hotlines\n\n" +
                    "$liveModeTip"
        )
        _messages.value = listOf(welcomeMessage)
    }

    fun updateInput(input: String) {
        _currentInput.value = input.take(500) // Limit input length
    }

    fun sendMessage() {
        val input = _currentInput.value.trim()
        if (input.isEmpty()) return

        // Add user message
        val userMessage = ChatMessage(
            sender = Sender.USER,
            text = input
        )
        _messages.value = _messages.value + userMessage
        _currentInput.value = ""

        // Generate bot response
        generateBotResponse(input)
    }

    fun sendQuickTopic(topic: String, message: String) {
        val userMessage = ChatMessage(
            sender = Sender.USER,
            text = message
        )
        _messages.value = _messages.value + userMessage

        generateBotResponse(message)
    }

    fun toggleAIMode() {
        if (!aiAssistantRepository.isConfigured()) {
            val detectedLength = BuildConfig.OPENAI_API_KEY.length
            val hint = "Detected key length: $detectedLength. After updating local.properties run a Gradle sync/rebuild so the key reaches BuildConfig."
            postAssistantNotice(AIAssistantRepository.MISSING_KEY_MESSAGE + "\n" + hint)
            return
        }

        val enabled = !_isAIActive.value
        _isAIActive.value = enabled

        val notice = if (enabled) {
            "Live AI mode enabled. I'll craft tailored answers using OpenAI while keeping your safety first."
        } else {
            "Switched back to offline FAQ guidance."
        }
        postAssistantNotice(notice)
    }

    private fun generateBotResponse(userInput: String) {
        viewModelScope.launch {
            // Show typing indicator
            _isTyping.value = true
            delay(600)

            val response = if (_isAIActive.value && aiAssistantRepository.isConfigured()) {
                aiAssistantRepository.fetchAssistantReply(_messages.value)
                    .onFailure { error ->
                        Log.e(TAG, "Live AI response failed", error)
                        val reason = error.message?.takeIf { it.isNotBlank() }
                            ?: error.javaClass.simpleName
                        postAssistantNotice(
                            "Live AI is temporarily unavailable.\nReason: $reason\nShowing trusted offline guidance instead."
                        )
                    }
                    .getOrNull()
            } else null

            val text = response ?: findBestResponse(userInput)

            appendBotMessage(text)
            _isTyping.value = false
        }
    }

    private fun findBestResponse(input: String): String {
        val lowerInput = input.lowercase()

        // Find matching keywords in FAQ database
        val matchingResponses = faqDatabase.filter { (keywords, _) ->
            keywords.split(",").any { keyword ->
                lowerInput.contains(keyword.trim())
            }
        }

        return when {
            matchingResponses.isNotEmpty() -> {
                matchingResponses.values.first()
            }

            // Emergency keywords
            lowerInput.contains("emergency") || lowerInput.contains("danger") ||
                    lowerInput.contains("help me") || lowerInput.contains("urgent") -> {
                faqDatabase["emergency"] ?: getDefaultResponse()
            }

            // Common greeting responses
            lowerInput.contains("hello") || lowerInput.contains("hi") -> {
                "Hello! I'm here to help you with any questions about safety, legal rights, or support resources. What would you like to know about?"
            }

            lowerInput.contains("thank") -> {
                "You're welcome! Remember, you're not alone in this journey. Is there anything else I can help you with today?"
            }

            // Default response with helpful suggestions
            else -> getDefaultResponse()
        }
    }

    private fun getDefaultResponse(): String {
        return "I understand you're looking for help. Here are some ways I can assist you:\n\n" +
                "• **Say 'legal help'** for information about rights and protections\n" +
                "• **Say 'safety tips'** for safety planning strategies\n" +
                "• **Say 'emotional support'** for mental health resources\n" +
                "• **Say 'emergency'** for immediate danger guidance\n" +
                "• **Say 'hotlines'** for crisis support numbers\n\n" +
                "You can also use the topic buttons below for quick access to information."
    }

    fun clearChat() {
        _messages.value = emptyList()
        addWelcomeMessage()
    }

    private fun appendBotMessage(text: String) {
        val botMessage = ChatMessage(
            sender = Sender.BOT,
            text = text
        )
        _messages.value = _messages.value + botMessage
    }

    private fun postAssistantNotice(message: String) {
        appendBotMessage(message)
    }

    companion object {
        private const val TAG = "AIAssistanceVM"
    }
}
