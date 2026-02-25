package com.example.mykmp.config

import com.example.mykmp.BuildConfig

/**
 * Android: читает API-ключ из BuildConfig.
 * Ключ берётся из local.properties или системного окружения (при сборке).
 *
 * Добавьте в local.properties:
 *   ANTHROPIC_API_KEY=sk-ant-...
 * Или установите переменную окружения ANTHROPIC_API_KEY.
 */
actual fun getApiKey(): String {
    val key = BuildConfig.ANTHROPIC_API_KEY
    if (key.isBlank()) {
        println("WARNING: ANTHROPIC_API_KEY not set. Add to local.properties or set env variable.")
    }
    return key
}
