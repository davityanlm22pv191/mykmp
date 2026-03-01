package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.data.api.ApiAccessChecker
import com.example.mykmp.domain.context.ConversationBranch
import com.example.mykmp.domain.context.ContextManager
import com.example.mykmp.domain.context.ContextStrategyType
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
    val showVpnBanner: Boolean = false,
    val conversationStats: ConversationTokensStats = ConversationTokensStats(),
    val estimatedInputTokens: Int = 0,
    // Rolling Summary state (от стратегии)
    val conversationSummary: ConversationSummary? = null,
    val isSummarizing: Boolean = false,
    val isSummaryExpanded: Boolean = false,
    // Контекстные стратегии
    val contextStrategyType: ContextStrategyType = ContextStrategyType.ROLLING_SUMMARY,
    val availableStrategies: List<ContextStrategyType> = emptyList(),
    // Sticky Facts state
    val stickyFacts: Map<String, String> = emptyMap(),
    val isExtractingFacts: Boolean = false,
    val isFactsExpanded: Boolean = false,
    // Branching state
    val branches: List<ConversationBranch> = emptyList(),
    val activeBranchId: String = "main"
)

/**
 * ViewModel чата. Управляет состоянием UI и взаимодействием с Claude API.
 * Делегирует управление контекстом в ContextManager.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val contextManager: ContextManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val apiAccessChecker = ApiAccessChecker()
    private var messageCounter = 0L

    init {
        setupStrategyCallbacks()
        loadHistory()
        loadSettings()
        initContextStrategy()
        checkApiAccess()
    }

    /**
     * Устанавливает callback'и для стратегий, чтобы обновлять UI при изменении состояния.
     */
    private fun setupStrategyCallbacks() {
        contextManager.rollingSummaryStrategy.onStateChanged = {
            syncStrategyState()
        }
        contextManager.stickyFactsStrategy.onStateChanged = {
            syncStrategyState()
        }
        contextManager.branchingStrategy.onStateChanged = {
            syncStrategyState()
        }
    }

    /**
     * Инициализирует активную стратегию из сохранённых настроек.
     */
    private fun initContextStrategy() {
        val strategyType = _uiState.value.requestConfig.contextStrategyType
        contextManager.initWithType(strategyType)
        _uiState.update { it.copy(availableStrategies = contextManager.allStrategyTypes) }
        syncStrategyState()
    }

    /**
     * Синхронизирует состояние активной стратегии → UI state.
     */
    private fun syncStrategyState() {
        val strategy = contextManager.activeStrategy
        when (strategy.type) {
            ContextStrategyType.ROLLING_SUMMARY -> {
                val rs = contextManager.rollingSummaryStrategy
                _uiState.update {
                    it.copy(
                        conversationSummary = rs.currentSummary,
                        isSummarizing = rs.isSummarizing,
                        stickyFacts = emptyMap(),
                        isExtractingFacts = false,
                        contextStrategyType = strategy.type
                    )
                }
            }
            ContextStrategyType.STICKY_FACTS -> {
                val sf = contextManager.stickyFactsStrategy
                _uiState.update {
                    it.copy(
                        conversationSummary = null,
                        isSummarizing = false,
                        stickyFacts = sf.currentFacts,
                        isExtractingFacts = sf.isExtracting,
                        contextStrategyType = strategy.type
                    )
                }
            }
            ContextStrategyType.BRANCHING -> {
                val bs = contextManager.branchingStrategy
                _uiState.update {
                    it.copy(
                        conversationSummary = null,
                        isSummarizing = false,
                        stickyFacts = emptyMap(),
                        isExtractingFacts = false,
                        branches = bs.branches,
                        activeBranchId = bs.activeBranchId,
                        contextStrategyType = strategy.type
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        conversationSummary = null,
                        isSummarizing = false,
                        stickyFacts = emptyMap(),
                        isExtractingFacts = false,
                        branches = emptyList(),
                        activeBranchId = "main",
                        contextStrategyType = strategy.type
                    )
                }
            }
        }
    }

    /**
     * Переключает активную стратегию контекста.
     */
    fun onSwitchStrategy(type: ContextStrategyType) {
        contextManager.switchStrategy(type)
        val config = _uiState.value.requestConfig.copy(contextStrategyType = type)
        _uiState.update { it.copy(requestConfig = config) }
        chatHistoryRepository.saveSettings(config)
        syncStrategyState()
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

    private fun checkApiAccess() {
        viewModelScope.launch {
            val blocked = apiAccessChecker.isGeoBlocked()
            _uiState.update { it.copy(
                isGeoBlocked = blocked,
                showVpnBanner = blocked
            ) }
        }
    }

    /**
     * Скрывает всплывающий баннер VPN (auto-dismiss или ручное закрытие).
     */
    fun dismissVpnBanner() {
        _uiState.update { it.copy(showVpnBanner = false) }
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
     * Делегирует построение контекста в ContextManager.
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

            // Делегируем построение контекста ContextManager
            val contextResult = contextManager.buildContext(
                currentState.messages,
                currentState.requestConfig
            )

            // Замеряем время ответа
            val timeMark = TimeSource.Monotonic.markNow()

            val result = chatRepository.sendMessage(
                contextResult.messagesToSend,
                currentState.requestConfig,
                contextResult.systemPromptAddition
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

                    // Делегируем пост-обработку ContextManager (суммаризация, экстракция и т.д.)
                    contextManager.onMessageReceived(
                        _uiState.value.messages,
                        currentState.requestConfig
                    )
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
        _uiState.update { it.copy(
            messages = emptyList(),
            error = null,
            conversationStats = ConversationTokensStats(),
            estimatedInputTokens = 0,
            conversationSummary = null,
            isSummarizing = false,
            isSummaryExpanded = false,
            stickyFacts = emptyMap(),
            isExtractingFacts = false,
            isFactsExpanded = false,
            branches = emptyList(),
            activeBranchId = "main"
        ) }
        chatHistoryRepository.clearHistory()
        // Очищаем состояние ВСЕХ стратегий, не только активной
        contextManager.clearAllStates()
        syncStrategyState()
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
     * Переключает раскрытие/скрытие панели фактов.
     */
    fun onToggleFactsExpanded() {
        _uiState.update { it.copy(isFactsExpanded = !it.isFactsExpanded) }
    }

    /**
     * Создаёт новую ветку диалога от текущего момента.
     */
    fun onCreateBranch(name: String) {
        val messages = _uiState.value.messages
        contextManager.branchingStrategy.createBranch(name, messages)
        syncStrategyState()
    }

    /**
     * Переключает активную ветку диалога.
     */
    fun onSwitchBranch(branchId: String) {
        contextManager.branchingStrategy.switchBranch(branchId)
        syncStrategyState()
    }

    /**
     * Удаляет ветку диалога.
     */
    fun onDeleteBranch(branchId: String) {
        contextManager.branchingStrategy.deleteBranch(branchId)
        syncStrategyState()
    }

    /**
     * Приближённая оценка входных токенов до отправки запроса.
     * Учитывает активную стратегию, system prompt, контекстное окно, текущий ввод.
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

        val allValid = messages.filter { !it.isError }
        val contextWindowSize = config.contextWindowSize

        // Strategy-specific additions
        when (config.contextStrategyType) {
            ContextStrategyType.ROLLING_SUMMARY -> {
                if (allValid.size > contextWindowSize) {
                    contextManager.rollingSummaryStrategy.currentSummary?.summaryText?.let {
                        chars += it.length + 60
                    }
                }
            }
            ContextStrategyType.STICKY_FACTS -> {
                val facts = contextManager.stickyFactsStrategy.currentFacts
                if (facts.isNotEmpty()) {
                    for ((_, value) in facts) {
                        chars += value.length + 40 // label + formatting
                    }
                }
            }
            else -> { /* другие стратегии будут добавлены позже */ }
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
