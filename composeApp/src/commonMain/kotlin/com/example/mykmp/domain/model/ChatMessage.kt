package com.example.mykmp.domain.model

/**
 * Сообщение в чате.
 *
 * @param id уникальный идентификатор сообщения
 * @param role роль отправителя (USER или ASSISTANT)
 * @param text текст сообщения
 * @param timestamp временная метка (порядковый номер)
 * @param isError является ли сообщение ошибкой (для визуального выделения)
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val timestamp: Long,
    val isError: Boolean = false
) {
    enum class Role {
        USER, ASSISTANT
    }
}
