package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.data.api.ApiAccessChecker
import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ConversationSummary
import com.example.mykmp.domain.model.ConversationTokensStats
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.model.TokensUsage
import com.example.mykmp.domain.model.toConversationStats
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние UI чата.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val requestConfig: ChatRequestConfig = ChatRequestConfig(),
    val isSettingsExpanded: Boolean = false,
    val isGeoBlocked: Boolean = false,
    val conversationStats: ConversationTokensStats = ConversationTokensStats(),
    val estimatedInputTokens: Int = 0,
    val conversationSummary: ConversationSummary? = null,
    val isSummarizing: Boolean = false,
    val isSummaryExpanded: Boolean = false
)

/**
 * ViewModel чата. Управляет состоянием UI и взаимодействием с Claude API.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val apiAccessChecker = ApiAccessChecker()
    private var messageCounter = 0L
    private var conversationSummary: ConversationSummary? = null

    init {
        loadHistory()
        loadSettings()
        loadSummary()
        checkApiAccess()
    }

    private fun loadHistory() {
        val saved = chatHistoryRepository.loadHistory()
        if (saved.isNotEmpty()) {
            messageCounter = saved.maxOf { it.timestamp }
            _uiState.update { it.copy(
                messages = saved,
                conversationStats = saved.toConversationStats()
            ) }
        }
    }

    private fun loadSettings() {
        val saved = chatHistoryRepository.loadSettings()
        if (saved != null) {
            _uiState.update { it.copy(requestConfig = saved) }
        }
    }

    private fun loadSummary() {
        conversationSummary = chatHistoryRepository.loadSummary()
        conversationSummary?.let { summary ->
            _uiState.update { it.copy(conversationSummary = summary) }
        }
    }

    private fun checkApiAccess() {
        viewModelScope.launch {
            val blocked = apiAccessChecker.isGeoBlocked()
            _uiState.update { it.copy(isGeoBlocked = blocked) }
        }
    }

    private fun saveHistory() {
        chatHistoryRepository.saveHistory(_uiState.value.messages)
    }

    /**
     * Обновляет текст в поле ввода.
     */
    fun onInputChanged(text: String) {
        _uiState.update { it.copy(
            inputText = text,
            estimatedInputTokens = estimateInputTokens(it.messages, text, it.requestConfig)
        ) }
    }

    /**
     * Переключает видимость панели настроек.
     */
    fun onToggleSettings() {
        _uiState.update { it.copy(isSettingsExpanded = !it.isSettingsExpanded) }
    }

    /**
     * Обновляет конфигурацию параметров запроса.
     */
    fun onUpdateConfig(config: ChatRequestConfig) {
        _uiState.update { it.copy(
            requestConfig = config,
            estimatedInputTokens = estimateInputTokens(it.messages, it.inputText, config)
        ) }
        chatHistoryRepository.saveSettings(config)
    }

    /**
     * Сбрасывает конфигурацию к значениям по умолчанию.
     */
    fun onResetConfig() {
        val defaults = ChatRequestConfig()
        _uiState.update { it.copy(requestConfig = defaults) }
        chatHistoryRepository.saveSettings(defaults)
    }

    /**
     * Отправляет сообщение пользователя в Claude API.
     * Использует текущую конфигурацию из requestConfig.
     */
    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(
            id = (++messageCounter).toString(),
            role = ChatMessage.Role.USER,
            text = text,
            timestamp = messageCounter
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isLoading = true,
                error = null,
                isSettingsExpanded = false,
                estimatedInputTokens = 0
            )
        }

        viewModelScope.launch {
            val currentState = _uiState.value
            val selectedModel = currentState.requestConfig.selectedModel
            val contextWindowSize = currentState.requestConfig.contextWindowSize

            // Конвертируем историю чата в формат Claude API,
            // пропуская сообщения-ошибки
            val allValid = currentState.messages.filter { !it.isError }

            // Контекстное окно: последние N сообщений сырыми, остальные — через summary
            val rawMessages: List<ChatMessage>
            val activeSummaryText: String?
            if (allValid.size <= contextWindowSize) {
                rawMessages = allValid
                activeSummaryText = null
            } else {
                rawMessages = allValid.takeLast(contextWindowSize)
                activeSummaryText = conversationSummary?.summaryText
            }

            val conversationHistory = rawMessages.map { msg ->
                ClaudeMessageRequest(
                    role = when (msg.role) {
                        ChatMessage.Role.USER -> "user"
                        ChatMessage.Role.ASSISTANT -> "assistant"
                    },
                    content = msg.text
                )
            }

            // Замеряем время ответа
            val timeMark = TimeSource.Monotonic.markNow()

            val result = chatRepository.sendMessage(
                conversationHistory,
                currentState.requestConfig,
                activeSummaryText
            )

            val responseTimeMs = timeMark.elapsedNow().inWholeMilliseconds

            result.fold(
                onSuccess = { response ->
                    // Извлекаем текстовые блоки из ответа
                    val assistantText = response.content
                        .filter { it.type == "text" }
                        .joinToString("\n") { it.text }

                    // Суффикс, если ответ был обрезан или остановлен
                    val stopSuffix = when (response.stopReason) {
                        "max_tokens" -> "\n\n[...ответ обрезан по max_tokens]"
                        "stop_sequence" -> "\n\n[...остановлено по stop_sequence]"
                        else -> ""
                    }

                    // Извлекаем usage и считаем стоимость
                    val tokensUsage = response.usage?.let {
                        TokensUsage(inputTokens = it.inputTokens, outputTokens = it.outputTokens)
                    }
                    val costUsd = tokensUsage?.let {
                        selectedModel.calculateCost(it.inputTokens, it.outputTokens)
                    }

                    val assistantMessage = ChatMessage(
                        id = (++messageCounter).toString(),
                        role = ChatMessage.Role.ASSISTANT,
                        text = assistantText + stopSuffix,
                        timestamp = messageCounter,
                        modelId = selectedModel.id,
                        modelDisplayName = selectedModel.displayName,
                        responseTimeMs = responseTimeMs,
                        tokensUsage = tokensUsage,
                        costUsd = costUsd
                    )
                    _uiState.update {
                        val newMessages = it.messages + assistantMessage
                        it.copy(
                            messages = newMessages,
                            isLoading = false,
                            conversationStats = newMessages.toConversationStats()
                        )
                    }
                    saveHistory()
                    checkAndTriggerSummarization()
                },
                onFailure = { error ->
                    val errorMessage = ChatMessage(
                        id = (++messageCounter).toString(),
                        role = ChatMessage.Role.ASSISTANT,
                        text = "Error: ${error.message ?: "Unknown error"}",
                        timestamp = messageCounter,
                        isError = true,
                        modelId = selectedModel.id,
                        modelDisplayName = selectedModel.displayName,
                        responseTimeMs = responseTimeMs
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errorMessage,
                            isLoading = false,
                            error = error.message
                        )
                    }
                    saveHistory()
                }
            )
        }
    }

    /**
     * Очищает историю чата и сбрасывает хранилище.
     */
    fun onClearHistory() {
        messageCounter = 0L
        conversationSummary = null
        _uiState.update { it.copy(
            messages = emptyList(),
            error = null,
            conversationStats = ConversationTokensStats(),
            estimatedInputTokens = 0,
            conversationSummary = null,
            isSummarizing = false,
            isSummaryExpanded = false
        ) }
        chatHistoryRepository.clearHistory()
    }

    /**
     * Очищает текущую ошибку.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Переключает раскрытие/скрытие текста резюме.
     */
    fun onToggleSummaryExpanded() {
        _uiState.update { it.copy(isSummaryExpanded = !it.isSummaryExpanded) }
    }

    /**
     * Проверяет условие триггера суммаризации и запускает её в фоне.
     * Условие: totalValid - covered - N >= N
     * (за пределами окна накопилось N несуммаризированных сообщений).
     */
    private fun checkAndTriggerSummarization() {
        val state = _uiState.value
        val n = state.requestConfig.contextWindowSize
        val totalValid = state.messages.count { !it.isError }
        val covered = conversationSummary?.coveredMessageCount ?: 0

        if (totalValid - covered - n >= n) {
            viewModelScope.launch {
                performSummarization()
            }
        }
    }

    /**
     * Выполняет фоновую суммаризацию: берёт сообщения за пределами окна,
     * отправляет запрос на суммаризацию и сохраняет результат.
     */
    private suspend fun performSummarization() {
        if (_uiState.value.isSummarizing) return
        _uiState.update { it.copy(isSummarizing = true) }

        try {
            val state = _uiState.value
            val n = state.requestConfig.contextWindowSize
            val allValid = state.messages.filter { !it.isError }
            val previousCovered = conversationSummary?.coveredMessageCount ?: 0

            // Сообщения за пределами окна
            val outsideWindow = allValid.dropLast(n)
            // Из них — новые, не покрытые предыдущим резюме
            val newBatch = outsideWindow.drop(previousCovered)

            if (newBatch.isEmpty()) {
                _uiState.update { it.copy(isSummarizing = false) }
                return
            }

            val existingSummary = conversationSummary?.summaryText
            val prompt = buildSummarizationPrompt(existingSummary, newBatch)

            // Конфиг для суммаризации: temperature=0, maxTokens=2048, FREE_TEXT, та же модель
            val summaryConfig = state.requestConfig.copy(
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
                null // Не передаём summary в запрос на суммаризацию — иначе рекурсия
            )

            result.fold(
                onSuccess = { response ->
                    val summaryText = response.content
                        .filter { it.type == "text" }
                        .joinToString("\n") { it.text }

                    val newSummary = ConversationSummary(
                        summaryText = summaryText,
                        coveredMessageCount = outsideWindow.size,
                        createdAt = messageCounter,
                        modelId = state.requestConfig.selectedModel.id
                    )
                    conversationSummary = newSummary
                    chatHistoryRepository.saveSummary(newSummary)
                    _uiState.update { it.copy(
                        isSummarizing = false,
                        conversationSummary = newSummary
                    ) }
                    println("✅ Суммаризация завершена: покрыто ${outsideWindow.size} сообщений")
                },
                onFailure = { error ->
                    println("❌ Ошибка суммаризации: ${error.message}")
                    _uiState.update { it.copy(isSummarizing = false) }
                }
            )
        } catch (e: Exception) {
            println("❌ Ошибка суммаризации: ${e.message}")
            _uiState.update { it.copy(isSummarizing = false) }
        }
    }

    /**
     * Формирует промпт для суммаризации диалога.
     */
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

    /**
     * Приближённая оценка входных токенов до отправки запроса.
     * Учитывает system prompt, контекстное окно (N последних сообщений), summary, текущий ввод.
     * ~3.5 символа ≈ 1 токен (компромисс между кириллицей и латиницей).
     * +30 символов на оверхед форматирования каждого сообщения (role, структура JSON).
     */
    private fun estimateInputTokens(
        messages: List<ChatMessage>,
        currentInput: String,
        config: ChatRequestConfig
    ): Int {
        if (currentInput.isBlank()) return 0
        var chars = 0

        // Summary в system prompt (если есть и окно активно)
        val allValid = messages.filter { !it.isError }
        val contextWindowSize = config.contextWindowSize
        if (allValid.size > contextWindowSize) {
            conversationSummary?.summaryText?.let { chars += it.length + 60 }
        }

        // System prompt (format)
        when (config.responseFormatMode) {
            ResponseFormatMode.FREE_TEXT -> {}
            ResponseFormatMode.STRUCTURED_HINT ->
                config.formatHint.ifBlank { null }?.let { chars += it.length }
            ResponseFormatMode.STRUCTURED_JSON -> chars += 120
        }

        // Контекстное окно: только последние N сообщений
        val windowMessages = if (allValid.size > contextWindowSize) {
            allValid.takeLast(contextWindowSize)
        } else {
            allValid
        }
        for (msg in windowMessages) {
            chars += msg.text.length + 30
        }

        // Текущий ввод
        chars += currentInput.length + 30
        return (chars / 3.5).toInt().coerceAtLeast(0)
    }
}
