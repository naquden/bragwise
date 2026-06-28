package se.atte.bragwise.crash

actual fun createCrashReporter(): CrashReporter = NoopCrashReporter
