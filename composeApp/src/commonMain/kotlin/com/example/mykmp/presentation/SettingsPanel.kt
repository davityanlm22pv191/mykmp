package com.example.mykmp.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mykmp.domain.context.ContextStrategyType
import com.example.mykmp.domain.model.AVAILABLE_MODELS
import com.example.mykmp.domain.model.CONTEXT_WINDOW_STEP
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.DEFAULT_CONTEXT_WINDOW_SIZE
import com.example.mykmp.domain.model.DEFAULT_MAX_TOKENS
import com.example.mykmp.domain.model.DEFAULT_TEMPERATURE
import com.example.mykmp.domain.model.MAX_TEMPERATURE
import com.example.mykmp.domain.model.MAX_TOKENS_LIMIT
import com.example.mykmp.domain.model.MAX_CONTEXT_WINDOW_SIZE
import com.example.mykmp.domain.model.MIN_CONTEXT_WINDOW_SIZE
import com.example.mykmp.domain.model.MIN_MAX_TOKENS
import com.example.mykmp.domain.model.MIN_TEMPERATURE
import com.example.mykmp.domain.model.ModelTier
import com.example.mykmp.domain.model.ResponseFormatMode
import com.example.mykmp.domain.model.TEMPERATURE_STEP
import kotlin.math.roundToInt

/**
 * Панель настроек параметров запроса к Claude API.
 * Раскрывается над полем ввода в ChatScreen.
 */
@Composable
fun SettingsPanel(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit,
    onReset: () -> Unit,
    availableStrategies: List<ContextStrategyType> = emptyList(),
    onSwitchStrategy: (ContextStrategyType) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .heightIn(max = 400.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // === Заголовок ===
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = "Настройки запроса",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // === Блок: Выбор модели ===
            ModelSelectionSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Формат ответа ===
            ResponseFormatSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Max tokens ===
            MaxTokensSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Stop sequences ===
            StopSequencesSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Температура ===
            TemperatureSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Стратегия контекста ===
            ContextStrategySection(config, onUpdateConfig, availableStrategies, onSwitchStrategy)

            Spacer(Modifier.height(12.dp))

            // === Кнопка сброса ===
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить настройки")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Секция выбора формата ответа.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResponseFormatSection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit
) {
    Text(
        text = "Формат ответа",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = config.responseFormatMode == ResponseFormatMode.FREE_TEXT,
            onClick = { onUpdateConfig(config.copy(responseFormatMode = ResponseFormatMode.FREE_TEXT)) },
            label = { Text("Свободный ответ") }
        )
        FilterChip(
            selected = config.responseFormatMode == ResponseFormatMode.STRUCTURED_HINT,
            onClick = { onUpdateConfig(config.copy(responseFormatMode = ResponseFormatMode.STRUCTURED_HINT)) },
            label = { Text("Hint-инструкция") }
        )
        FilterChip(
            selected = config.responseFormatMode == ResponseFormatMode.STRUCTURED_JSON,
            onClick = { onUpdateConfig(config.copy(responseFormatMode = ResponseFormatMode.STRUCTURED_JSON)) },
            label = { Text("JSON Schema") }
        )
    }

    // Поле для hint-инструкции
    if (config.responseFormatMode == ResponseFormatMode.STRUCTURED_HINT) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = config.formatHint,
            onValueChange = { onUpdateConfig(config.copy(formatHint = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Инструкция формата") },
            placeholder = { Text("Например: Отвечай списком из 3 пунктов") },
            minLines = 2,
            maxLines = 4
        )
    }

    // Пояснение для JSON mode
    if (config.responseFormatMode == ResponseFormatMode.STRUCTURED_JSON) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Claude будет отвечать строго в JSON-формате. " +
                "В system prompt добавится инструкция: " +
                "\"Respond strictly in valid JSON format.\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Секция настройки max_tokens.
 */
@Composable
private fun MaxTokensSection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit
) {
    Text(
        text = "Ограничение длины ответа (max_tokens)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    // Переключатель "Максимум"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Максимум ($MAX_TOKENS_LIMIT)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = config.useMaxTokensLimit,
            onCheckedChange = { onUpdateConfig(config.copy(useMaxTokensLimit = it)) }
        )
    }

    // Слайдер (disabled при useMaxTokensLimit)
    val displayValue = if (config.useMaxTokensLimit) MAX_TOKENS_LIMIT else config.maxTokens

    Text(
        text = "$displayValue tokens",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Slider(
        value = displayValue.toFloat(),
        onValueChange = { newValue ->
            // Округляем до ближайших 256
            val rounded = ((newValue / 256).roundToInt() * 256).coerceIn(MIN_MAX_TOKENS, MAX_TOKENS_LIMIT)
            onUpdateConfig(config.copy(maxTokens = rounded))
        },
        valueRange = MIN_MAX_TOKENS.toFloat()..MAX_TOKENS_LIMIT.toFloat(),
        enabled = !config.useMaxTokensLimit,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$MIN_MAX_TOKENS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "По умолчанию: $DEFAULT_MAX_TOKENS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$MAX_TOKENS_LIMIT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Секция настройки stop_sequences.
 */
@Composable
private fun StopSequencesSection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit
) {
    Text(
        text = "Условия завершения (stop_sequences)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    // Переключатель
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Использовать stop_sequences",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = config.useStopSequences,
            onCheckedChange = { onUpdateConfig(config.copy(useStopSequences = it)) }
        )
    }

    // Список стоп-последовательностей
    if (config.useStopSequences) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Каждая строка — отдельная стоп-последовательность. " +
                "Claude остановит генерацию, встретив любую из них.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        // Показываем существующие стоп-последовательности
        config.stopSequences.forEachIndexed { index, sequence ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                OutlinedTextField(
                    value = sequence,
                    onValueChange = { newValue ->
                        val updated = config.stopSequences.toMutableList()
                        updated[index] = newValue
                        onUpdateConfig(config.copy(stopSequences = updated))
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Stop #${index + 1}") }
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        val updated = config.stopSequences.toMutableList()
                        updated.removeAt(index)
                        onUpdateConfig(config.copy(stopSequences = updated))
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("\u00D7", style = MaterialTheme.typography.titleMedium) // ×
                }
            }
        }

        // Кнопка добавления
        OutlinedButton(
            onClick = {
                val updated = config.stopSequences + ""
                onUpdateConfig(config.copy(stopSequences = updated))
            },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("+ Добавить")
        }
    }
}

