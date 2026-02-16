package com.example.mykmp.config

/**
 * Web (Kotlin/WasmJS): API-ключ НЕ ДОЛЖЕН храниться в клиентском коде.
 *
 * Аналогично Kotlin/JS — ключ в клиентском бандле легко извлекается.
 * Для веб-версии необходим backend-прокси.
 *
 * TODO: Реализовать backend-прокси для безопасной работы с Claude API.
 */
actual fun getApiKey(): String {
    println("WARNING: WasmJs target — API key is not configured. Use a backend proxy for production.")
    return ""
}
