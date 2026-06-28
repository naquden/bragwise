package se.atte.bragwise.data.db

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver =
        throw UnsupportedOperationException("SQLite not available on web; guest data uses localStorage")
}
