package com.example.mykmp.data.database

import app.cash.sqldelight.db.SqlDriver

// SQLDelight не поддерживает wasmJs — задачи не персистируются на этой платформе.
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException("SQLDelight not supported on wasmJs")
    }
}
