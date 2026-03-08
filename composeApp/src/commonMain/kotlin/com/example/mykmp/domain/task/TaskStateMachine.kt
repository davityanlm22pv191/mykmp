package com.example.mykmp.domain.task

interface TaskStateMachine {
    val activeTask: Task?

    suspend fun startTask(newTask: NewTask): Task
    suspend fun advance(reason: String = ""): Task
    suspend fun pause(reason: String = ""): Task
    suspend fun resume(): Task
    suspend fun fail(reason: String = ""): Task
    suspend fun complete(): Task
    suspend fun setActiveTask(taskId: String)
    suspend fun reset()

    fun getSystemPromptAddition(): String?
    suspend fun onMessageReceived(responseText: String)
    fun getNextExpectedAction(): ExpectedAction?

    var onStateChanged: ((Task?) -> Unit)?
    var onSuggestionReady: ((ExpectedAction) -> Unit)?
}
