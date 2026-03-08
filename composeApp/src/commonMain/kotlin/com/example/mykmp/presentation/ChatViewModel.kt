package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.data.api.ApiAccessChecker
import com.example.mykmp.domain.context.ContextManager
import com.example.mykmp.domain.context.ContextStrategyType
import com.example.mykmp.domain.context.ConversationBranch
import com.example.mykmp.domain.memory.LongTermFact
import com.example.mykmp.domain.memory.MemoryCategory
import com.example.mykmp.domain.memory.MemoryManager
import com.example.mykmp.domain.memory.TaskMemory
import com.example.mykmp.domain.model.*
import com.example.mykmp.domain.profile.ProfileManager
import com.example.mykmp.domain.profile.ProfileSuggestion
import com.example.mykmp.domain.profile.UserProfile
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository
import com.example.mykmp.domain.task.ExpectedAction
import com.example.mykmp.domain.task.Task
import com.example.mykmp.domain.task.TaskStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

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
    val activeBranchId: String = "main",
    // Панель памяти
    val isMemoryPanelExpanded: Boolean = false,
    // Рабочая память
    val taskList: List<TaskMemory> = emptyList(),
    val activeTaskId: String? = null,
    // Долговременная память
    val longTermFacts: List<LongTermFact> = emptyList(),
    val memorySearchQuery: String = "",
    val memorySearchResults: List<LongTermFact> = emptyList(),
    // Предложенные факты из StickyFacts → LongTerm (ожидают подтверждения)
    val suggestedFacts: Map<String, String> = emptyMap(),
    val hasSuggestedFacts: Boolean = false,
    // Панель профилей
    val isProfilePanelExpanded: Boolean = false,
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String? = null,
    // Предложения обновления профиля (авто-экстракция)
    val profileSuggestion: ProfileSuggestion? = null,
    val hasProfileSuggestion: Boolean = false,
    // Активная задача FSM
    val activeTask: Task? = null,
    val taskSuggestion: ExpectedAction? = null,
    val hasTaskSuggestion: Boolean = false
)

