package com.example.mykmp.config

/**
 * Web (Kotlin/JS): API-ключ НЕ ДОЛЖЕН храниться в клиентском коде.
 *
 * Любой ключ, встроенный в JS-бандл, может быть извлечён пользователем
 * через DevTools браузера. Для веб-версии необходимо использовать
 * backend-прокси, который хранит ключ на сервере.
 *
 * TODO: Реализовать backend-прокси (например, Ktor Server) и
 *       направлять запросы через него вместо прямого вызова Claude API.
 */
actual fun getApiKey(): String {
    println("WARNING: Web target — API key is not configured. Use a backend proxy for production.")
    return ""
}
