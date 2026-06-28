package se.atte.bragwise.data

interface PushTokenRemote {
    suspend fun registerPushToken(token: String, platform: String)
}
