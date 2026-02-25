package com.example.mykmp.data.api

import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.MAX_TOKENS_LIMIT
import com.example.mykmp.domain.model.ResponseFormatMode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Клиент для Claude Messages API.
 *
 * @param apiKey API-ключ Anthropic
 * @param baseUrl базовый URL API (по умолчанию https://api.anthropic.com)
 */
class ClaudeApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val httpClient = HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(this@ClaudeApiClient.json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000   // 2 минуты на весь запрос
            connectTimeoutMillis = 30_000    // 30 секунд на подключение
            socketTimeoutMillis = 120_000    // 2 минуты на чтение данных
        }
    }

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

    /**
     * Формирует system prompt на основе конфигурации формата ответа.
     */
    private fun buildSystemPrompt(config: ChatRequestConfig): String? {
        return when (config.responseFormatMode) {
            ResponseFormatMode.FREE_TEXT -> null
            ResponseFormatMode.STRUCTURED_HINT -> {
                config.formatHint.ifBlank { null }
            }
            ResponseFormatMode.STRUCTURED_JSON -> {
                "You must respond strictly in valid JSON format. " +
                    "Do not include any text outside the JSON object. " +
                    "The response should be a JSON object with relevant fields."
            }
        }
    }

    /**
     * Отправляет сообщения в Claude API с учётом конфигурации.
     *
     * @param conversationHistory полная история диалога (user/assistant)
     * @param config конфигурация параметров запроса
     * @return Result с ответом Claude или ошибкой
     */
    suspend fun sendMessage(
        conversationHistory: List<ClaudeMessageRequest>,
        config: ChatRequestConfig = ChatRequestConfig()
    ): Result<ClaudeResponse> {
        return try {
            // Формируем параметры из конфигурации
            val effectiveMaxTokens = if (config.useMaxTokensLimit) {
                MAX_TOKENS_LIMIT
            } else {
                config.maxTokens
            }

            val systemPrompt = buildSystemPrompt(config)

            val effectiveStopSequences = if (config.useStopSequences && config.stopSequences.isNotEmpty()) {
                config.stopSequences
            } else {
                null
            }

            // Если пользователь выбрал конкретную температуру — передаём её, иначе null (API использует свой дефолт)
            val effectiveTemperature = if (!config.useDefaultTemperature) {
                config.temperature
            } else {
                null
            }

            val request = ClaudeRequest(
                model = config.selectedModel.id,
                maxTokens = effectiveMaxTokens,
                messages = conversationHistory,
                system = systemPrompt,
                stopSequences = effectiveStopSequences,
                temperature = effectiveTemperature
            )

            val url = "$baseUrl/v1/messages"
            val requestBody = json.encodeToString(ClaudeRequest.serializer(), request)

            // Логируем cURL и параметры для отладки
            val tempInfo = if (effectiveTemperature != null) "temperature=$effectiveTemperature" else "temperature=default"
            println("┌─── Request params: model=${config.selectedModel.id}, max_tokens=$effectiveMaxTokens, $tempInfo ───")
            println(buildCurlCommand(url, requestBody))

            val response = httpClient.post(url) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(TextContent(requestBody, ContentType.Application.Json))
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
