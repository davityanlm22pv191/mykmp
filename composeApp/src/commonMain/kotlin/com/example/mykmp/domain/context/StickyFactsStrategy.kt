package com.example.mykmp.domain.context

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Хранимые ключевые факты разговора.
 */
@Serializable
data class StickyFacts(
    val facts: Map<String, String> = emptyMap(),
    val lastProcessedMessageIndex: Int = 0
)

/**
 * Стратегия «Ключевые факты»: извлекает факты из разговора через Claude API,
 * хранит как Map<String,String>, отправляет facts + последние N сообщений.
 *
 * Категории фактов: userGoal, constraints, preferences, decisions, techStack, openQuestions
 */
class StickyFactsStrategy(
    private val chatRepository: ChatRepository,
    private val historyRepository: ChatHistoryRepository
) : ContextStrategy {

    override val type = ContextStrategyType.STICKY_FACTS
    override val displayName = type.displayName()
    override val description = type.description()

    private var stickyFacts = StickyFacts()
    private var _isExtracting = false

    val isExtracting: Boolean get() = _isExtracting
    val currentFacts: Map<String, String> get() = stickyFacts.facts

    /** Callback для уведомления UI об изменении состояния. */
    var onStateChanged: (() -> Unit)? = null

    /** Callback: предложить извлечённые факты для сохранения в долговременную память. */
    var onFactsExtracted: ((Map<String, String>) -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult {
        val validMessages = messages.filter { !it.isError }
        val n = config.contextWindowSize
        val window = validMessages.takeLast(n)

        val apiMessages = window.map { msg ->
            ClaudeMessageRequest(
                role = when (msg.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                },
                content = msg.text
            )
        }

        // Формируем system prompt с фактами
        val systemAddition = if (stickyFacts.facts.isNotEmpty()) {
            buildFactsSystemPrompt()
        } else {
            null
        }

        return ContextResult(
            messagesToSend = apiMessages,
            systemPromptAddition = systemAddition
        )
    }

    override suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        val validMessages = allMessages.filter { !it.isError }
        val totalValid = validMessages.size
        val lastProcessed = stickyFacts.lastProcessedMessageIndex

        // Извлекаем факты каждые 4 новых сообщения (2 пары user+assistant)
        if (totalValid - lastProcessed >= 4) {
            extractFacts(allMessages, config)
        }
    }

    override fun loadState() {
        stickyFacts = historyRepository.loadStickyFacts()?.let { jsonStr ->
            try {
                json.decodeFromString<StickyFacts>(jsonStr)
            } catch (e: Exception) {
                println("❌ Ошибка загрузки sticky facts: ${e.message}")
                StickyFacts()
            }
        } ?: StickyFacts()
    }

    override fun clearState() {
        stickyFacts = StickyFacts()
        _isExtracting = false
        historyRepository.clearStickyFacts()
    }

    private fun saveState() {
        try {
            val jsonStr = json.encodeToString(StickyFacts.serializer(), stickyFacts)
            historyRepository.saveStickyFacts(jsonStr)
        } catch (e: Exception) {
            println("❌ Ошибка сохранения sticky facts: ${e.message}")
        }
    }

    /**
     * Формирует system prompt с извлечёнными фактами.
     */
    private fun buildFactsSystemPrompt(): String {
        return buildString {
            appendLine("Ключевые факты из разговора (используй их для контекста):")
            appendLine()
            for ((category, value) in stickyFacts.facts) {
                val label = categoryLabel(category)
                appendLine("[$label]: $value")
            }
        }
    }

    /**
     * Русские метки для категорий фактов.
     */
    private fun categoryLabel(category: String): String = when (category) {
        "userGoal" -> "Цель пользователя"
        "constraints" -> "Ограничения"
        "preferences" -> "Предпочтения"
        "decisions" -> "Принятые решения"
        "techStack" -> "Технологии"
        "openQuestions" -> "Открытые вопросы"
        else -> category
    }

    /**
     * Фоновая экстракция фактов из новых сообщений через Claude API.
     */
    private suspend fun extractFacts(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        if (_isExtracting) return
        _isExtracting = true
        onStateChanged?.invoke()

        try {
            val validMessages = allMessages.filter { !it.isError }
            val lastProcessed = stickyFacts.lastProcessedMessageIndex
            val newMessages = validMessages.drop(lastProcessed)

            if (newMessages.isEmpty()) {
                _isExtracting = false
                onStateChanged?.invoke()
                return
            }

            val prompt = buildExtractionPrompt(newMessages)

            val extractionConfig = config.copy(
                useDefaultTemperature = false,
                temperature = 0.0,
                maxTokens = 1024,
                useMaxTokensLimit = false,
                responseFormatMode = ResponseFormatMode.FREE_TEXT,
                formatHint = "",
                useStopSequences = false,
                stopSequences = emptyList()
            )

            val result = chatRepository.sendMessage(
                listOf(ClaudeMessageRequest(role = "user", content = prompt)),
                extractionConfig,
                null
            )

            result.fold(
                onSuccess = { response ->
                    val responseText = response.content
                        .filter { it.type == "text" }
                        .joinToString("\n") { it.text }

                    val extractedFacts = parseFactsResponse(responseText)
                    if (extractedFacts.isNotEmpty()) {
                        // Мержим новые факты с существующими
                        val merged = stickyFacts.facts.toMutableMap()
                        merged.putAll(extractedFacts)
                        // Удаляем пустые значения
                        merged.entries.removeAll { it.value.isBlank() }

                        stickyFacts = StickyFacts(
                            facts = merged,
                            lastProcessedMessageIndex = validMessages.size
                        )
                        saveState()
                        println("✅ Факты извлечены: ${merged.size} категорий")

                        // Предлагаем новые факты для сохранения в долговременную память
                        if (extractedFacts.isNotEmpty()) {
                            onFactsExtracted?.invoke(extractedFacts)
                        }
                    } else {
                        // Обновляем lastProcessedMessageIndex даже если фактов нет
                        stickyFacts = stickyFacts.copy(
                            lastProcessedMessageIndex = validMessages.size
                        )
                        saveState()
                    }

                    _isExtracting = false
                    onStateChanged?.invoke()
                },
                onFailure = { error ->
                    println("❌ Ошибка экстракции фактов: ${error.message}")
                    _isExtracting = false
                    onStateChanged?.invoke()
                }
            )
        } catch (e: Exception) {
            println("❌ Ошибка экстракции фактов: ${e.message}")
            _isExtracting = false
            onStateChanged?.invoke()
        }
    }

    /**
     * Формирует промпт для экстракции фактов.
     */
    private fun buildExtractionPrompt(newMessages: List<ChatMessage>): String {
        return buildString {
            appendLine("Проанализируй диалог и извлеки ключевые факты по категориям.")
            appendLine("Верни ТОЛЬКО JSON объект (без markdown, без ```) в формате:")
            appendLine("""{"userGoal":"...","constraints":"...","preferences":"...","decisions":"...","techStack":"...","openQuestions":"..."}""")
            appendLine()
            appendLine("Категории:")
            appendLine("- userGoal: основная цель/задача пользователя")
            appendLine("- constraints: ограничения и требования")
            appendLine("- preferences: предпочтения пользователя (язык, стиль, подход)")
            appendLine("- decisions: принятые решения и выводы")
            appendLine("- techStack: упомянутые технологии, библиотеки, инструменты")
            appendLine("- openQuestions: нерешённые вопросы")
            appendLine()
            appendLine("Если категория не применима, оставь пустую строку.")

            if (stickyFacts.facts.isNotEmpty()) {
                appendLine()
                appendLine("=== Предыдущие факты (обнови/дополни) ===")
                for ((cat, value) in stickyFacts.facts) {
                    appendLine("$cat: $value")
                }
            }

            appendLine()
            appendLine("=== Новые сообщения ===")
            for (msg in newMessages) {
                val role = when (msg.role) {
                    ChatMessage.Role.USER -> "Пользователь"
                    ChatMessage.Role.ASSISTANT -> "Ассистент"
                }
                appendLine("$role: ${msg.text}")
                appendLine()
            }

            appendLine("Верни ТОЛЬКО JSON. Без пояснений.")
        }
    }

    /**
     * Парсит JSON-ответ с фактами. Устойчив к лишним символам вокруг JSON.
     */
    private fun parseFactsResponse(response: String): Map<String, String> {
        return try {
            // Ищем JSON в ответе (может быть обёрнут в ``` или иметь текст вокруг)
            val jsonStr = extractJsonFromResponse(response)
            if (jsonStr != null) {
                val map = json.decodeFromString<Map<String, String>>(jsonStr)
                map.filterValues { it.isNotBlank() }
            } else {
                println("⚠️ Не удалось найти JSON в ответе: ${response.take(200)}")
                emptyMap()
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка парсинга фактов: ${e.message}, ответ: ${response.take(200)}")
            emptyMap()
        }
    }

    /**
     * Извлекает JSON из текстового ответа (ищет первую пару { }).
     */
    private fun extractJsonFromResponse(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
