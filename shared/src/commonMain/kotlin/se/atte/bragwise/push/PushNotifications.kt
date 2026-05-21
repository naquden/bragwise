package se.atte.bragwise.push

import kotlinx.coroutines.flow.SharedFlow

enum class PushPlatform { FCM, APNS }

/**
 * KMP shim for platform push token + permission. Per-platform actuals
 * forward token rotations through [tokens] so the registration callable
 * is invoked from common code (see [PushTokenRegistrar]).
 */
expect class PushNotifications {
    suspend fun requestPermission(): Boolean
    val tokens: SharedFlow<PushToken>
    val incomingDeepLinks: SharedFlow<String>
}

data class PushToken(val value: String, val platform: PushPlatform)
