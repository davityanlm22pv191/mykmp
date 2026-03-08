package com.example.mykmp.domain.profile

import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.repository.ChatRepository

/**
 * Менеджер профилей пользователя.
 * Управляет набором профилей и строит персонализированный system prompt.
 */
interface ProfileManager {

    // === CRUD профилей ===

    fun getProfiles(): List<UserProfile>
    fun getActiveProfile(): UserProfile?
    fun createProfile(displayName: String): UserProfile
    fun updateProfile(id: String, update: ProfileSuggestion)
    fun replaceProfile(profile: UserProfile)
    fun deleteProfile(id: String)
    fun switchProfile(id: String)

    // === Контекст для Claude ===

    /**
     * Формирует персонализированный фрагмент system prompt на основе активного профиля.
     * Возвращает null, если профиль не задан или он пустой.
     */
    fun buildPersonalizationPrompt(): String?

    // === Авто-экстракция предпочтений ===

    /**
     * Анализирует диалог и предлагает обновление профиля через Claude API.
     * Вызывается после каждых 5 новых сообщений.
     * При обнаружении новых данных вызывает [onSuggestionReady].
     */
    suspend fun analyzeAndSuggest(
        messages: List<ChatMessage>,
        config: ChatRequestConfig,
        chatRepository: ChatRepository
    )

    // === Callbacks для UI ===

    var onSuggestionReady: ((ProfileSuggestion) -> Unit)?
    var onStateChanged: (() -> Unit)?
}
