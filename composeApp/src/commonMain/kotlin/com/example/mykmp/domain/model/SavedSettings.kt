package com.example.mykmp.domain.model

import com.example.mykmp.domain.context.ContextStrategyType
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
    val contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE,
    val contextStrategyType: String = "ROLLING_SUMMARY"
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
    contextWindowSize = contextWindowSize,
    contextStrategyType = contextStrategyType.name
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
    contextWindowSize = contextWindowSize,
    contextStrategyType = try {
        ContextStrategyType.valueOf(contextStrategyType)
    } catch (_: Exception) {
        ContextStrategyType.ROLLING_SUMMARY
    }
)