/**
 * ViewModel чата. Управляет состоянием UI и взаимодействием с Claude API.
 * Делегирует управление контекстом в ContextManager.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val contextManager: ContextManager,
    private val memoryManager: MemoryManager,
    private val profileManager: ProfileManager,
    private val taskStateMachine: TaskStateMachine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val apiAccessChecker = ApiAccessChecker()
    private var messageCounter = 0L

    init {
        setupStrategyCallbacks()
        setupProfileCallbacks()
        setupTaskCallbacks()
        loadHistory()
        loadSettings()
        initContextStrategy()
        syncMemoryState()
        syncProfileState()
        syncTaskState()
        checkApiAccess()
    }

    /**
     * Устанавливает callback'и для ProfileManager.
     */
    private fun setupProfileCallbacks() {
        profileManager.onStateChanged = { syncProfileState() }
        profileManager.onSuggestionReady = { suggestion -> onProfileSuggestion(suggestion) }
    }

    /**
     * Устанавливает callback'и для TaskStateMachine.
     */
    private fun setupTaskCallbacks() {
        taskStateMachine.onStateChanged = { syncTaskState() }
        taskStateMachine.onSuggestionReady = { action -> onTaskSuggestion(action) }
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
        // Предложение извлечённых фактов для сохранения в долговременную память
        contextManager.stickyFactsStrategy.onFactsExtracted = { facts ->
            onSuggestFacts(facts)
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
        _uiState.update {
            it.copy(
                isSettingsExpanded = !it.isSettingsExpanded,
                isMemoryPanelExpanded = false,
                isProfilePanelExpanded = false
            )
        }
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

            // Комбинируем system prompt: профиль → стратегия → память → задача
            val profilePrompt = profileManager.buildPersonalizationPrompt()
            val memoryPrompt = memoryManager.buildMemoryPrompt()
            val taskPrompt = taskStateMachine.getSystemPromptAddition()
            val combinedSystemAddition = listOfNotNull(
                profilePrompt,
                contextResult.systemPromptAddition,
                memoryPrompt,
                taskPrompt
            ).joinToString("\n\n").ifBlank { null }

            // Замеряем время ответа
            val timeMark = TimeSource.Monotonic.markNow()

            val result = chatRepository.sendMessage(
                contextResult.messagesToSend,
                currentState.requestConfig,
                combinedSystemAddition
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

                    // Пост-обработка ответа в FSM задачи
                    taskStateMachine.onMessageReceived(assistantText)

                    // Авто-экстракция предпочтений для профиля
                    profileManager.analyzeAndSuggest(
                        _uiState.value.messages,
                        currentState.requestConfig,
                        chatRepository
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

    // === Панель памяти ===

    /**
     * Переключает видимость панели памяти.
     */
    fun onToggleMemoryPanel() {
        _uiState.update {
            it.copy(
                isMemoryPanelExpanded = !it.isMemoryPanelExpanded,
                isSettingsExpanded = false,
                isProfilePanelExpanded = false
            )
        }
    }

    // === Рабочая память ===

    fun onCreateTask(taskId: String, name: String) {
        memoryManager.createTask(taskId, name)
        syncMemoryState()
    }

    fun onSetActiveTask(taskId: String) {
        memoryManager.setActiveTask(taskId)
        syncMemoryState()
    }

    fun onAddWorkingEntry(taskId: String, key: String, value: String) {
        memoryManager.addWorkingEntry(taskId, key, value)
        syncMemoryState()
    }

    fun onRemoveWorkingEntry(taskId: String, key: String) {
        memoryManager.removeWorkingEntry(taskId, key)
        syncMemoryState()
    }

    fun onDeleteTask(taskId: String) {
        memoryManager.deleteTask(taskId)
        syncMemoryState()
    }

    // === Долговременная память ===

    fun onAddFact(category: String, key: String, value: String) {
        memoryManager.addFact(category, key, value, source = "manual")
        syncMemoryState()
    }

    fun onUpdateFact(factId: String, newValue: String) {
        memoryManager.updateFact(factId, newValue)
        syncMemoryState()
    }

    fun onDeleteFact(factId: String) {
        memoryManager.deleteFact(factId)
        syncMemoryState()
    }

    fun onSearchMemory(query: String) {
        val results = memoryManager.searchFacts(query)
        _uiState.update { it.copy(memorySearchQuery = query, memorySearchResults = results) }
    }

    // === Предложенные факты (StickyFacts → LongTerm) ===

    /**
     * Обработка предложенных фактов из StickyFactsStrategy.
     */
    fun onSuggestFacts(facts: Map<String, String>) {
        if (facts.isEmpty()) return
        _uiState.update { it.copy(suggestedFacts = facts, hasSuggestedFacts = true) }
    }

    /**
     * Подтверждает факт из StickyFacts → сохраняет в долговременную память.
     */
    fun onConfirmFact(stickyKey: String, stickyValue: String) {
        val category = mapStickyKeyToCategory(stickyKey)
        memoryManager.addFact(category, stickyKey, stickyValue, source = "confirmed")
        // Убираем подтверждённый из списка предложений
        val remaining = _uiState.value.suggestedFacts.filterKeys { it != stickyKey }
        _uiState.update { it.copy(suggestedFacts = remaining, hasSuggestedFacts = remaining.isNotEmpty()) }
        syncMemoryState()
    }

    /**
     * Отклоняет предложенный факт.
     */
    fun onDismissFact(stickyKey: String) {
        val remaining = _uiState.value.suggestedFacts.filterKeys { it != stickyKey }
        _uiState.update { it.copy(suggestedFacts = remaining, hasSuggestedFacts = remaining.isNotEmpty()) }
    }

    /**
     * Отклоняет все предложенные факты.
     */
    fun onDismissAllSuggested() {
        _uiState.update { it.copy(suggestedFacts = emptyMap(), hasSuggestedFacts = false) }
    }

    /**
     * Синхронизирует состояние памяти → UI state.
     */
    private fun syncMemoryState() {
        val taskList = memoryManager.getTaskList()
        val activeTask = memoryManager.getActiveTask()
        val facts = memoryManager.getAllFacts()
        _uiState.update {
            it.copy(
                taskList = taskList,
                activeTaskId = activeTask?.taskId,
                longTermFacts = facts
            )
        }
    }

    /**
     * Маппинг ключей StickyFacts → категории долговременной памяти.
     */
    private fun mapStickyKeyToCategory(stickyKey: String): String = when (stickyKey) {
        "preferences" -> MemoryCategory.PREFERENCES.name
        "decisions" -> MemoryCategory.DECISIONS.name
        "techStack" -> MemoryCategory.KNOWLEDGE.name
        "userGoal" -> MemoryCategory.KNOWLEDGE.name
        "constraints" -> MemoryCategory.KNOWLEDGE.name
        "openQuestions" -> MemoryCategory.KNOWLEDGE.name
        else -> MemoryCategory.KNOWLEDGE.name
    }

    // === Панель профилей ===

    fun onToggleProfilePanel() {
        _uiState.update {
            it.copy(
                isProfilePanelExpanded = !it.isProfilePanelExpanded,
                isSettingsExpanded = false,
                isMemoryPanelExpanded = false
            )
        }
    }

    fun onCreateProfile(displayName: String) {
        profileManager.createProfile(displayName)
        syncProfileState()
    }

    fun onSwitchProfile(profileId: String) {
        profileManager.switchProfile(profileId)
        syncProfileState()
    }

    fun onUpdateProfile(profileId: String, update: ProfileSuggestion) {
        profileManager.updateProfile(profileId, update)
        syncProfileState()
    }

    fun onDeleteProfile(profileId: String) {
        profileManager.deleteProfile(profileId)
        syncProfileState()
    }

    fun onConfirmProfileSuggestion() {
        val suggestion = _uiState.value.profileSuggestion ?: return
        val activeId = _uiState.value.activeProfileId ?: return
        profileManager.updateProfile(activeId, suggestion)
        _uiState.update { it.copy(profileSuggestion = null, hasProfileSuggestion = false) }
        syncProfileState()
    }

    fun onDismissProfileSuggestion() {
        _uiState.update { it.copy(profileSuggestion = null, hasProfileSuggestion = false) }
    }

    // Методы обновления конкретных полей активного профиля через ProfileSuggestion-подобный подход
    fun onAddLanguage(profileId: String, language: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        if (!profile.preferredLanguages.contains(language)) {
            profileManager.updateProfile(profileId, ProfileSuggestion(preferredLanguages = listOf(language)))
        }
    }

    fun onRemoveLanguage(profileId: String, language: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        val updated = profile.copy(preferredLanguages = profile.preferredLanguages.filter { it != language })
        replaceProfile(profileId, updated)
    }

    fun onAddArchitecture(profileId: String, arch: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        if (!profile.architecturePrefs.contains(arch)) {
            profileManager.updateProfile(profileId, ProfileSuggestion(architecturePrefs = listOf(arch)))
        }
    }

    fun onRemoveArchitecture(profileId: String, arch: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        val updated = profile.copy(architecturePrefs = profile.architecturePrefs.filter { it != arch })
        replaceProfile(profileId, updated)
    }

    fun onUpdateResponseStyle(profileId: String, style: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        replaceProfile(profileId, profile.copy(responseStyle = style))
    }

    fun onUpdateTone(profileId: String, tone: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        replaceProfile(profileId, profile.copy(tone = tone))
    }

    fun onUpdateExpertise(profileId: String, level: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        replaceProfile(profileId, profile.copy(expertiseLevel = level))
    }

    fun onAddBudgetLimit(profileId: String, key: String, value: String) {
        profileManager.updateProfile(profileId, ProfileSuggestion(budgetLimits = mapOf(key to value)))
    }

    fun onRemoveBudgetLimit(profileId: String, key: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        replaceProfile(profileId, profile.copy(budgetLimits = profile.budgetLimits.filterKeys { it != key }))
    }

    fun onAddTimeConstraint(profileId: String, key: String, value: String) {
        profileManager.updateProfile(profileId, ProfileSuggestion(timeConstraints = mapOf(key to value)))
    }

    fun onRemoveTimeConstraint(profileId: String, key: String) {
        val profile = profileManager.getProfiles().find { it.id == profileId } ?: return
        replaceProfile(profileId, profile.copy(timeConstraints = profile.timeConstraints.filterKeys { it != key }))
    }

    /**
     * Синхронизирует состояние профилей → UI state.
     */
    private fun syncProfileState() {
        _uiState.update {
            it.copy(
                profiles = profileManager.getProfiles(),
                activeProfileId = profileManager.getActiveProfile()?.id
            )
        }
    }

    /**
     * Обработка предложения обновления профиля из авто-экстракции.
     */
    private fun onProfileSuggestion(suggestion: ProfileSuggestion) {
        if (suggestion.isEmpty()) return
        _uiState.update { it.copy(profileSuggestion = suggestion, hasProfileSuggestion = true) }
    }

    /**
     * Полная замена профиля (для операций удаления отдельных элементов).
     */
    private fun replaceProfile(profileId: String, updated: UserProfile) {
        profileManager.replaceProfile(updated)
        syncProfileState()
    }

    // === Задачи FSM ===

    fun onAdvanceTask() {
        viewModelScope.launch {
            try { taskStateMachine.advance() } catch (e: Exception) {
                println("❌ onAdvanceTask: ${e.message}")
            }
        }
    }

    fun onPauseTask() {
        viewModelScope.launch {
            try { taskStateMachine.pause() } catch (e: Exception) {
                println("❌ onPauseTask: ${e.message}")
            }
        }
    }

    fun onResumeTask() {
        viewModelScope.launch {
            try { taskStateMachine.resume() } catch (e: Exception) {
                println("❌ onResumeTask: ${e.message}")
            }
        }
    }

    fun onDismissTaskSuggestion() {
        _uiState.update { it.copy(taskSuggestion = null, hasTaskSuggestion = false) }
    }

    private fun syncTaskState() {
        _uiState.update { it.copy(activeTask = taskStateMachine.activeTask) }
    }

    private fun onTaskSuggestion(action: ExpectedAction) {
        _uiState.update { it.copy(taskSuggestion = action, hasTaskSuggestion = true) }
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
