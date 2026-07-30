package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.MigrationSummary

class MockAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(
        AuthState.SignedIn(uid = MOCK_UID, email = MOCK_EMAIL),
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val pendingSignInEmail: StateFlow<String?> = MutableStateFlow(null)

    override fun isSignInLink(link: String): Boolean = false

    override suspend fun continueAsGuest(): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = null, isAnonymous = true)
        return Result.success(Unit)
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = email)
        return Result.success(Unit)
    }

    override suspend fun completeSignInWithLink(link: String): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = MOCK_EMAIL)
        return Result.success(Unit)
    }

    override suspend fun signInWithApple(): Result<Unit> {
        _authState.value = AuthState.SignedIn(uid = MOCK_UID, email = MOCK_EMAIL)
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        _authState.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun migrateLocalToCloud(): Result<MigrationSummary> =
        Result.success(MigrationSummary(migrated = 0, skipped = 0, failed = 0))
}
