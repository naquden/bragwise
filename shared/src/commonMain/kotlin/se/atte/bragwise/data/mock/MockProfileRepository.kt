package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.data.NotificationPrefs
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

class MockProfileRepository : ProfileRepository {
    private val _player = MutableStateFlow(mockPlayer)
    private val _notificationPrefs = MutableStateFlow(NotificationPrefs.DEFAULT)

    override fun observeMe(): Flow<Player?> = _player.asStateFlow()

    override fun observeNotificationPrefs(): Flow<NotificationPrefs> = _notificationPrefs.asStateFlow()

    override fun observePublicProfile(uid: String): Flow<PublicProfile?> = flowOf(
        when (uid) {
            MOCK_UID -> mockPublicProfile
            "uid-alice" -> mockCloudFriends[0].player.let {
                PublicProfile(
                    uid = it.uid,
                    username = it.username,
                    displayName = it.displayName,
                    avatarSeed = it.avatarSeed
                )
            }
            "uid-bob" -> mockCloudFriends[1].player.let {
                PublicProfile(
                    uid = it.uid,
                    username = it.username,
                    displayName = it.displayName,
                    avatarSeed = it.avatarSeed
                )
            }
            else -> null
        },
    )

    override suspend fun claimUsername(username: String): Result<Unit> {
        _player.value = _player.value.copy(username = username)
        return Result.success(Unit)
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        avatarSeed: String?,
    ): Result<Unit> {
        _player.value = _player.value.copy(
            displayName = displayName ?: _player.value.displayName,
            username = username ?: _player.value.username,
            avatarSeed = avatarSeed ?: _player.value.avatarSeed,
        )
        return Result.success(Unit)
    }

    override suspend fun setMasterNotification(enabled: Boolean): Result<Unit> {
        _notificationPrefs.value = _notificationPrefs.value.copy(master = enabled)
        return Result.success(Unit)
    }

    override suspend fun setCategoryNotification(key: String, enabled: Boolean): Result<Unit> {
        _notificationPrefs.value = when (key) {
            "social" -> _notificationPrefs.value.copy(social = enabled)
            "results" -> _notificationPrefs.value.copy(results = enabled)
            "participations" -> _notificationPrefs.value.copy(participations = enabled)
            "invites" -> _notificationPrefs.value.copy(invites = enabled)
            else -> _notificationPrefs.value
        }
        return Result.success(Unit)
    }

    override suspend fun recordActivity(): Result<Unit> = Result.success(Unit)
}
