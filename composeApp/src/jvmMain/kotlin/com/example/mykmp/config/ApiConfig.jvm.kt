package com.example.mykmp.config

/**
 * Desktop (JVM): читает API-ключ из переменной окружения.
 *
 * Перед запуском выполните в терминале:
 *   export ANTHROPIC_API_KEY=sk-ant-...
 *
 * Или задайте переменную в Run Configuration IntelliJ IDEA:
 *   Run → Edit Configurations → Environment Variables → ANTHROPIC_API_KEY=sk-ant-...
 */
actual fun getApiKey(): String {
    return System.getenv("ANTHROPIC_API_KEY")
        ?: error(
            "ANTHROPIC_API_KEY environment variable is not set.\n" +
                "Set it before running the app:\n" +
                "  export ANTHROPIC_API_KEY=sk-ant-..."
        )
}
