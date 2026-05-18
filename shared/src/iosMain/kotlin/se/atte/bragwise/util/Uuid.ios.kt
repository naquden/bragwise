package se.atte.bragwise.util

import platform.Foundation.NSUUID

actual fun randomUuid(): String = NSUUID().UUIDString
