package com.example.mykmp.data.storage

import kotlinx.browser.localStorage

actual fun saveToStorage(key: String, value: String) {
    localStorage.setItem(key, value)
}

actual fun loadFromStorage(key: String): String? {
    return localStorage.getItem(key)
}
