package com.example.mykmp.domain.profile

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.repository.ChatRepository
import io.ktor.util.date.*
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Реализация ProfileManager.
 * In-memory кэш + персистентность через ProfileRepository.
 */
class ProfileManagerImpl(
    private val repository: ProfileRepository
) : ProfileManager {

    private val profiles = mutableListOf<UserProfile>()
    private var activeProfileId: String? = null
    private var lastProcessedCount: Int = 0

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override var onSuggestionReady: ((ProfileSuggestion) -> Unit)? = null
    override var onStateChanged: (() -> Unit)? = null

    init {
        profiles.addAll(repository.loadProfiles())
        activeProfileId = repository.loadActiveProfileId()
        lastProcessedCount = repository.loadLastProcessedCount()

        // Если сохранённый активный профиль не существует — сбросить
        if (activeProfileId != null && profiles.none { it.id == activeProfileId }) {
            activeProfileId = profiles.firstOrNull()?.id
        }
    }

    // === CRUD профилей ===

    override fun getProfiles(): List<UserProfile> = profiles.toList()

    override fun getActiveProfile(): UserProfile? =
        profiles.find { it.id == activeProfileId }

    override fun createProfile(displayName: String): UserProfile {
        val now = getTimeMillis()
        val profile = UserProfile(
            id = generateId(),
            displayName = displayName,
            createdAt = now,
            updatedAt = now
        )
        profiles.add(profile)
        // Первый созданный профиль становится активным
        if (profiles.size == 1) {
            activeProfileId = profile.id
            repository.saveActiveProfileId(activeProfileId)
        }
        saveProfiles()
        onStateChanged?.invoke()
        return profile
    }

    override fun updateProfile(id: String, update: ProfileSuggestion) {
        val idx = profiles.indexOfFirst { it.id == id }
        if (idx == -1) return

        val existing = profiles[idx]
        val merged = existing.copy(
            preferredLanguages = (existing.preferredLanguages + update.preferredLanguages).distinct(),
            architecturePrefs = (existing.architecturePrefs + update.architecturePrefs).distinct(),
            budgetLimits = existing.budgetLimits + update.budgetLimits,
            timeConstraints = existing.timeConstraints + update.timeConstraints,
            updatedAt = getTimeMillis()
        )
        profiles[idx] = merged
        saveProfiles()
        onStateChanged?.invoke()
    }

    override fun replaceProfile(profile: UserProfile) {
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx == -1) return
        profiles[idx] = profile
        saveProfiles()
        onStateChanged?.invoke()
    }

    override fun deleteProfile(id: String) {
        profiles.removeAll { it.id == id }
        if (activeProfileId == id) {
            activeProfileId = profiles.firstOrNull()?.id
            repository.saveActiveProfileId(activeProfileId)
        }
        saveProfiles()
        onStateChanged?.invoke()
    }

    override fun switchProfile(id: String) {
        if (profiles.none { it.id == id }) return
        activeProfileId = id
        repository.saveActiveProfileId(id)
        onStateChanged?.invoke()
    }

    // === Контекст для Claude ===

    override fun buildPersonalizationPrompt(): String? {
        val profile = getActiveProfile() ?: return null
        profile.preferredLanguages.isNotEmpty() ||
            profile.architecturePrefs.isNotEmpty() ||
            profile.budgetLimits.isNotEmpty() ||
            profile.timeConstraints.isNotEmpty()

        return buildString {
            appendLine("Ты — экспертный ассистент для ${profile.displayName}.")
            appendLine()
            appendLine("ПРЕДПОЧТЕНИЯ:")
            if (profile.preferredLanguages.isNotEmpty()) {
                appendLine("- Языки: ${profile.preferredLanguages.joinToString(", ")}")
            }
            if (profile.architecturePrefs.isNotEmpty()) {
                appendLine("- Архитектура: ${profile.architecturePrefs.joinToString(", ")}")
            }
            appendLine("- Стиль ответов: ${safeResponseStyle(profile.responseStyle).displayName()}")
            appendLine("- Тон: ${safeTone(profile.tone).displayName()}")
            appendLine("- Уровень: ${safeExpertiseLevel(profile.expertiseLevel).displayName()}")

            if (profile.budgetLimits.isNotEmpty() || profile.timeConstraints.isNotEmpty()) {
                appendLine()
                appendLine("ОГРАНИЧЕНИЯ:")
                for ((key, value) in profile.budgetLimits) {
                    appendLine("- $key: $value")
                }
                for ((key, value) in profile.timeConstraints) {
                    appendLine("- $key: $value")
                }
            }

            appendLine()
            append("Отвечай строго согласно этим предпочтениям.")
        }
    }

    // === Авто-экстракция предпочтений ===

    override suspend fun analyzeAndSuggest(
        messages: List<ChatMessage>,
        config: ChatRequestConfig,
        chatRepository: ChatRepository
    ) {
        val validMessages = messages.filter { !it.isError }
        val totalValid = validMessages.size

        // Анализируем каждые 5 новых сообщений
        if (totalValid - lastProcessedCount < 5) return

        val newMessages = validMessages.drop(lastProcessedCount)
        if (newMessages.isEmpty()) return

        val prompt = buildExtractionPrompt(newMessages)
        val extractionConfig = config.copy(
            useDefaultTemperature = false,
            temperature = 0.0,
            maxTokens = 512,
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

                val suggestion = parseSuggestion(responseText)
                lastProcessedCount = totalValid
                repository.saveLastProcessedCount(lastProcessedCount)

                if (!suggestion.isEmpty()) {
                    println("✅ Предложение профиля: $suggestion")
                    onSuggestionReady?.invoke(suggestion)
                }
            },
            onFailure = { error ->
                println("❌ Ошибка авто-экстракции профиля: ${error.message}")
            }
        )
    }

    // === Private ===

    private fun buildExtractionPrompt(messages: List<ChatMessage>): String {
        return buildString {
            appendLine("Проанализируй диалог. Выяви предпочтения пользователя.")
            appendLine("Верни ТОЛЬКО JSON (без markdown, без ```):")
            appendLine("""{"preferredLanguages":["..."],"architecturePrefs":["..."],"budgetLimits":{"key":"value"},"timeConstraints":{"key":"value"}}""")
            appendLine()
            appendLine("Поля:")
            appendLine("- preferredLanguages: языки программирования")
            appendLine("- architecturePrefs: архитектурные паттерны")
            appendLine("- budgetLimits: бюджетные ограничения (ключ → значение)")
            appendLine("- timeConstraints: временные ограничения (ключ → значение)")
            appendLine()
            appendLine("Если ничего не выявлено — пустые массивы/объекты.")
            appendLine()
            appendLine("=== Диалог ===")
            for (msg in messages) {
                val role = when (msg.role) {
                    ChatMessage.Role.USER -> "Пользователь"
                    ChatMessage.Role.ASSISTANT -> "Ассистент"
                }
                appendLine("$role: ${msg.text}")
                appendLine()
            }
            append("Верни ТОЛЬКО JSON. Без пояснений.")
        }
    }

    private fun parseSuggestion(response: String): ProfileSuggestion {
        return try {
            val jsonStr = extractJsonFromResponse(response) ?: return ProfileSuggestion()
            json.decodeFromString<ProfileSuggestion>(jsonStr)
        } catch (e: Exception) {
            println("⚠️ Ошибка парсинга предложения профиля: ${e.message}")
            ProfileSuggestion()
        }
    }

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

    private fun saveProfiles() {
        repository.saveProfiles(profiles.toList())
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun safeResponseStyle(name: String): ResponseStyle =
        try { ResponseStyle.valueOf(name) } catch (_: Exception) { ResponseStyle.CONCISE }

    private fun safeTone(name: String): Tone =
        try { Tone.valueOf(name) } catch (_: Exception) { Tone.FRIENDLY }

    private fun safeExpertiseLevel(name: String): ExpertiseLevel =
        try { ExpertiseLevel.valueOf(name) } catch (_: Exception) { ExpertiseLevel.MIDDLE }
}
