package se.atte.bragwise.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.MigrationMode

class MigrationViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    sealed interface Phase {
        data object Choosing : Phase
        data object Loading : Phase
        data object Done : Phase
        data class Failed(val message: String) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Choosing)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    val isNewAccount: Boolean get() = auth.lastSignInCreatedNewUser == true

    /**
     * Reset to [Phase.Choosing] when the dialog re-enters composition. The VM
     * is Activity-scoped (hand-rolled nav has no per-route ViewModelStoreOwner),
     * so a 2nd sign-in reuses the same instance — which would otherwise still be
     * in a terminal [Phase.Done] from the previous run and silently skip the
     * choice. Only resets from a terminal phase so an in-flight migration is
     * never interrupted.
     */
    fun resetIfTerminal() {
        if (_phase.value is Phase.Done || _phase.value is Phase.Failed) {
            _phase.value = Phase.Choosing
        }
    }

    fun onChoose(mode: MigrationMode) {
        if (mode == MigrationMode.SKIP) {
            _phase.value = Phase.Done
            return
        }
        _phase.value = Phase.Loading
        viewModelScope.launch {
            auth.migrateLocalToCloud(mode)
                .onSuccess { _phase.value = Phase.Done }
                .onFailure { _phase.value = Phase.Failed(it.message ?: "Unknown error") }
        }
    }

    fun retry(mode: MigrationMode) {
        _phase.value = Phase.Loading
        viewModelScope.launch {
            auth.migrateLocalToCloud(mode)
                .onSuccess { _phase.value = Phase.Done }
                .onFailure { _phase.value = Phase.Failed(it.message ?: "Unknown error") }
        }
    }
}
