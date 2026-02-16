package com.example.mykmp.config

/**
 * Возвращает API-ключ Claude для текущей платформы.
 *
 * Для локальной разработки:
 * - Desktop (JVM): задайте переменную окружения ANTHROPIC_API_KEY
 *     export ANTHROPIC_API_KEY=sk-ant-...
 * - Android: добавьте в local.properties (файл не коммитится в Git):
 *     ANTHROPIC_API_KEY=sk-ant-...
 * - iOS: задайте переменную окружения в схеме Xcode
 * - Web: ключ не хранится на клиенте; используйте backend-прокси
 *
 * ВАЖНО: Никогда не вставляйте ключ прямо в исходный код!
 * Для продакшена рекомендуется использовать backend-прокси,
 * чтобы ключ не попадал в клиентское приложение.
 */
expect fun getApiKey(): String
