package com.example.mykmp.domain.repository

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.data.api.ClaudeResponse

/**
 * Репозиторий для взаимодействия с Claude API.
 * Абстрагирует детали сетевого слоя от презентационного.
 */
interface ChatRepository {
    /**
     * Отправляет историю диалога в Claude API.
     *
     * @param conversationHistory список сообщений в формате Claude API
     * @return Result с ответом или ошибкой
     */
    suspend fun sendMessage(
        conversationHistory: List<ClaudeMessageRequest>
    ): Result<ClaudeResponse>
}
