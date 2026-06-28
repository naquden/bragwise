package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

class FirebasePushTokenRemote(
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) : PushTokenRemote {
    override suspend fun registerPushToken(token: String, platform: String) {
        functions.httpsCallable("registerPushToken").invoke(RegisterPushTokenPayload(token = token, platform = platform))
    }
}

@Serializable
private data class RegisterPushTokenPayload(val token: String, val platform: String)
