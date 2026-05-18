package se.atte.bragwise.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for one-VM-per-screen MVI. State flows out, intents flow in, effects
 * are one-shot side-channel events (navigation, snackbar, haptics).
 *
 * `replay = 0` on the effects flow so a resubscribing collector does not get
 * stale events — see plan §5 ChallengeDetailViewModel.
 */
abstract class ScreenViewModel<S, I, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = 16)
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    abstract fun onIntent(intent: I)

    protected fun update(block: (S) -> S) {
        _state.update(block)
    }

    /** Always use this — `tryEmit` silently drops on buffer overflow. */
    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
