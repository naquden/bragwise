package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

interface ProfileRemote {
    fun observePlayer(uid: String): Flow<Player?>
    fun observePublicProfile(uid: String): Flow<PublicProfile?>
    fun observeNotificationPrefs(uid: String): Flow<NotificationPrefs>
    suspend fun setMasterNotification(enabled: Boolean)
    suspend fun setCategoryNotification(key: String, enabled: Boolean)
    suspend fun recordActivity()
    suspend fun claimUsername(username: String)
    suspend fun updateProfile(displayName: String?, username: String?, avatarSeed: String?)
}
