package com.example.mykmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mykmp.domain.invariant.Invariant
import com.example.mykmp.domain.invariant.InvariantManager
import com.example.mykmp.domain.invariant.NewInvariant
import com.example.mykmp.domain.task.Task
import com.example.mykmp.domain.task.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InvariantsUiState(
    val globalInvariants: List<Invariant> = emptyList(),
    val taskInvariants: List<Invariant> = emptyList(),
    val tasks: List<Task> = emptyList(),           // список FSM-задач для picker'а
    val selectedTaskId: String? = null,
    val isLoading: Boolean = false,
    val showNewInvariantDialog: Boolean = false
)

class InvariantsViewModel(
    private val invariantManager: InvariantManager,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvariantsUiState())
    val uiState: StateFlow<InvariantsUiState> = _uiState.asStateFlow()

    init {
        invariantManager.onStateChanged = { reload() }
        reload()
    }

    fun onToggleDialog() {
        _uiState.value = _uiState.value.copy(
            showNewInvariantDialog = !_uiState.value.showNewInvariantDialog
        )
    }

    fun onSelectTask(taskId: String?) {
        _uiState.value = _uiState.value.copy(selectedTaskId = taskId)
        reloadTaskInvariants(taskId)
    }

    fun onAddInvariant(new: NewInvariant) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showNewInvariantDialog = false)
            invariantManager.addInvariant(new)
            reload()
        }
    }

    fun onRemoveInvariant(id: String) {
        viewModelScope.launch {
            invariantManager.removeInvariant(id)
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val globalInvariants = invariantManager.getInvariants(taskId = null)
            val tasks = taskRepository.getAllTasks()
            val selectedTaskId = _uiState.value.selectedTaskId
            val taskInvariants = if (selectedTaskId != null) {
                // per-task инварианты (не включая глобальные)
                invariantManager.getAllInvariants().filter { it.taskId == selectedTaskId }
            } else {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                globalInvariants = globalInvariants,
                taskInvariants = taskInvariants,
                tasks = tasks,
                isLoading = false
            )
        }
    }

    private fun reloadTaskInvariants(taskId: String?) {
        viewModelScope.launch {
            val taskInvariants = if (taskId != null) {
                invariantManager.getAllInvariants().filter { it.taskId == taskId }
            } else {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(taskInvariants = taskInvariants)
        }
    }
}
