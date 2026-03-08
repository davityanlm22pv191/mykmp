package com.example.mykmp.domain.profile

/**
 * Персистентное хранение профилей пользователя.
 */
interface ProfileRepository {
    fun saveProfiles(profiles: List<UserProfile>)
    fun loadProfiles(): List<UserProfile>
    fun saveActiveProfileId(id: String?)
    fun loadActiveProfileId(): String?
    fun saveLastProcessedCount(count: Int)
    fun loadLastProcessedCount(): Int
}
