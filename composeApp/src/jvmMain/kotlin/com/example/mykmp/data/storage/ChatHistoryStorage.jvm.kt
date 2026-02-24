package com.example.mykmp.data.storage

import java.io.File

private fun getStorageFile(key: String): File {
    val dir = File(System.getProperty("user.home"), ".claude-chat")
    dir.mkdirs()
    return File(dir, "$key.json")
}

actual fun saveToStorage(key: String, value: String) {
    getStorageFile(key).writeText(value)
}

actual fun loadFromStorage(key: String): String? {
    val file = getStorageFile(key)
    return if (file.exists()) file.readText() else null
}
