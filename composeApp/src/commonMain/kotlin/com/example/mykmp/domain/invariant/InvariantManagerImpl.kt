package com.example.mykmp.domain.invariant

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.repository.ChatRepository
import io.ktor.util.date.*
import kotlin.random.Random

class InvariantManagerImpl(private val repository: InvariantRepository) : InvariantManager {

    override var onStateChanged: (() -> Unit)? = null

    override suspend fun addInvariant(new: NewInvariant): Invariant {
        val invariant = Invariant(
            id = generateId(),
            type = new.type,
            constraintText = new.constraintText,
            description = new.description,
            priority = new.priority,
            taskId = new.taskId,
            createdAt = getTimeMillis(),
            source = "user"
        )
        repository.saveInvariant(invariant)
        onStateChanged?.invoke()
        return invariant
    }

    override suspend fun getInvariants(taskId: String?): List<Invariant> {
        return if (taskId != null) {
            repository.getInvariantsForTask(taskId)
        } else {
            repository.getGlobalInvariants()
        }
    }

    override suspend fun getAllInvariants(): List<Invariant> =
        repository.getAllInvariants()

    override suspend fun removeInvariant(id: String) {
        repository.deleteInvariant(id)
        onStateChanged?.invoke()
    }

    /**
     * Строит блок ограничений для system prompt.
     * Инварианты сортируются по убыванию приоритета (CRITICAL → HIGH → MEDIUM).
     */
    override suspend fun buildConstraintPrompt(taskId: String?): String? {
        val invariants = getInvariants(taskId)
        if (invariants.isEmpty()) return null
        return buildString {
            appendLine("🚫 ИНВАРИАНТЫ (НЕНАРУШИМЫЕ ПРАВИЛА):")
            invariants
                .sortedWith(compareByDescending { it.priority.ordinal })
                .forEach { inv ->
                    appendLine("${inv.priority.emoji()} [${inv.priority.name}] ${inv.id} (${inv.type.displayName()}): ${inv.constraintText}")
                    if (inv.description.isNotBlank()) {
                        appendLine("   ${inv.description}")
                    }
                }
            appendLine()
            appendLine("Твои обязанности:")
            appendLine("1. НИКОГДА не предлагай решения, нарушающие инварианты.")
            appendLine("2. Если задача невыполнима без нарушения — скажи: ❌ Нарушение инварианта [id]: [причина].")
            appendLine("3. Обосновывай предложения ссылкой на совместимость с инвариантами.")
        }.trimEnd()
    }

    /**
     * On-demand проверка сообщения ассистента через отдельный вызов Claude.
     * Использует temperature=0 и минимальные токены для детерминированного JSON-ответа.
     */
    override suspend fun checkMessage(
        messageId: String,
        messageText: String,
        taskId: String?,
        chatRepository: ChatRepository,
        config: ChatRequestConfig
    ): InvariantCheckResult {
        val invariants = getInvariants(taskId)
        if (invariants.isEmpty()) return InvariantCheckResult.NoViolations

        val prompt = buildString {
            appendLine("Проверь, нарушает ли следующий ответ какие-либо из инвариантов.")
            appendLine("Ответь СТРОГО в JSON: {\"violations\":[{\"invariantId\":\"…\",\"reason\":\"…\",\"severity\":\"CRITICAL|HIGH|MEDIUM\"}]}")
            appendLine("Если нарушений нет: {\"violations\":[]}")
            appendLine()
            appendLine("ИНВАРИАНТЫ:")
            invariants.forEach { inv ->
                appendLine("- ${inv.id} (${inv.priority.name}): ${inv.constraintText}")
            }
            appendLine()
            appendLine("ОТВЕТ ДЛЯ ПРОВЕРКИ:")
            append(messageText)
        }

        val checkConfig = config.copy(
            useDefaultTemperature = false,
            temperature = 0.0,
            maxTokens = 512,
            useMaxTokensLimit = false,
            responseFormatMode = ResponseFormatMode.FREE_TEXT,
            useStopSequences = false,
            stopSequences = emptyList()
        )

        val result = chatRepository.sendMessage(
            conversationHistory = listOf(ClaudeMessageRequest(role = "user", content = prompt)),
            config = checkConfig,
            systemPromptAddition = null
        )

        return result.fold(
            onSuccess = { response ->
                val text = response.content
                    .filter { it.type == "text" }
                    .joinToString("") { it.text }
                parseCheckResult(text, messageId, invariants)
            },
            onFailure = {
                InvariantCheckResult.NoViolations
            }
        )
    }

