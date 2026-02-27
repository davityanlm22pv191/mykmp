package com.example.mykmp.domain.model

import kotlinx.serialization.Serializable

/**
 * Сериализуемое представление пользовательских настроек.
 * Модель хранится как ID, а не как объект ModelInfo.
 */
@Serializable
data class SavedSettings(
    val responseFormatMode: String = "FREE_TEXT",
    val formatHint: String = "",
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val useMaxTokensLimit: Boolean = false,
    val useStopSequences: Boolean = false,
    val stopSequences: List<String> = emptyList(),
    val temperature: Double = DEFAULT_TEMPERATURE,
    val useDefaultTemperature: Boolean = true,
    val selectedModelId: String = DEFAULT_MODEL.id,
    val contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE
)

fun ChatRequestConfig.toSavedSettings() = SavedSettings(
    responseFormatMode = responseFormatMode.name,
    formatHint = formatHint,
    maxTokens = maxTokens,
    useMaxTokensLimit = useMaxTokensLimit,
    useStopSequences = useStopSequences,
    stopSequences = stopSequences,
    temperature = temperature,
    useDefaultTemperature = useDefaultTemperature,
    selectedModelId = selectedModel.id,
    contextWindowSize = contextWindowSize
)

fun SavedSettings.toConfig() = ChatRequestConfig(
    responseFormatMode = try {
        ResponseFormatMode.valueOf(responseFormatMode)
    } catch (_: Exception) {
        ResponseFormatMode.FREE_TEXT
    },
    formatHint = formatHint,
    maxTokens = maxTokens,
    useMaxTokensLimit = useMaxTokensLimit,
    useStopSequences = useStopSequences,
    stopSequences = stopSequences,
    temperature = temperature,
    useDefaultTemperature = useDefaultTemperature,
    selectedModel = AVAILABLE_MODELS.firstOrNull { it.id == selectedModelId } ?: DEFAULT_MODEL,
    contextWindowSize = contextWindowSize
)
