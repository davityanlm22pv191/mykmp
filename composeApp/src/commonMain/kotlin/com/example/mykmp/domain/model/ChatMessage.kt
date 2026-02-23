package com.example.mykmp.domain.model

/**
 * Сообщение в чате.
 *
 * @param id уникальный идентификатор сообщения
 * @param role роль отправителя (USER или ASSISTANT)
 * @param text текст сообщения
 * @param timestamp временная метка (порядковый номер)
 * @param isError является ли сообщение ошибкой (для визуального выделения)
 * @param modelId идентификатор модели, которая сгенерировала ответ (только для ASSISTANT)
 * @param modelDisplayName отображаемое имя модели (только для ASSISTANT)
 * @param responseTimeMs время ответа в миллисекундах (только для ASSISTANT)
 * @param tokensUsage информация об использованных токенах (только для ASSISTANT)
 * @param costUsd ориентировочная стоимость запроса в USD (только для ASSISTANT)
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val modelId: String? = null,
    val modelDisplayName: String? = null,
    val responseTimeMs: Long? = null,
    val tokensUsage: TokensUsage? = null,
    val costUsd: Double? = null
) {
    enum class Role {
        USER, ASSISTANT
    }
}

/**
 * Информация об использованных токенах.
 */
data class TokensUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}
