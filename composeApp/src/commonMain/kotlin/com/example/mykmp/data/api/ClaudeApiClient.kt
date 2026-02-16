package com.example.mykmp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Клиент для Claude Messages API.
 *
 * @param apiKey API-ключ Anthropic
 * @param baseUrl базовый URL API (по умолчанию https://api.anthropic.com)
 * @param model идентификатор модели Claude
 * @param maxTokens максимальное количество токенов в ответе
 */
class ClaudeApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val model: String = "claude-sonnet-4-20250514",
    private val maxTokens: Int = 4096
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(this@ClaudeApiClient.json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    /**
     * Отправляет сообщения в Claude API и возвращает ответ.
     *
     * @param conversationHistory полная история диалога (user/assistant)
     * @return Result с ответом Claude или ошибкой
     */
    /**
     * Формирует cURL-команду для отладки.
     * API-ключ маскируется: показываются только первые 10 и последние 4 символа.
     */
    private fun buildCurlCommand(url: String, requestBody: String): String {
        val maskedKey = if (apiKey.length > 14) {
            apiKey.take(10) + "..." + apiKey.takeLast(4)
        } else {
            "***"
        }
        return buildString {
            appendLine("┌─── cURL ───")
            appendLine("curl -X POST '$url' \\")
            appendLine("  -H 'content-type: application/json' \\")
            appendLine("  -H 'x-api-key: $maskedKey' \\")
            appendLine("  -H 'anthropic-version: 2023-06-01' \\")
            appendLine("  -d '$requestBody'")
            appendLine("└────────────")
        }
    }

    suspend fun sendMessage(
        conversationHistory: List<ClaudeMessageRequest>
    ): Result<ClaudeResponse> {
        return try {
            val request = ClaudeRequest(
                model = model,
                maxTokens = maxTokens,
                messages = conversationHistory
            )

            val url = "$baseUrl/v1/messages"
            val requestBody = json.encodeToString(ClaudeRequest.serializer(), request)

            // Логируем cURL для отладки
            println(buildCurlCommand(url, requestBody))

            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(request)
            }

            val statusCode = response.status.value
            val responseBody = response.bodyAsText()
            println("┌─── Response ───")
            println("HTTP $statusCode")
            println(responseBody.take(500) + if (responseBody.length > 500) "...(truncated)" else "")
            println("└────────────────")

            if (response.status.isSuccess()) {
                val parsed = json.decodeFromString<ClaudeResponse>(responseBody)
                Result.success(parsed)
            } else {
                val errorMessage = try {
                    json.decodeFromString<ClaudeErrorResponse>(responseBody).error.message
                } catch (_: Exception) {
                    "HTTP $statusCode: $responseBody"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        httpClient.close()
    }
}
