package com.example.mykmp.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.sqljs.initSqlDriver
import com.example.mykmp.database.TaskDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // JS — in-memory SQLite via sql.js; no persistence
        return initSqlDriver(TaskDatabase.Schema).also {
            // Schema created by initSqlDriver
        }
    }
}
