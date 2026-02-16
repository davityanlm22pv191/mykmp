package com.example.mykmp.data.api

import io.ktor.client.engine.HttpClientEngine

/**
 * Возвращает платформо-специфичный HTTP-движок для Ktor Client.
 *
 * Реализации:
 * - JVM (Desktop): CIO
 * - Android: OkHttp
 * - iOS: Darwin (NSURLSession)
 * - JS / WasmJS: Js (Fetch API)
 */
expect fun createPlatformEngine(): HttpClientEngine
