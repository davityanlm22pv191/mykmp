package com.example.mykmp.domain.model

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
    val stopSequences: List<String> = emptyList()
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
