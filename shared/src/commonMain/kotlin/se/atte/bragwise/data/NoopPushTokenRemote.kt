package se.atte.bragwise.data

/** No-op [PushTokenRemote] for mock/test wiring and platforms without push (web). */
class NoopPushTokenRemote : PushTokenRemote {
    override suspend fun registerPushToken(token: String, platform: String) = Unit
}
