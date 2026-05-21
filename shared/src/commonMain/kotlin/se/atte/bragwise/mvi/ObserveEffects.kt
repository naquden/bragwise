package se.atte.bragwise.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * Collects a one-shot [effects] flow and invokes [onEffect] for each event.
 *
 * `rememberUpdatedState` ensures the lambda always closes over the current
 * parent-composable values (e.g. nav callbacks), even if it was recomposed
 * since the LaunchedEffect last started — preventing stale-capture bugs.
 *
 * The LaunchedEffect is keyed on [effects], which is stable across
 * recompositions (the Channel-backed Flow from ScreenViewModel is created
 * once per ViewModel lifetime), so a new coroutine is only launched when the
 * ViewModel itself is replaced.
 */
@Composable
fun <E> ObserveEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { currentOnEffect(it) }
    }
}
