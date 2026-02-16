package com.example.mykmp.config

import platform.Foundation.NSProcessInfo

/**
 * iOS: читает API-ключ из переменных окружения процесса.
 *
 * Задайте переменную окружения в Xcode:
 *   Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables
 *   Добавьте: ANTHROPIC_API_KEY = sk-ant-...
 */
actual fun getApiKey(): String {
    val env = NSProcessInfo.processInfo.environment
    val key = env["ANTHROPIC_API_KEY"] as? String
    if (key.isNullOrBlank()) {
        error(
            "ANTHROPIC_API_KEY environment variable not set.\n" +
                "Set it in Xcode Scheme → Run → Environment Variables."
        )
    }
    return key
}
