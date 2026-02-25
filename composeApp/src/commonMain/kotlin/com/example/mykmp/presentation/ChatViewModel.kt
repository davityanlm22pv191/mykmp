package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.data.api.ApiAccessChecker
import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.TokensUsage
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
    val isGeoBlocked: Boolean = false
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

    init {
        loadHistory()
        loadSettings()
        checkApiAccess()
    }

    private fun loadHistory() {
        val saved = chatHistoryRepository.loadHistory()
        if (saved.isNotEmpty()) {
            messageCounter = saved.maxOf { it.timestamp }
            _uiState.update { it.copy(messages = saved) }
        }
    }

    private fun loadSettings() {
        val saved = chatHistoryRepository.loadSettings()
        if (saved != null) {
            _uiState.update { it.copy(requestConfig = saved) }
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
        _uiState.update { it.copy(inputText = text) }
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
        _uiState.update { it.copy(requestConfig = config) }
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
                isSettingsExpanded = false
            )
        }

        viewModelScope.launch {
            val currentState = _uiState.value
            val selectedModel = currentState.requestConfig.selectedModel

            // Конвертируем историю чата в формат Claude API,
            // пропуская сообщения-ошибки
            val conversationHistory = currentState.messages
                .filter { !it.isError }
                .map { msg ->
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
                currentState.requestConfig
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
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false
                        )
                    }
                    saveHistory()
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
        _uiState.update { it.copy(messages = emptyList(), error = null) }
        chatHistoryRepository.clearHistory()
    }

    /**
     * Очищает текущую ошибку.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