/**
 * Секция настройки температуры (temperature).
 *
 * Температура влияет на «случайность» ответов Claude:
 * - 0.0 — максимально детерминированные, предсказуемые ответы
 * - 0.7 — баланс точности и креативности (значение по умолчанию)
 * - 1.2+ — более креативные, разнообразные, но менее предсказуемые ответы
 *
 * Для сравнения поведения модели при разных температурах:
 * 1. Отключите «Значение по умолчанию модели»
 * 2. Установите temperature = 0.0 и отправьте запрос
 * 3. Повторите с temperature = 0.7 и 1.2
 * 4. Сравните ответы в чате или логах (temperature видна в консоли)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemperatureSection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit
) {
    Text(
        text = "Температура (temperature)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    // Переключатель «Значение по умолчанию модели»
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Значение по умолчанию модели",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = config.useDefaultTemperature,
            onCheckedChange = { onUpdateConfig(config.copy(useDefaultTemperature = it)) }
        )
    }

    // Текущее значение
    val displayTemp = if (config.useDefaultTemperature) "по умолчанию" else "%.1f".format(config.temperature)
    Text(
        text = "Температура: $displayTemp",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(4.dp))

    // Слайдер
    Slider(
        value = config.temperature.toFloat(),
        onValueChange = { newValue ->
            // Округляем до шага 0.1
            val rounded = (newValue / TEMPERATURE_STEP).roundToInt() * TEMPERATURE_STEP
            onUpdateConfig(config.copy(temperature = rounded.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)))
        },
        valueRange = MIN_TEMPERATURE.toFloat()..MAX_TEMPERATURE.toFloat(),
        enabled = !config.useDefaultTemperature,
        modifier = Modifier.fillMaxWidth()
    )

    // Метки шкалы
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("0.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.height(8.dp))

    // Быстрые пресеты
    Text(
        text = "Быстрые пресеты:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(4.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Пресет 0.0 — точность
        FilterChip(
            selected = !config.useDefaultTemperature && config.temperature == MIN_TEMPERATURE,
            onClick = {
                onUpdateConfig(config.copy(temperature = MIN_TEMPERATURE, useDefaultTemperature = false))
            },
            label = { Text("0.0 — Точный") },
            enabled = !config.useDefaultTemperature
        )
        // Пресет 0.5 — баланс
        FilterChip(
            selected = !config.useDefaultTemperature && config.temperature == DEFAULT_TEMPERATURE,
            onClick = {
                onUpdateConfig(config.copy(temperature = DEFAULT_TEMPERATURE, useDefaultTemperature = false))
            },
            label = { Text("0.5 — Баланс") },
            enabled = !config.useDefaultTemperature
        )
        // Пресет 1 — креатив
        FilterChip(
            selected = !config.useDefaultTemperature && config.temperature == MAX_TEMPERATURE,
            onClick = {
                onUpdateConfig(config.copy(temperature = MAX_TEMPERATURE, useDefaultTemperature = false))
            },
            label = { Text("1 — Креатив") },
            enabled = !config.useDefaultTemperature
        )
    }

    Spacer(Modifier.height(8.dp))

    // Подсказка, зависящая от текущего значения
    val hint = if (config.useDefaultTemperature) {
        "API использует свою температуру по умолчанию (обычно ~1.0)."
    } else when {
        config.temperature <= 0.2 -> "Точные, стабильные, предсказуемые ответы. " +
            "Подходит для: генерации кода, проверки фактов, строгих инструкций."
        config.temperature <= 0.7 -> "Баланс точности и креативности. " +
            "Подходит для: объяснений, улучшения текста, решений с лёгким творческим компонентом."
        else -> "Креативные, разнообразные, менее предсказуемые ответы. " +
            "Подходит для: генерации идей, сюжетов, нестандартных формулировок."
    }

    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Секция выбора модели Claude (WEAK / MEDIUM / STRONG).
 *
 * Для сравнения моделей:
 * 1. Выберите слабую модель → отправьте запрос → запомните время/токены/стоимость.
 * 2. Переключитесь на среднюю → повторите тот же запрос.
 * 3. Переключитесь на сильную → повторите.
 * 4. Сравните качество ответов, скорость и стоимость.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSelectionSection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit
) {
    Text(
        text = "Модель",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AVAILABLE_MODELS.forEach { model ->
            val tierLabel = when (model.tier) {
                ModelTier.WEAK -> "Быстрая"
                ModelTier.MEDIUM -> "Баланс"
                ModelTier.STRONG -> "Качество"
            }

            FilterChip(
                selected = config.selectedModel.id == model.id,
                onClick = { onUpdateConfig(config.copy(selectedModel = model)) },
                label = { Text("${model.displayName} ($tierLabel)") }
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    // Описание выбранной модели
    Text(
        text = config.selectedModel.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Цена
    Text(
        text = "Цена: \$${config.selectedModel.inputPricePer1M}/1M вх. " +
            "+ \$${config.selectedModel.outputPricePer1M}/1M вых. токенов",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Секция выбора стратегии контекста и настройки контекстного окна.
 * Показывает доступные стратегии как чипы, описание активной стратегии, слайдер окна.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextStrategySection(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit,
    availableStrategies: List<ContextStrategyType>,
    onSwitchStrategy: (ContextStrategyType) -> Unit
) {
    Text(
        text = "Стратегия контекста",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    // Чипы выбора стратегии
    if (availableStrategies.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableStrategies.forEach { strategy ->
                FilterChip(
                    selected = config.contextStrategyType == strategy,
                    onClick = { onSwitchStrategy(strategy) },
                    label = { Text(strategy.displayName()) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Описание активной стратегии
        Text(
            text = config.contextStrategyType.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))

    // Слайдер размера окна (общий для всех стратегий)
    Text(
        text = "Размер окна: ${config.contextWindowSize} сообщений",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Slider(
        value = config.contextWindowSize.toFloat(),
        onValueChange = { newValue ->
            val rounded = ((newValue / CONTEXT_WINDOW_STEP).roundToInt() * CONTEXT_WINDOW_STEP)
                .coerceIn(MIN_CONTEXT_WINDOW_SIZE, MAX_CONTEXT_WINDOW_SIZE)
            onUpdateConfig(config.copy(contextWindowSize = rounded))
        },
        valueRange = MIN_CONTEXT_WINDOW_SIZE.toFloat()..MAX_CONTEXT_WINDOW_SIZE.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$MIN_CONTEXT_WINDOW_SIZE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "По умолчанию: $DEFAULT_CONTEXT_WINDOW_SIZE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$MAX_CONTEXT_WINDOW_SIZE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
