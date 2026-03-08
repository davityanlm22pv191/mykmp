package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.domain.task.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskDashboardUiState(
    val activeTasks: List<Task> = emptyList(),
    val archivedTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val showNewTaskDialog: Boolean = false
)

class TaskDashboardViewModel(
    private val taskStateMachine: TaskStateMachine,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDashboardUiState())
    val uiState: StateFlow<TaskDashboardUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
        taskStateMachine.onStateChanged = { syncTasks() }
    }

    fun onToggleNewTaskDialog() {
        _uiState.value = _uiState.value.copy(showNewTaskDialog = !_uiState.value.showNewTaskDialog)
    }

    fun onCreateTask(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showNewTaskDialog = false)
            taskStateMachine.startTask(NewTask(title.trim(), description.trim()))
            loadTasks()
        }
    }

    fun onSetActive(taskId: String) {
        viewModelScope.launch {
            taskStateMachine.setActiveTask(taskId)
        }
    }

    fun onAdvance(taskId: String) {
        viewModelScope.launch {
            if (taskStateMachine.activeTask?.taskId != taskId) {
                taskStateMachine.setActiveTask(taskId)
            }
            taskStateMachine.advance()
            loadTasks()
        }
    }

    fun onPause(taskId: String) {
        viewModelScope.launch {
            if (taskStateMachine.activeTask?.taskId != taskId) {
                taskStateMachine.setActiveTask(taskId)
            }
            taskStateMachine.pause()
            loadTasks()
        }
    }

    fun onResume(taskId: String) {
        viewModelScope.launch {
            if (taskStateMachine.activeTask?.taskId != taskId) {
                taskStateMachine.setActiveTask(taskId)
            }
            taskStateMachine.resume()
            loadTasks()
        }
    }

    fun onFail(taskId: String) {
        viewModelScope.launch {
            if (taskStateMachine.activeTask?.taskId != taskId) {
                taskStateMachine.setActiveTask(taskId)
            }
            taskStateMachine.fail()
            loadTasks()
        }
    }

    fun onDelete(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            if (taskStateMachine.activeTask?.taskId == taskId) {
                taskStateMachine.reset()
            }
            loadTasks()
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val all = taskRepository.getAllTasks()
            val archived = setOf(TaskStateEnum.DONE, TaskStateEnum.FAILED)
            _uiState.value = _uiState.value.copy(
                activeTasks = all.filter { it.state !in archived },
                archivedTasks = all.filter { it.state in archived },
                isLoading = false
            )
        }
    }

    private fun syncTasks() {
        viewModelScope.launch { loadTasks() }
    }
}
