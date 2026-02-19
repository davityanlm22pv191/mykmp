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
import androidx.compose.foundation.layout.height
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
                title = { Text("Claude Chat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
            // Пузырь с текстом
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
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Кнопка «Копировать» под пузырём
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = copyAlignment
            ) {
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
            }
        }
    }
}
