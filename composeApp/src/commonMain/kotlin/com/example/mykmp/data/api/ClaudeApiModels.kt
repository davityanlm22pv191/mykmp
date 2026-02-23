package com.example.mykmp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// === Модели запроса к Claude Messages API ===

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeMessageRequest>,
    val system: String? = null,
    @SerialName("stop_sequences")
    val stopSequences: List<String>? = null,
    val temperature: Double? = null
)

@Serializable
data class ClaudeMessageRequest(
    val role: String,    // "user" или "assistant"
    val content: String
)

// === Модели ответа Claude Messages API ===

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeContentBlock(
    val type: String,   // "text"
    val text: String
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

// === Модель ошибки Claude API ===

@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeErrorDetail
)

@Serializable
data class ClaudeErrorDetail(
    val type: String,
    val message: String
)
