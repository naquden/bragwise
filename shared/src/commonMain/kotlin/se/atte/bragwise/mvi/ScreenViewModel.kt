package se.atte.bragwise.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for one-VM-per-screen MVI. State flows out, intents flow in, effects
 * are one-shot side-channel events (navigation, snackbar, haptics).
 *
 * Channel-based effects: each event is delivered exactly once to a single
 * collector. If the collector is temporarily absent (recomposition), the
 * event is queued and delivered when the collector resumes — unlike
 * SharedFlow(replay=0) which would silently drop it.
 */
abstract class ScreenViewModel<S, I, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)

    protected fun update(block: (S) -> S) {
        _state.update(block)
    }

    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        super.onCleared()
        _effects.close()
    }
}
