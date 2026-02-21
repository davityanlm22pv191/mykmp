package com.example.mykmp.domain.model

/**
 * Уровень модели: слабая (быстрая/дешёвая), средняя (баланс), сильная (качество).
 */
enum class ModelTier {
    /** Быстрая, дешёвая, менее мощная */
    WEAK,
    /** Компромисс между скоростью, ценой и качеством */
    MEDIUM,
    /** Дорогая, медленнее, но наилучшее качество */
    STRONG
}

/**
 * Информация о модели Claude.
 *
 * @param id точный идентификатор для API
 * @param displayName человеко-читаемое имя
 * @param tier уровень модели (WEAK / MEDIUM / STRONG)
 * @param inputPricePer1M цена за 1M входных токенов в USD
 * @param outputPricePer1M цена за 1M выходных токенов в USD
 * @param description краткое описание для UI
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val tier: ModelTier,
    val inputPricePer1M: Double,
    val outputPricePer1M: Double,
    val description: String
) {
    /**
     * Рассчитывает стоимость запроса в USD.
     */
    fun calculateCost(inputTokens: Int, outputTokens: Int): Double {
        return (inputTokens / 1_000_000.0) * inputPricePer1M +
            (outputTokens / 1_000_000.0) * outputPricePer1M
    }
}

/**
 * Список доступных моделей Claude.
 *
 * Цены актуальны на 2025 год (https://docs.anthropic.com/en/docs/about-claude/models).
 * При необходимости обновите id и цены.
 */
val AVAILABLE_MODELS = listOf(
    ModelInfo(
        id = "claude-3-haiku-20240307",
        displayName = "Claude 3 Haiku",
        tier = ModelTier.WEAK,
        inputPricePer1M = 0.25,
        outputPricePer1M = 1.25,
        description = "Быстрая, дешёвая. Подходит для простых задач."
    ),
    ModelInfo(
        id = "claude-sonnet-4-20250514",
        displayName = "Claude Sonnet 4",
        tier = ModelTier.MEDIUM,
        inputPricePer1M = 3.0,
        outputPricePer1M = 15.0,
        description = "Баланс скорости, цены и качества."
    ),
    ModelInfo(
        id = "claude-opus-4-20250514",
        displayName = "Claude Opus 4",
        tier = ModelTier.STRONG,
        inputPricePer1M = 15.0,
        outputPricePer1M = 75.0,
        description = "Максимальное качество. Для сложных задач."
    )
)

/** Модель по умолчанию — средняя (Sonnet) */
val DEFAULT_MODEL = AVAILABLE_MODELS.first { it.tier == ModelTier.MEDIUM }
