package com.example.mykmp.domain.profile

import kotlinx.serialization.Serializable

/**
 * Профиль пользователя — хранит предпочтения для персонализации system prompt.
 */
@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val preferredLanguages: List<String> = emptyList(),    // ["Kotlin", "Swift"]
    val architecturePrefs: List<String> = emptyList(),     // ["Clean Architecture", "MVVM"]
    val responseStyle: String = ResponseStyle.CONCISE.name,
    val tone: String = Tone.FRIENDLY.name,
    val expertiseLevel: String = ExpertiseLevel.MIDDLE.name,
    val budgetLimits: Map<String, String> = emptyMap(),    // {"AWS": "50$/мес"}
    val timeConstraints: Map<String, String> = emptyMap(), // {"deploy": "2 нед"}
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Предложение обновления профиля (из авто-экстракции диалога).
 */
@Serializable
data class ProfileSuggestion(
    val preferredLanguages: List<String> = emptyList(),
    val architecturePrefs: List<String> = emptyList(),
    val budgetLimits: Map<String, String> = emptyMap(),
    val timeConstraints: Map<String, String> = emptyMap()
) {
    fun isEmpty(): Boolean =
        preferredLanguages.isEmpty() &&
        architecturePrefs.isEmpty() &&
        budgetLimits.isEmpty() &&
        timeConstraints.isEmpty()
}

enum class ResponseStyle {
    VERBOSE, CONCISE, STEP_BY_STEP;

    fun displayName(): String = when (this) {
        VERBOSE -> "Подробно с объяснениями"
        CONCISE -> "Кратко, только суть"
        STEP_BY_STEP -> "Шаг за шагом"
    }
}

enum class Tone {
    FORMAL, FRIENDLY, DIRECT;

    fun displayName(): String = when (this) {
        FORMAL -> "Формальный"
        FRIENDLY -> "Дружелюбный"
        DIRECT -> "По делу, без воды"
    }
}

enum class ExpertiseLevel {
    JUNIOR, MIDDLE, SENIOR;

    fun displayName(): String = when (this) {
        JUNIOR -> "Начинающий"
        MIDDLE -> "Средний"
        SENIOR -> "Опытный"
    }
}
