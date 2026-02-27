package com.example.mykmp.domain.model

import kotlinx.serialization.Serializable

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
@Serializable
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
@Serializable
data class TokensUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * Агрегированная статистика токенов по всей беседе.
 * Не сериализуется — пересчитывается из списка сообщений.
 */
data class ConversationTokensStats(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCostUsd: Double = 0.0
) {
    val totalTokens: Int get() = totalInputTokens + totalOutputTokens
}

/**
 * Вычисляет агрегированную статистику токенов из списка сообщений.
 */
fun List<ChatMessage>.toConversationStats(): ConversationTokensStats {
    var input = 0
    var output = 0
    var cost = 0.0
    for (msg in this) {
        msg.tokensUsage?.let { input += it.inputTokens; output += it.outputTokens }
        msg.costUsd?.let { cost += it }
    }
    return ConversationTokensStats(input, output, cost)
}
