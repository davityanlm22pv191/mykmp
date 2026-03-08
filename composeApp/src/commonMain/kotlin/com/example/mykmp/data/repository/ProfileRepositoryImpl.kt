package com.example.mykmp.data.repository

import com.example.mykmp.data.storage.loadFromStorage
import com.example.mykmp.data.storage.saveToStorage
import com.example.mykmp.domain.profile.ProfileRepository
import com.example.mykmp.domain.profile.UserProfile
import kotlinx.serialization.json.Json

private const val KEY_PROFILE_LIST = "profile_list"
private const val KEY_ACTIVE_PROFILE_ID = "profile_active_id"
private const val KEY_LAST_PROCESSED = "profile_last_processed"

/**
 * Реализация ProfileRepository через saveToStorage/loadFromStorage.
 */
class ProfileRepositoryImpl : ProfileRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun saveProfiles(profiles: List<UserProfile>) {
        try {
            saveToStorage(KEY_PROFILE_LIST, json.encodeToString(profiles))
        } catch (e: Exception) {
            println("❌ Ошибка сохранения профилей: ${e.message}")
        }
    }

    override fun loadProfiles(): List<UserProfile> {
        return try {
            val jsonStr = loadFromStorage(KEY_PROFILE_LIST) ?: return emptyList()
            if (jsonStr.isBlank()) return emptyList()
            json.decodeFromString<List<UserProfile>>(jsonStr)
        } catch (e: Exception) {
            println("❌ Ошибка загрузки профилей: ${e.message}")
            emptyList()
        }
    }

    override fun saveActiveProfileId(id: String?) {
        saveToStorage(KEY_ACTIVE_PROFILE_ID, id ?: "")
    }

    override fun loadActiveProfileId(): String? {
        val stored = loadFromStorage(KEY_ACTIVE_PROFILE_ID)
        return if (stored.isNullOrBlank()) null else stored
    }

    override fun saveLastProcessedCount(count: Int) {
        saveToStorage(KEY_LAST_PROCESSED, count.toString())
    }

    override fun loadLastProcessedCount(): Int {
        return loadFromStorage(KEY_LAST_PROCESSED)?.toIntOrNull() ?: 0
    }
}
