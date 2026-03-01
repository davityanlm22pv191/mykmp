package com.example.mykmp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * Проверяет доступность api.anthropic.com с текущей сети.
 * GET /v1/messages без ключа:
 *   - 403 "Request not allowed" → гео-блокировка (нужен VPN)
 *   - 401 / другой код → API доступен
 *   - Исключение → нет сети
 */
class ApiAccessChecker {

    private val httpClient = HttpClient(createPlatformEngine()) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun isGeoBlocked(baseUrl: String = "https://api.anthropic.com"): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/v1/messages") {
                header("anthropic-version", "2023-06-01")
            }
            if (response.status == HttpStatusCode.Forbidden) {
                val body = try { response.bodyAsText() } catch (_: Exception) { "" }
                body.contains("Request not allowed") || body.contains("forbidden")
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
