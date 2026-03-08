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
import com.example.mykmp.domain.invariant.ConstraintPriority
import com.example.mykmp.domain.invariant.Invariant
import com.example.mykmp.domain.invariant.InvariantType
import com.example.mykmp.domain.invariant.NewInvariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvariantsScreen(
    viewModel: InvariantsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    if (uiState.showNewInvariantDialog) {
        NewInvariantDialog(
            tasks = uiState.tasks.filter { t ->
                t.state != com.example.mykmp.domain.task.TaskStateEnum.DONE &&
                t.state != com.example.mykmp.domain.task.TaskStateEnum.FAILED
            },
            onConfirm = { new -> viewModel.onAddInvariant(new) },
            onDismiss = { viewModel.onToggleDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инварианты", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Чат", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onToggleDialog() }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Вкладки: Глобальные / Задача (если есть задачи)
            val hasTasks = uiState.tasks.isNotEmpty()
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Глобальные (${uiState.globalInvariants.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                if (hasTasks) {
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val taskName = uiState.selectedTaskId?.let { id ->
                                uiState.tasks.find { it.taskId == id }?.title?.let { "  $it" } ?: ""
                            } ?: ""
                            Text(
                                "Задача$taskName (${uiState.taskInvariants.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when {
                    selectedTab == 0 -> {
                        // Глобальные инварианты
                        InvariantList(
                            invariants = uiState.globalInvariants,
                            emptyText = "Нет глобальных инвариантов. Нажми + для добавления.",
                            onRemove = { viewModel.onRemoveInvariant(it) }
                        )
                    }
                    selectedTab == 1 && hasTasks -> {
                        // Per-task: picker задачи + список
                        Column {
                            // Горизонтальный ряд чипов для выбора задачи
                            TaskPickerRow(
                                tasks = uiState.tasks,
                                selectedTaskId = uiState.selectedTaskId,
                                onSelect = { viewModel.onSelectTask(it) }
                            )
                            InvariantList(
                                invariants = uiState.taskInvariants,
                                emptyText = if (uiState.selectedTaskId == null)
                                    "Выбери задачу выше"
                                else
                                    "Нет инвариантов для этой задачи.",
                                onRemove = { viewModel.onRemoveInvariant(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvariantList(
    invariants: List<Invariant>,
    emptyText: String,
    onRemove: (String) -> Unit
) {
    if (invariants.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
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
            items(invariants, key = { it.id }) { inv ->
                InvariantCard(
                    invariant = inv,
                    onRemove = { onRemove(inv.id) }
                )
            }
        }
    }
}

@Composable
private fun TaskPickerRow(
    tasks: List<com.example.mykmp.domain.task.Task>,
    selectedTaskId: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tasks.forEach { task ->
            FilterChip(
                selected = task.taskId == selectedTaskId,
                onClick = {
                    onSelect(if (task.taskId == selectedTaskId) null else task.taskId)
                },
                label = {
                    Text(task.title, style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}

@Composable
private fun InvariantCard(
    invariant: Invariant,
    onRemove: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    // Приоритет (цветной badge)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = invariant.priority.badgeColor()
                    ) {
                        Text(
                            text = "${invariant.priority.emoji()} ${invariant.priority.displayName()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // Тип
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = invariant.type.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Text(
                        "🗑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ID инварианта маленьким серым текстом
            Text(
                text = "id: ${invariant.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Текст правила
            Text(
                text = invariant.constraintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Описание (опционально)
            if (invariant.description.isNotBlank()) {
                Text(
                    text = invariant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Диалог создания нового инварианта.
 */
@Composable
private fun NewInvariantDialog(
    tasks: List<com.example.mykmp.domain.task.Task>,
    onConfirm: (NewInvariant) -> Unit,
    onDismiss: () -> Unit
) {
    var constraintText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(InvariantType.TECH_STACK) }
    var selectedPriority by remember { mutableStateOf(ConstraintPriority.HIGH) }
    var isGlobal by remember { mutableStateOf(true) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый инвариант", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Правило
                OutlinedTextField(
                    value = constraintText,
                    onValueChange = { constraintText = it },
                    label = { Text("Правило (ненарушимое ограничение)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Описание
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (опционально)") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Тип
                Text("Тип:", style = MaterialTheme.typography.labelMedium)
                ChipGroup(
                    items = InvariantType.values().map { it to it.displayName() },
                    selected = selectedType,
                    onSelect = { selectedType = it }
                )

                // Приоритет
                Text("Приоритет:", style = MaterialTheme.typography.labelMedium)
                ChipGroup(
                    items = ConstraintPriority.values().map { it to "${it.emoji()} ${it.displayName()}" },
                    selected = selectedPriority,
                    onSelect = { selectedPriority = it }
                )

                // Скоуп
                Text("Скоуп:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isGlobal,
                        onClick = { isGlobal = true; selectedTaskId = null }
                    )
                    Text("Глобальный", style = MaterialTheme.typography.bodySmall)
                }
                if (tasks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isGlobal,
                            onClick = { isGlobal = false }
                        )
                        Text("Привязать к задаче:", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isGlobal) {
                        ChipGroup(
                            items = tasks.map { it.taskId to it.title },
                            selected = selectedTaskId,
                            onSelect = { selectedTaskId = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (constraintText.isNotBlank()) {
                        onConfirm(
                            NewInvariant(
                                type = selectedType,
                                constraintText = constraintText.trim(),
                                description = description.trim(),
                                priority = selectedPriority,
                                taskId = if (isGlobal) null else selectedTaskId
                            )
                        )
                    }
                },
                enabled = constraintText.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/**
 * Горизонтальная группа чипов для выбора одного элемента из enum/списка.
 */
@Composable
private fun <T> ChipGroup(
    items: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (item, label) ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

private fun ConstraintPriority.badgeColor(): Color = when (this) {
    ConstraintPriority.CRITICAL -> Color(0xFFEF4444)
    ConstraintPriority.HIGH     -> Color(0xFFF59E0B)
    ConstraintPriority.MEDIUM   -> Color(0xFF10B981)
}
