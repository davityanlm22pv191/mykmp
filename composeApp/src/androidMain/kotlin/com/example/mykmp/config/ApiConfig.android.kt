package com.example.mykmp.config

import com.example.mykmp.BuildConfig

/**
 * Android: читает API-ключ из BuildConfig.
 * Ключ берётся из local.properties (файл уже в .gitignore).
 *
 * Добавьте в local.properties:
 *   ANTHROPIC_API_KEY=sk-ant-...
 */
actual fun getApiKey(): String {
    val key = BuildConfig.ANTHROPIC_API_KEY
    if (key.isBlank()) {
        error(
            "ANTHROPIC_API_KEY not set in local.properties.\n" +
                "Add this line to local.properties:\n" +
                "  ANTHROPIC_API_KEY=sk-ant-..."
        )
    }
    return key
}
