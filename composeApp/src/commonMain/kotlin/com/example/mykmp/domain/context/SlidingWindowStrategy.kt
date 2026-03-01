package com.example.mykmp.domain.context

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig

/**
 * Стратегия скользящего окна: отправляет только последние N сообщений.
 * Старые сообщения отбрасываются без сохранения.
 */
class SlidingWindowStrategy : ContextStrategy {

    override val type = ContextStrategyType.SLIDING_WINDOW
    override val displayName = type.displayName()
    override val description = type.description()

    override suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult {
        val validMessages = messages.filter { !it.isError }
        val window = validMessages.takeLast(config.contextWindowSize)

        val apiMessages = window.map { msg ->
            ClaudeMessageRequest(
                role = when (msg.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                },
                content = msg.text
            )
        }

        return ContextResult(
            messagesToSend = apiMessages,
            systemPromptAddition = null
        )
    }

    override suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        // No-op: скользящее окно не требует пост-обработки
    }

    override fun loadState() {
        // Нет состояния
    }

    override fun clearState() {
        // Нет состояния
    }
}
