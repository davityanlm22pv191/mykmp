package com.example.mykmp.domain.repository

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.data.api.ClaudeResponse
import com.example.mykmp.domain.model.ChatRequestConfig

/**
 * Репозиторий для взаимодействия с Claude API.
 * Абстрагирует детали сетевого слоя от презентационного.
 */
interface ChatRepository {
    /**
     * Отправляет историю диалога в Claude API.
     *
     * @param conversationHistory список сообщений в формате Claude API
     * @param config конфигурация параметров запроса
     * @return Result с ответом или ошибкой
     */
    suspend fun sendMessage(
        conversationHistory: List<ClaudeMessageRequest>,
        config: ChatRequestConfig = ChatRequestConfig()
    ): Result<ClaudeResponse>
}
