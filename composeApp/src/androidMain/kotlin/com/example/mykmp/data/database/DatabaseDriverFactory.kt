package com.example.mykmp.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.mykmp.database.TaskDatabase

object AndroidDatabaseInit {
    internal var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }
}

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val context = AndroidDatabaseInit.context
            ?: throw IllegalStateException("AndroidDatabaseInit.init(context) not called")
        return AndroidSqliteDriver(TaskDatabase.Schema, context, "tasks.db")
    }
}
