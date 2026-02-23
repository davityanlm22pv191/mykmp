package com.example.mykmp.data.repository

import com.example.mykmp.data.api.ClaudeApiClient
import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.data.api.ClaudeResponse
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.repository.ChatRepository

/**
 * Реализация ChatRepository, делегирующая запросы в ClaudeApiClient.
 */
class ChatRepositoryImpl(
    private val apiClient: ClaudeApiClient
) : ChatRepository {

    override suspend fun sendMessage(
        conversationHistory: List<ClaudeMessageRequest>,
        config: ChatRequestConfig
    ): Result<ClaudeResponse> {
        return apiClient.sendMessage(
            conversationHistory,
            config.copy(stopSequences = config.stopSequences.filter { sequence -> sequence.isNotBlank() })
        )
    }
}
