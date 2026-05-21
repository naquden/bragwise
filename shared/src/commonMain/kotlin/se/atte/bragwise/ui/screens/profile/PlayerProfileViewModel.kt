package se.atte.bragwise.ui.screens.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class PlayerProfileViewModel(
    private val uid: String,
    private val profiles: ProfileRepository,
    private val social: SocialRepository,
) : ScreenViewModel<PlayerProfileViewModel.State, Nothing, Nothing>(
    initialState = State(ui = UiState.Loading),
) {
    data class Data(
        val profile: PublicProfile,
        val head: HeadToHead.Record?,
    )

    data class State(val ui: UiState<Data>)

    init {
        combine(profiles.observePublicProfile(uid), social.observeHeadToHead()) { p, h ->
            if (p == null) {
                update { it.copy(ui = UiState.Empty()) }
            } else {
                update { it.copy(ui = UiState.Ready(Data(profile = p, head = h.vs[uid]))) }
            }
        }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Nothing) = Unit
}
