package com.example.mykmp.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.DEFAULT_MAX_TOKENS
import com.example.mykmp.domain.model.MAX_TOKENS_LIMIT
import com.example.mykmp.domain.model.MIN_MAX_TOKENS
import com.example.mykmp.domain.model.ResponseFormatMode
import kotlin.math.roundToInt

/**
 * Панель настроек параметров запроса к Claude API.
 * Раскрывается над полем ввода в ChatScreen.
 */
@Composable
fun SettingsPanel(
    config: ChatRequestConfig,
    onUpdateConfig: (ChatRequestConfig) -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // === Заголовок ===
            Text(
                text = "Настройки запроса",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // === Блок: Формат ответа ===
            ResponseFormatSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Max tokens ===
            MaxTokensSection(config, onUpdateConfig)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // === Блок: Stop sequences ===
            StopSequencesSection(config, onUpdateConfig)

            Spacer(Modifier.height(12.dp))

            // === Кнопка сброса ===
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить настройки")
            }
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
            label = { Text("Свободный") }
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
            text = "default: $DEFAULT_MAX_TOKENS",
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
