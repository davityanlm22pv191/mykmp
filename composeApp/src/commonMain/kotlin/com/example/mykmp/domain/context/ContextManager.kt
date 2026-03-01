package com.example.mykmp.domain.context

import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository

/**
 * Оркестратор стратегий контекста.
 * Держит все стратегии, делегирует активной.
 */
class ContextManager(
    private val chatRepository: ChatRepository,
    private val historyRepository: ChatHistoryRepository
) {
    private val strategies: Map<ContextStrategyType, ContextStrategy> = mapOf(
        ContextStrategyType.SLIDING_WINDOW to SlidingWindowStrategy(),
        ContextStrategyType.STICKY_FACTS to StickyFactsStrategy(chatRepository, historyRepository),
        ContextStrategyType.BRANCHING to BranchingStrategy(historyRepository),
        ContextStrategyType.ROLLING_SUMMARY to RollingSummaryStrategy(chatRepository, historyRepository)
    )

    private var _activeStrategyType = ContextStrategyType.ROLLING_SUMMARY
    val activeStrategyType: ContextStrategyType get() = _activeStrategyType
    val activeStrategy: ContextStrategy get() = strategies[_activeStrategyType]!!

    val allStrategyTypes: List<ContextStrategyType>
        get() = strategies.keys.toList()

    // Типизированный доступ к стратегиям для UI state
    val rollingSummaryStrategy: RollingSummaryStrategy
        get() = strategies[ContextStrategyType.ROLLING_SUMMARY] as RollingSummaryStrategy

    val stickyFactsStrategy: StickyFactsStrategy
        get() = strategies[ContextStrategyType.STICKY_FACTS] as StickyFactsStrategy

    val branchingStrategy: BranchingStrategy
        get() = strategies[ContextStrategyType.BRANCHING] as BranchingStrategy

    fun switchStrategy(type: ContextStrategyType) {
        if (type == _activeStrategyType) return
        if (!strategies.containsKey(type)) return
        _activeStrategyType = type
        activeStrategy.loadState()
    }

    fun initWithType(type: ContextStrategyType) {
        // Fallback на ROLLING_SUMMARY если стратегия не зарегистрирована
        _activeStrategyType = if (strategies.containsKey(type)) type
            else ContextStrategyType.ROLLING_SUMMARY
        activeStrategy.loadState()
    }

    suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult {
        return activeStrategy.buildContext(messages, config)
    }

    suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        activeStrategy.onMessageReceived(allMessages, config)
    }

    fun loadActiveState() {
        activeStrategy.loadState()
    }

    fun clearActiveState() {
        activeStrategy.clearState()
    }

    /**
     * Очищает состояние ВСЕХ стратегий (при полной очистке истории).
     */
    fun clearAllStates() {
        strategies.values.forEach { it.clearState() }
    }
}
