package com.example.mykmp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mykmp.domain.task.Task
import com.example.mykmp.domain.task.TaskStateEnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDashboardScreen(
    viewModel: TaskDashboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    if (uiState.showNewTaskDialog) {
        NewTaskDialog(
            onConfirm = { title, desc -> viewModel.onCreateTask(title, desc) },
            onDismiss = { viewModel.onToggleNewTaskDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Задачи", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Чат", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onToggleNewTaskDialog() }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Активные (${uiState.activeTasks.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Архив (${uiState.archivedTasks.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val tasks = if (selectedTab == 0) uiState.activeTasks else uiState.archivedTasks
                if (tasks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedTab == 0) "Нет активных задач. Нажми + для создания."
                            else "Архив пуст.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.taskId }) { task ->
                            TaskCard(
                                task = task,
                                onSetActive = { viewModel.onSetActive(task.taskId) },
                                onAdvance = { viewModel.onAdvance(task.taskId) },
                                onPause = { viewModel.onPause(task.taskId) },
                                onResume = { viewModel.onResume(task.taskId) },
                                onFail = { viewModel.onFail(task.taskId) },
                                onDelete = { viewModel.onDelete(task.taskId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onSetActive: () -> Unit,
    onAdvance: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFail: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = task.state.badgeColor()
                    ) {
                        Text(
                            text = task.state.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Text("✕", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            // Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.weight(1f).height(4.dp)
                )
                Text(
                    "${(task.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.currentStep != null) {
                Text(
                    "Шаг: ${task.currentStep.displayName()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val isTerminal = task.state == TaskStateEnum.DONE || task.state == TaskStateEnum.FAILED
                if (!isTerminal) {
                    OutlinedButton(
                        onClick = onSetActive,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Открыть", style = MaterialTheme.typography.labelSmall)
                    }
                }
                when (task.state) {
                    TaskStateEnum.PLANNING, TaskStateEnum.VALIDATION -> {
                        OutlinedButton(
                            onClick = onAdvance,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("→ Далее", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TaskStateEnum.EXECUTION -> {
                        OutlinedButton(
                            onClick = onAdvance,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("→ Далее", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("⏸", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onFail,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("✕ Ошибка",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TaskStateEnum.PAUSED -> {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("▶ Продолжить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(
    onConfirm: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (опционально)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title, description) },
                enabled = title.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun TaskStateEnum.badgeColor(): Color = when (this) {
    TaskStateEnum.PLANNING   -> Color(0xFFF59E0B)
    TaskStateEnum.EXECUTION  -> Color(0xFF3B82F6)
    TaskStateEnum.VALIDATION -> Color(0xFFF97316)
    TaskStateEnum.PAUSED     -> Color(0xFF6B7280)
    TaskStateEnum.DONE       -> Color(0xFF10B981)
    TaskStateEnum.FAILED     -> Color(0xFFEF4444)
    TaskStateEnum.IDLE       -> Color(0xFF9CA3AF)
}

