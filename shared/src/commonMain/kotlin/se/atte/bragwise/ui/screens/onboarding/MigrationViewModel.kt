package se.atte.bragwise.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.MigrationMode

class MigrationViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    /**
     * A brand-new account SYNCs guest predictions up to the cloud; signing
     * back into an existing account RESTOREs from the cloud, dropping local
     * guest data (the cloud is authoritative). `null` is treated as existing
     * (RESTORE) so we never push stale local data over a real account.
     */
    private val mode: MigrationMode
        get() = if (auth.lastSignInCreatedNewUser == true) MigrationMode.SYNC else MigrationMode.RESTORE

    sealed interface Phase {
        data object Loading : Phase
        data object Done : Phase
        data class Failed(val message: String) : Phase
    }

    sealed interface Effect {
        data object Complete : Effect
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    init {
        run()
    }

    fun retry() = run()

    private fun run() {
        _phase.value = Phase.Loading
        viewModelScope.launch {
            auth.migrateLocalToCloud(mode)
                .onSuccess {
                    _phase.value = Phase.Done
                    _effects.send(Effect.Complete)
                }
                .onFailure { _phase.value = Phase.Failed(it.message ?: "Unknown error") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _effects.close()
    }
}
