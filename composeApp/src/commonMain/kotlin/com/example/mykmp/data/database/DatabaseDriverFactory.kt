package com.example.mykmp.data.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory() {
    fun createDriver(): SqlDriver
}
