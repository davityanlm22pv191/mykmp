package com.example.mykmp.data.storage

import java.io.File

/**
 * Android: хранение в internal storage приложения.
 * Контекст инициализируется через AndroidStorageInit.init(context) в MainActivity.
 */
object AndroidStorageInit {
    internal var filesDir: File? = null

    fun init(filesDir: File) {
        this.filesDir = filesDir
    }
}

private fun getStorageFile(key: String): File {
    val dir = AndroidStorageInit.filesDir
        ?: throw IllegalStateException("AndroidStorageInit.init(context.filesDir) not called")
    return File(dir, "$key.json")
}

actual fun saveToStorage(key: String, value: String) {
    getStorageFile(key).writeText(value)
}

actual fun loadFromStorage(key: String): String? {
    val file = getStorageFile(key)
    return if (file.exists()) file.readText() else null
}
