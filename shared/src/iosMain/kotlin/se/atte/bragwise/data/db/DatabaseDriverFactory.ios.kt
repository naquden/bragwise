package se.atte.bragwise.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import se.atte.bragwise.db.BragwiseDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(BragwiseDatabase.Schema, "bragwise.db")
}
