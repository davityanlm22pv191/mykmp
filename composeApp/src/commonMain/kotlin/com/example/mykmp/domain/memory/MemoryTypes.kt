package com.example.mykmp.domain.memory

import kotlinx.serialization.Serializable

// === Рабочая память (Working Memory) ===

/**
 * Запись рабочей памяти: ключ-значение в рамках задачи.
 */
@Serializable
data class WorkingMemoryEntry(
    val key: String,
    val value: String,
    val updatedAt: Long
)

/**
 * Задача с набором записей рабочей памяти.
 */
@Serializable
data class TaskMemory(
    val taskId: String,
    val name: String,
    val entries: List<WorkingMemoryEntry> = emptyList(),
    val createdAt: Long,
    val isActive: Boolean = false
)

// === Долговременная память (Long-term Memory) ===

/**
 * Категории долговременной памяти.
 */
enum class MemoryCategory {
    PREFERENCES,
    DECISIONS,
    KNOWLEDGE,
    SKILLS;

    /** Русское название для UI. */
    fun displayName(): String = when (this) {
        PREFERENCES -> "Предпочтения"
        DECISIONS -> "Решения"
        KNOWLEDGE -> "Знания"
        SKILLS -> "Навыки"
    }
}

/**
 * Факт долговременной памяти: категоризированное знание с уровнем уверенности.
 *
 * @param source "manual" — добавлен вручную, "extracted" — извлечён автоматически,
 *               "confirmed" — извлечён и подтверждён пользователем
 */
@Serializable
data class LongTermFact(
    val id: String,
    val category: String,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val source: String = "manual",
    val createdAt: Long
)
