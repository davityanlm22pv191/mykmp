package com.example.mykmp.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mykmp.domain.memory.LongTermFact
import com.example.mykmp.domain.memory.MemoryCategory
import com.example.mykmp.domain.memory.TaskMemory

/**
 * Панель управления памятью ассистента.
 * Две секции: рабочая память (по задачам) и долговременная (факты).
 */
@Composable
fun MemoryPanel(
    taskList: List<TaskMemory>,
    activeTaskId: String?,
    longTermFacts: List<LongTermFact>,
    memorySearchQuery: String,
    memorySearchResults: List<LongTermFact>,
    onCreateTask: (taskId: String, name: String) -> Unit,
    onSetActiveTask: (taskId: String) -> Unit,
    onAddWorkingEntry: (taskId: String, key: String, value: String) -> Unit,
    onRemoveWorkingEntry: (taskId: String, key: String) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    onAddFact: (category: String, key: String, value: String) -> Unit,
    onDeleteFact: (factId: String) -> Unit,
    onSearchMemory: (query: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === Рабочая память ===
            WorkingMemorySection(
                taskList = taskList,
                activeTaskId = activeTaskId,
                onCreateTask = onCreateTask,
                onSetActiveTask = onSetActiveTask,
                onAddWorkingEntry = onAddWorkingEntry,
                onRemoveWorkingEntry = onRemoveWorkingEntry,
                onDeleteTask = onDeleteTask
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // === Долговременная память ===
            LongTermMemorySection(
                facts = longTermFacts,
                searchQuery = memorySearchQuery,
                searchResults = memorySearchResults,
                onAddFact = onAddFact,
                onDeleteFact = onDeleteFact,
                onSearch = onSearchMemory
            )
        }
    }
}

// === Рабочая память ===

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkingMemorySection(
    taskList: List<TaskMemory>,
    activeTaskId: String?,
    onCreateTask: (taskId: String, name: String) -> Unit,
    onSetActiveTask: (taskId: String) -> Unit,
    onAddWorkingEntry: (taskId: String, key: String, value: String) -> Unit,
    onRemoveWorkingEntry: (taskId: String, key: String) -> Unit,
    onDeleteTask: (taskId: String) -> Unit
) {
    Text(
        text = "Рабочая память",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )

    // Список задач (чипы)
    if (taskList.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            taskList.forEach { task ->
                FilterChip(
                    selected = task.taskId == activeTaskId,
                    onClick = { onSetActiveTask(task.taskId) },
                    label = {
                        Text(task.name, style = MaterialTheme.typography.labelSmall)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDeleteTask(task.taskId) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Text("\u00D7", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
        }
    }

    // Форма создания задачи
    var newTaskId by remember { mutableStateOf("") }
    var newTaskName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newTaskId,
            onValueChange = { newTaskId = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("task-id", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = newTaskName,
            onValueChange = { newTaskName = it },
            modifier = Modifier.weight(1.5f),
            placeholder = { Text("Название задачи", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                if (newTaskId.isNotBlank() && newTaskName.isNotBlank()) {
                    onCreateTask(newTaskId.trim(), newTaskName.trim())
                    newTaskId = ""
                    newTaskName = ""
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }
    }

    // Записи активной задачи
    val activeTask = taskList.find { it.taskId == activeTaskId }
    if (activeTask != null && activeTask.entries.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.small
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            activeTask.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${entry.key}: ${entry.value}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveWorkingEntry(activeTask.taskId, entry.key) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            "\u00D7",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Форма добавления записи в активную задачу
    if (activeTask != null) {
        var entryKey by remember { mutableStateOf("") }
        var entryValue by remember { mutableStateOf("") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = entryKey,
                onValueChange = { entryKey = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ключ", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = entryValue,
                onValueChange = { entryValue = it },
                modifier = Modifier.weight(1.5f),
                placeholder = { Text("Значение", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = {
                    if (entryKey.isNotBlank() && entryValue.isNotBlank()) {
                        onAddWorkingEntry(activeTask.taskId, entryKey.trim(), entryValue.trim())
                        entryKey = ""
                        entryValue = ""
                    }
                },
                modifier = Modifier.height(40.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// === Долговременная память ===

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongTermMemorySection(
    facts: List<LongTermFact>,
    searchQuery: String,
    searchResults: List<LongTermFact>,
    onAddFact: (category: String, key: String, value: String) -> Unit,
    onDeleteFact: (factId: String) -> Unit,
    onSearch: (query: String) -> Unit
) {
    Text(
        text = "Долговременная память",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )

    // Поиск
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { onSearch(it) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Поиск...", style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )

    // Факты по категориям
    val displayFacts = if (searchQuery.isNotBlank()) searchResults else facts
    val grouped = displayFacts.groupBy { it.category }

    if (grouped.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.small
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (category, categoryFacts) ->
                val label = try {
                    MemoryCategory.valueOf(category).displayName()
                } catch (_: Exception) {
                    category
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                categoryFacts.forEach { fact ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fact.key}: ${fact.value}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (fact.source != "manual") {
                                Text(
                                    text = fact.source,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { onDeleteFact(fact.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text(
                                    "\u00D7",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    } else if (facts.isEmpty()) {
        Text(
            text = "Пока нет сохранённых фактов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Форма добавления факта
    var selectedCategory by remember { mutableStateOf(MemoryCategory.KNOWLEDGE.name) }
    var factKey by remember { mutableStateOf("") }
    var factValue by remember { mutableStateOf("") }

    // Выбор категории (чипы)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MemoryCategory.entries.forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat.name,
                onClick = { selectedCategory = cat.name },
                label = { Text(cat.displayName(), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = factKey,
            onValueChange = { factKey = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ключ", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = factValue,
            onValueChange = { factValue = it },
            modifier = Modifier.weight(1.5f),
            placeholder = { Text("Значение", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                if (factKey.isNotBlank() && factValue.isNotBlank()) {
                    onAddFact(selectedCategory, factKey.trim(), factValue.trim())
                    factKey = ""
                    factValue = ""
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }
    }
}
