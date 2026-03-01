package com.example.mykmp.domain.context

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ConversationSummary
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository

/**
 * Стратегия суммаризации: старые сообщения заменяются кратким резюме,
 * последние N отправляются полностью. Обёртка существующей логики.
 */
class RollingSummaryStrategy(
    private val chatRepository: ChatRepository,
    private val historyRepository: ChatHistoryRepository
) : ContextStrategy {

    override val type = ContextStrategyType.ROLLING_SUMMARY
    override val displayName = type.displayName()
    override val description = type.description()

    private var summary: ConversationSummary? = null
    private var _isSummarizing = false

    val isSummarizing: Boolean get() = _isSummarizing
    val currentSummary: ConversationSummary? get() = summary

    /** Callback для уведомления UI об изменении состояния. */
    var onStateChanged: (() -> Unit)? = null

    override suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult {
        val validMessages = messages.filter { !it.isError }
        val n = config.contextWindowSize

        val rawMessages: List<ChatMessage>
        val summaryText: String?
        if (validMessages.size <= n) {
            rawMessages = validMessages
            summaryText = null
        } else {
            rawMessages = validMessages.takeLast(n)
            summaryText = summary?.summaryText
        }

        val apiMessages = rawMessages.map { msg ->
            ClaudeMessageRequest(
                role = when (msg.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                },
                content = msg.text
            )
        }

        val systemAddition = summaryText?.let {
            "Резюме предыдущей части разговора:\n$it"
        }

        return ContextResult(
            messagesToSend = apiMessages,
            systemPromptAddition = systemAddition
        )
    }

    override suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        val n = config.contextWindowSize
        val totalValid = allMessages.count { !it.isError }
        val covered = summary?.coveredMessageCount ?: 0

        if (totalValid - covered - n >= n) {
            performSummarization(allMessages, config)
        }
    }

    override fun loadState() {
        summary = historyRepository.loadSummary()
    }

    override fun clearState() {
        summary = null
        _isSummarizing = false
        historyRepository.clearSummary()
    }

    private suspend fun performSummarization(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        if (_isSummarizing) return
        _isSummarizing = true
        onStateChanged?.invoke()

        try {
            val n = config.contextWindowSize
            val allValid = allMessages.filter { !it.isError }
            val previousCovered = summary?.coveredMessageCount ?: 0

            val outsideWindow = allValid.dropLast(n)
            val newBatch = outsideWindow.drop(previousCovered)

            if (newBatch.isEmpty()) {
                _isSummarizing = false
                onStateChanged?.invoke()
                return
            }

            val existingSummary = summary?.summaryText
            val prompt = buildSummarizationPrompt(existingSummary, newBatch)

            val summaryConfig = config.copy(
                useDefaultTemperature = false,
                temperature = 0.0,
                maxTokens = 2048,
                useMaxTokensLimit = false,
                responseFormatMode = ResponseFormatMode.FREE_TEXT,
                formatHint = "",
                useStopSequences = false,
                stopSequences = emptyList()
            )

            val result = chatRepository.sendMessage(
                listOf(ClaudeMessageRequest(role = "user", content = prompt)),
                summaryConfig,
                null
            )

            result.fold(
                onSuccess = { response ->
                    val summaryText = response.content
                        .filter { it.type == "text" }
                        .joinToString("\n") { it.text }

                    val newSummary = ConversationSummary(
                        summaryText = summaryText,
                        coveredMessageCount = outsideWindow.size,
                        createdAt = 0L,
                        modelId = config.selectedModel.id
                    )
                    summary = newSummary
                    historyRepository.saveSummary(newSummary)
                    _isSummarizing = false
                    onStateChanged?.invoke()
                    println("✅ Суммаризация завершена: покрыто ${outsideWindow.size} сообщений")
                },
                onFailure = { error ->
                    println("❌ Ошибка суммаризации: ${error.message}")
                    _isSummarizing = false
                    onStateChanged?.invoke()
                }
            )
        } catch (e: Exception) {
            println("❌ Ошибка суммаризации: ${e.message}")
            _isSummarizing = false
            onStateChanged?.invoke()
        }
    }

    private fun buildSummarizationPrompt(
        existingSummary: String?,
        newMessages: List<ChatMessage>
    ): String {
        return buildString {
            appendLine("Создай краткое резюме диалога. Сохрани все ключевые факты, решения, контекст и договорённости.")
            appendLine("Резюме должно быть достаточно подробным, чтобы продолжить разговор без потери контекста.")
            appendLine("Пиши на том же языке, на котором велась беседа.")
            appendLine()

            if (!existingSummary.isNullOrBlank()) {
                appendLine("=== Предыдущее резюме ===")
                appendLine(existingSummary)
                appendLine()
            }

            appendLine("=== Новые сообщения для суммаризации ===")
            for (msg in newMessages) {
                val role = when (msg.role) {
                    ChatMessage.Role.USER -> "Пользователь"
                    ChatMessage.Role.ASSISTANT -> "Ассистент"
                }
                appendLine("$role: ${msg.text}")
                appendLine()
            }

            appendLine("Создай обновлённое резюме, объединив предыдущее резюме (если есть) и новые сообщения.")
        }
    }
}
