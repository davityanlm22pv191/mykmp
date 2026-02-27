package com.example.mykmp.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ConversationSummary
import kotlinx.coroutines.launch
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.ic_copy_black
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Основной экран чата.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Автоскролл к последнему сообщению при добавлении нового
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        val itemCount = uiState.messages.size + if (uiState.isLoading) 1 else 0
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Claude Chat", style = MaterialTheme.typography.titleMedium)
                        if (uiState.conversationStats.totalTokens > 0) {
                            val stats = uiState.conversationStats
                            Text(
                                text = "\u03A3 ${formatTokenCount(stats.totalInputTokens)} in \u00B7 " +
                                        "${formatTokenCount(stats.totalOutputTokens)} out \u00B7 " +
                                        "\$%.4f".format(stats.totalCostUsd),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearHistory) {
                            Text(
                                text = "\uD83D\uDDD1",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Баннер гео-блокировки
            if (uiState.isGeoBlocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u26A0\uFE0F",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Включите VPN для доступа к API",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Баннер суммаризации
            if (uiState.conversationSummary != null || uiState.isSummarizing) {
                SummaryBanner(
                    summary = uiState.conversationSummary,
                    isSummarizing = uiState.isSummarizing,
                    isExpanded = uiState.isSummaryExpanded,
                    onToggleExpanded = viewModel::onToggleSummaryExpanded
                )
            }

            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) }
                    )
                }

                // Индикатор загрузки
                if (uiState.isLoading) {
                    item(key = "loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Claude думает...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Панель настроек (раскрывается над полем ввода)
            AnimatedVisibility(
                visible = uiState.isSettingsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SettingsPanel(
                    config = uiState.requestConfig,
                    onUpdateConfig = viewModel::onUpdateConfig,
                    onReset = viewModel::onResetConfig
                )
            }

            // Оценка токенов перед отправкой
            if (uiState.inputText.isNotBlank() && uiState.estimatedInputTokens > 0) {
                Text(
                    text = "~${formatTokenCount(uiState.estimatedInputTokens)} tok",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp, bottom = 2.dp)
                )
            }

            // Поле ввода и кнопка отправки
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка настроек ☰
                IconButton(
                    onClick = viewModel::onToggleSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = if (uiState.isSettingsExpanded) "\u2715" else "\u2630", // ✕ или ☰
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.width(4.dp))

                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { keyEvent ->
                            // Enter отправляет, Shift+Enter — новая строка
                            if (keyEvent.key == Key.Enter
                                && keyEvent.type == KeyEventType.KeyDown
                                && !keyEvent.isShiftPressed
                            ) {
                                viewModel.onSendMessage()
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = {
                        Text(
                            "Че там",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onTertiaryFixedVariant)
                        )
                    },
                    maxLines = 5,
                    label = { Text("Пиши сюда своё сообщение") },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.width(8.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = viewModel::onSendMessage,
                        enabled = uiState.inputText.isNotBlank() && !uiState.isLoading
                    ) {
                        Text(
                            text = "\u2191",  // Стрелка вверх
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Форматирует число токенов в компактный вид: 999, 1.2k, 15k
 */
private fun formatTokenCount(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 10_000 -> "%.1fk".format(count / 1000.0)
    else -> "%.0fk".format(count / 1000.0)
}

/**
 * Баннер суммаризации: показывает количество покрытых сообщений,
 * статус суммаризации и раскрываемый текст резюме.
 */
@Composable
private fun SummaryBanner(
    summary: ConversationSummary?,
    isSummarizing: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSummarizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Суммаризация...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else if (summary != null) {
                    Text(
                        text = "\uD83D\uDCDD Суммаризировано ${summary.coveredMessageCount} сообщ.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (summary != null) {
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = if (isExpanded) "\u25B2" else "\u25BC",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded && summary != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            summary?.let {
                Text(
                    text = it.summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Пузырь сообщения в чате с кнопкой копирования.
 * User — справа (primary), Assistant — слева (surface), Error — слева (error).
 *
 * @param message данные сообщения
 * @param onCopy callback для копирования текста в буфер обмена
 */
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit
) {
    val isUser = message.role == ChatMessage.Role.USER

    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val copyAlignment = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Column(
            modifier = Modifier.widthIn(max = 500.dp)
        ) {
            // Пузырь с текстом (Markdown для ассистента, plain text для пользователя)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                if (isUser || message.isError) {
                    // Пользовательские и ошибочные сообщения — обычный текст
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    // Ответы ассистента — рендерим Markdown с возможностью выделения
                    SelectionContainer {
                        Markdown(
                            content = message.text,
                            colors = markdownColor(text = textColor),
                            typography = markdownTypography(
                                text = MaterialTheme.typography.bodyLarge
                            )
                        )
                    }
                }
            }

            // Панель действий и метаданных под пузырём
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = copyAlignment,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка «Копировать»
                IconButton(
                    onClick = { onCopy(message.text) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(Res.drawable.ic_copy_black),
                        contentDescription = null
                    )
                }

                // Метаданные ответа (время, токены, стоимость, модель)
                if (message.responseTimeMs != null) {
                    Spacer(Modifier.width(4.dp))
                    val meta = buildString {
                        // Время ответа
                        val seconds = message.responseTimeMs / 1000.0
                        append("%.2f s".format(seconds))

                        // Токены (input / output раздельно)
                        message.tokensUsage?.let {
                            append(" \u00B7 ${formatTokenCount(it.inputTokens)} in \u00B7 ${formatTokenCount(it.outputTokens)} out")
                        }

                        // Стоимость
                        message.costUsd?.let {
                            append(" \u00B7 \$%.4f".format(it))
                        }

                        // Краткое имя модели
                        message.modelDisplayName?.let {
                            append(" \u00B7 $it")
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
