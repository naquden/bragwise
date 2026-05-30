package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.MigrationMode
import se.atte.bragwise.data.MigrationSummary

class MockAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(
        AuthState.SignedIn(uid = MOCK_UID, email = MOCK_EMAIL),
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val pendingSignInEmail: StateFlow<String?> = MutableStateFlow(null)

    override val lastSignInCreatedNewUser: Boolean? = true

    override fun isSignInLink(link: String): Boolean = false

    override suspend fun sendSignInLink(email: String): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = email)
        return Result.success(Unit)
    }

    override suspend fun completeSignInWithLink(link: String): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = MOCK_EMAIL)
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        _authState.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun migrateLocalToCloud(mode: MigrationMode): Result<MigrationSummary> =
        Result.success(MigrationSummary(migrated = 0, deferredKeptLocal = 0, droppedLocked = 0))
}
