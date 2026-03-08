package com.example.mykmp.domain.invariant

/**
 * Тип инварианта — область применения правила.
 */
enum class InvariantType {
    ARCHITECTURE,
    TECH_STACK,
    BUDGET,
    TIMING,
    BUSINESS_RULES,
    QUALITY_GATES,
    DEPLOYMENT;

    fun displayName(): String = when (this) {
        ARCHITECTURE    -> "Архитектура"
        TECH_STACK      -> "Технический стек"
        BUDGET          -> "Бюджет"
        TIMING          -> "Сроки"
        BUSINESS_RULES  -> "Бизнес-правила"
        QUALITY_GATES   -> "Критерии качества"
        DEPLOYMENT      -> "Развёртывание"
    }
}

/**
 * Приоритет инварианта — критичность нарушения.
 */
enum class ConstraintPriority {
    CRITICAL,
    HIGH,
    MEDIUM;

    fun emoji(): String = when (this) {
        CRITICAL -> "🔴"
        HIGH     -> "🟡"
        MEDIUM   -> "🟢"
    }

    fun displayName(): String = when (this) {
        CRITICAL -> "Критический"
        HIGH     -> "Высокий"
        MEDIUM   -> "Средний"
    }
}

/**
 * Инвариант — ненарушимое правило (глобальное или привязанное к задаче).
 *
 * @param taskId null = глобальный, иначе привязан к конкретной задаче FSM.
 */
data class Invariant(
    val id: String,
    val type: InvariantType,
    val constraintText: String,
    val description: String,
    val priority: ConstraintPriority,
    val taskId: String?,
    val createdAt: Long,
    val source: String = "user"
)

/**
 * DTO для создания нового инварианта.
 */
data class NewInvariant(
    val type: InvariantType,
    val constraintText: String,
    val description: String,
    val priority: ConstraintPriority,
    val taskId: String? = null
)

/**
 * Зафиксированное нарушение инварианта в конкретном сообщении.
 */
data class InvariantViolation(
    val invariantId: String,
    val messageId: String,
    val violationReason: String,
    val severity: ConstraintPriority,
    val timestamp: Long
)

/**
 * Результат проверки сообщения на соответствие инвариантам.
 */
sealed class InvariantCheckResult {
    /** Проверка выполняется */
    object Loading : InvariantCheckResult()
    /** Нарушений не обнаружено */
    object NoViolations : InvariantCheckResult()
    /** Обнаружены нарушения */
    data class HasViolations(val items: List<InvariantViolation>) : InvariantCheckResult()
}
