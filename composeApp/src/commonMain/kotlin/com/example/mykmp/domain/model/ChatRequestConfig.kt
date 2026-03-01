package com.example.mykmp.domain.model

import com.example.mykmp.domain.context.ContextStrategyType

/**
 * Конфигурация параметров запроса к Claude API.
 * Хранится в ChatUiState, передаётся через всю цепочку до ClaudeApiClient.
 */
data class ChatRequestConfig(
    /** Режим формата ответа */
    val responseFormatMode: ResponseFormatMode = ResponseFormatMode.FREE_TEXT,
    /** Текстовая инструкция по формату (для STRUCTURED_HINT) */
    val formatHint: String = "",
    /** Максимальное количество токенов в ответе */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** Использовать максимально допустимое значение max_tokens */
    val useMaxTokensLimit: Boolean = false,
    /** Включить stop_sequences */
    val useStopSequences: Boolean = false,
    /** Список стоп-последовательностей */
    val stopSequences: List<String> = emptyList(),
    /** Значение температуры (0.0–1.5). Используется, если useDefaultTemperature = false */
    val temperature: Double = DEFAULT_TEMPERATURE,
    /** Если true — temperature не передаётся в API, используется дефолт модели */
    val useDefaultTemperature: Boolean = true,
    /** Выбранная модель для запроса */
    val selectedModel: ModelInfo = DEFAULT_MODEL,
    /** Размер контекстного окна — количество последних сообщений, отправляемых сырыми */
    val contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE,
    /** Активная стратегия управления контекстом */
    val contextStrategyType: ContextStrategyType = ContextStrategyType.ROLLING_SUMMARY
)

/**
 * Режим формата ответа.
 */
enum class ResponseFormatMode {
    /** Обычный свободный текст */
    FREE_TEXT,
    /** Текстовая инструкция формата в system prompt */
    STRUCTURED_HINT,
    /** Инструкция отвечать строго в JSON */
    STRUCTURED_JSON
}

/** Значение max_tokens по умолчанию */
const val DEFAULT_MAX_TOKENS = 4096

/** Максимально разумный лимит max_tokens для Claude Sonnet */
const val MAX_TOKENS_LIMIT = 8192

/** Минимальное значение max_tokens */
const val MIN_MAX_TOKENS = 256

/** Температура по умолчанию (баланс точности и креативности) */
const val DEFAULT_TEMPERATURE = 0.5

/** Минимальная температура (детерминированные ответы) */
const val MIN_TEMPERATURE = 0.0

/** Максимальная температура (максимальная креативность) */
const val MAX_TEMPERATURE = 1.0

/** Шаг слайдера температуры */
const val TEMPERATURE_STEP = 0.1

/** Размер контекстного окна по умолчанию (последние N сообщений отправляются сырыми) */
const val DEFAULT_CONTEXT_WINDOW_SIZE = 10

/** Минимальный размер контекстного окна */
const val MIN_CONTEXT_WINDOW_SIZE = 4

/** Максимальный размер контекстного окна */
const val MAX_CONTEXT_WINDOW_SIZE = 40

/** Шаг слайдера контекстного окна */
const val CONTEXT_WINDOW_STEP = 2
