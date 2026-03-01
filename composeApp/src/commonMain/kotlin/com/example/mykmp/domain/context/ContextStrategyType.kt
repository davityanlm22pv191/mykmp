package com.example.mykmp.domain.context

import com.example.mykmp.data.api.ClaudeMessageRequest
import kotlinx.serialization.Serializable

/**
 * Тип стратегии управления контекстом.
 */
enum class ContextStrategyType {
    SLIDING_WINDOW,
    STICKY_FACTS,
    BRANCHING,
    ROLLING_SUMMARY;

    /** Русское имя для UI. */
    fun displayName(): String = when (this) {
        SLIDING_WINDOW -> "Скользящее окно"
        STICKY_FACTS -> "Ключевые факты"
        BRANCHING -> "Ветвление"
        ROLLING_SUMMARY -> "Суммаризация"
    }

    /** Русское описание для UI. */
    fun description(): String = when (this) {
        SLIDING_WINDOW -> "Отправляет только последние N сообщений. Старые отбрасываются без сохранения."
        STICKY_FACTS -> "Извлекает ключевые факты из разговора. Отправляет факты + последние N сообщений."
        BRANCHING -> "Создаёт точки ветвления. Каждая ветка — независимый поток диалога."
        ROLLING_SUMMARY -> "Старые сообщения заменяются кратким резюме. Последние N отправляются полностью."
    }
}

/**
 * Результат построения контекста для отправки в API.
 */
data class ContextResult(
    val messagesToSend: List<ClaudeMessageRequest>,
    val systemPromptAddition: String?
)
