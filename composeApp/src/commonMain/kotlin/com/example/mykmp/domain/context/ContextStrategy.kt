package com.example.mykmp.domain.context

import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig

/**
 * Интерфейс стратегии управления контекстом.
 * Каждая стратегия знает, как:
 * 1) Построить контекст из сообщений для отправки в API
 * 2) Обработать пост-факт (после получения ответа)
 * 3) Загрузить/сохранить своё состояние
 */
interface ContextStrategy {
    val type: ContextStrategyType
    val displayName: String
    val description: String

    /**
     * Построить контекст: выбрать сообщения и сформировать
     * дополнение к system prompt (факты, summary и т.д.)
     */
    suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult

    /**
     * Хук после успешного ответа API.
     * - Rolling Summary: суммаризация
     * - Sticky Facts: экстракция фактов
     * - Branching: авто-checkpoint
     * - Sliding Window: no-op
     */
    suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    )

    /** Загрузить персистентное состояние. */
    fun loadState()

    /** Сбросить состояние (при очистке истории). */
    fun clearState()
}
