package se.atte.bragwise.ui.screens.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.ProfileResolution
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.resolve
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class PlayerProfileViewModel(
    private val uid: String,
    private val profiles: ProfileRepository,
    private val social: SocialRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<PlayerProfileViewModel.State, Nothing, Nothing>(
    initialState = State(ui = UiState.Loading),
) {
    data class Data(
        val profile: PublicProfile,
        val me: Player?,
        val head: HeadToHead.Record?,
    )

    data class State(val ui: UiState<Data>)

    init {
        // Head-to-head is a private self-doc read; isolate its failures so a
        // PERMISSION_DENIED (e.g. rules not yet deployed) degrades to an empty
        // record instead of failing the whole profile with a misleading
        // "no access to challenge" message.
        val headFlow = social.observeHeadToHead()
            .catch { emit(HeadToHead(emptyMap())) }
            .onStart { emit(HeadToHead(emptyMap())) }

        combine(profiles.observePublicProfile(uid), profiles.observeMe(), headFlow) { p, me, h ->
            when (val resolution = p.resolve()) {
                is ProfileResolution.NotFound -> update { it.copy(ui = UiState.Empty()) }
                is ProfileResolution.Loaded -> update {
                    it.copy(ui = UiState.Ready(Data(profile = resolution.profile, me = me, head = h.vs[uid])))
                }
            }
        }
            .catch { e ->
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Nothing) = Unit
}