    // === Private ===

    /**
     * Парсит JSON-ответ Claude и возвращает результат проверки.
     * Поиск вручную, без kotlinx.serialization (нет зависимости).
     *
     * Ожидаемый формат: {"violations":[{"invariantId":"…","reason":"…","severity":"…"}]}
     */
    private suspend fun parseCheckResult(
        text: String,
        messageId: String,
        invariants: List<Invariant>
    ): InvariantCheckResult {
        return try {
            // Находим JSON-объект в ответе (Claude может добавить текст вокруг)
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                return InvariantCheckResult.NoViolations
            }
            val json = text.substring(jsonStart, jsonEnd + 1)

            // Ищем массив violations
            val violationsStart = json.indexOf("\"violations\"")
            if (violationsStart == -1) return InvariantCheckResult.NoViolations

            val arrayStart = json.indexOf('[', violationsStart)
            val arrayEnd = json.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1) return InvariantCheckResult.NoViolations

            val arrayContent = json.substring(arrayStart + 1, arrayEnd).trim()
            if (arrayContent.isBlank()) return InvariantCheckResult.NoViolations

            // Разбиваем на отдельные объекты-нарушения
            val violations = parseViolationObjects(arrayContent, messageId, invariants)

            if (violations.isEmpty()) {
                InvariantCheckResult.NoViolations
            } else {
                // Сохраняем нарушения в БД
                violations.forEach { violation ->
                    repository.saveViolation(violation)
                }
                InvariantCheckResult.HasViolations(violations)
            }
        } catch (_: Exception) {
            InvariantCheckResult.NoViolations
        }
    }

    /**
     * Парсит список объектов нарушений из строки вида:
     * {"invariantId":"id1","reason":"причина","severity":"CRITICAL"}, {...}
     */
    private fun parseViolationObjects(
        arrayContent: String,
        messageId: String,
        invariants: List<Invariant>
    ): List<InvariantViolation> {
        val violations = mutableListOf<InvariantViolation>()
        var depth = 0
        var objectStart = -1

        for (i in arrayContent.indices) {
            when (arrayContent[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart != -1) {
                        val objStr = arrayContent.substring(objectStart, i + 1)
                        parseViolationObject(objStr, messageId, invariants)?.let {
                            violations.add(it)
                        }
                        objectStart = -1
                    }
                }
            }
        }
        return violations
    }

    /**
     * Парсит один объект нарушения: {"invariantId":"…","reason":"…","severity":"…"}
     */
    private fun parseViolationObject(
        obj: String,
        messageId: String,
        invariants: List<Invariant>
    ): InvariantViolation? {
        val invariantId = extractJsonString(obj, "invariantId") ?: return null
        val reason = extractJsonString(obj, "reason") ?: return null
        val severityStr = extractJsonString(obj, "severity") ?: "MEDIUM"

        val severity = try {
            ConstraintPriority.valueOf(severityStr)
        } catch (_: Exception) {
            // Пробуем найти ближайший инвариант по id для определения severity
            invariants.find { it.id == invariantId }?.priority ?: ConstraintPriority.MEDIUM
        }

        return InvariantViolation(
            invariantId = invariantId,
            messageId = messageId,
            violationReason = reason,
            severity = severity,
            timestamp = getTimeMillis()
        )
    }

    /**
     * Извлекает строковое значение по ключу из JSON-объекта.
     * Поддерживает как {"key":"value"} так и {"key": "value"}.
     */
    private fun extractJsonString(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex == -1) return null

        val colonIndex = json.indexOf(':', keyIndex + keyPattern.length)
        if (colonIndex == -1) return null

        val afterColon = json.substring(colonIndex + 1).trimStart()
        if (!afterColon.startsWith('"')) return null

        val valueStart = json.indexOf('"', colonIndex + 1) + 1
        val valueEnd = findStringEnd(json, valueStart)
        if (valueEnd == -1) return null

        return json.substring(valueStart, valueEnd)
    }

    /**
     * Находит конец JSON-строки, учитывая экранирование.
     */
    private fun findStringEnd(json: String, start: Int): Int {
        var i = start
        while (i < json.length) {
            when {
                json[i] == '\\' -> i += 2  // пропускаем экранированный символ
                json[i] == '"' -> return i
                else -> i++
            }
        }
        return -1
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "inv-" + (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
