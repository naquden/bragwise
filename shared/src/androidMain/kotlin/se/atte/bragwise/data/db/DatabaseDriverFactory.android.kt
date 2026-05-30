package se.atte.bragwise.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import se.atte.bragwise.db.BragwiseDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(BragwiseDatabase.Schema, context, "bragwise.db")
}
