package com.example.mykmp.data.storage

/**
 * Платформо-зависимое key-value хранение строк (JSON).
 * Используется для истории чата, настроек и т.д.
 *
 * @param key ключ (используется как имя файла / ключ в localStorage)
 * @param value значение (JSON-строка)
 */
expect fun saveToStorage(key: String, value: String)

/**
 * Загружает ранее сохранённую строку по ключу.
 *
 * @param key ключ
 * @return сохранённая строка или null, если данных нет
 */
expect fun loadFromStorage(key: String): String?
