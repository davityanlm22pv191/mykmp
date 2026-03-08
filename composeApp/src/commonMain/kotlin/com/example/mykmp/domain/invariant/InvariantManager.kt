package com.example.mykmp.domain.invariant

import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.repository.ChatRepository

/**
 * Менеджер инвариантов.
 * Управляет ненарушимыми правилами проекта и проверяет ответы ассистента на их соответствие.
 */
interface InvariantManager {

    /**
     * Добавляет новый инвариант и возвращает созданный объект.
     */
    suspend fun addInvariant(new: NewInvariant): Invariant

    /**
     * Возвращает инварианты для заданного скоупа.
     * Если taskId = null — только глобальные.
     * Если taskId != null — глобальные + инварианты задачи.
     */
    suspend fun getInvariants(taskId: String? = null): List<Invariant>

    /**
     * Возвращает все инварианты (глобальные + все per-task).
     */
    suspend fun getAllInvariants(): List<Invariant>

    suspend fun removeInvariant(id: String)

    /**
     * Строит блок ограничений для system prompt.
     * Возвращает null, если инвариантов нет.
     */
    suspend fun buildConstraintPrompt(taskId: String?): String?

    /**
     * On-demand проверка одного сообщения ассистента.
     * Выполняет отдельный вызов Claude с temperature=0.
     */
    suspend fun checkMessage(
        messageId: String,
        messageText: String,
        taskId: String?,
        chatRepository: ChatRepository,
        config: ChatRequestConfig
    ): InvariantCheckResult

    /**
     * Callback: вызывается при изменении набора инвариантов.
     */
    var onStateChanged: (() -> Unit)?
}
