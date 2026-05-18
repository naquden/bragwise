package se.atte.bragwise.util

import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()
