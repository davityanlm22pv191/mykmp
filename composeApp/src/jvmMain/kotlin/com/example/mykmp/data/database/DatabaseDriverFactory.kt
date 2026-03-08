package com.example.mykmp.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.mykmp.database.TaskDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:tasks.db")
        TaskDatabase.Schema.create(driver)
        return driver
    }
}
