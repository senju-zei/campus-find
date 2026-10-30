package ph.edu.bulsu.campusfind.ui.activity

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.UserProfile
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages

enum class ActivityTab { POSTS, CLAIMS }

/** A claim with the item it is for (null if the item was removed). */
data class MyClaimRow(val claim: Claim, val item: Item?)

sealed interface ActivityState {
    data object Loading : ActivityState
    data class Posts(val items: List<Item>) : ActivityState
    data class Claims(val rows: List<MyClaimRow>) : ActivityState
    data class Failed(@param:StringRes val message: Int) : ActivityState
}

class MyActivityViewModel(private val handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository
    private val claims = ServiceLocator.claimRepository
    private val uid = ServiceLocator.authRepository.currentUser?.uid

    /** Profile header: name, email, role and counts. */
    val profile: StateFlow<UserProfile?> = (uid?.let { ServiceLocator.userRepository.observeProfile(it) } ?: flowOf(null))
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val counts: StateFlow<Pair<Int, Int>> = (
        uid?.let { id ->
            combine(items.observePostedBy(id), claims.observeMyClaims(id)) { posts, mine -> posts.size to mine.size }
        } ?: flowOf(0 to 0)
        )
        .catch { emit(0 to 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0 to 0)

    fun logOut() = ServiceLocator.authRepository.logOut()

    val selectedTab: StateFlow<ActivityTab> = handle.getStateFlow(KEY_TAB, ActivityTab.POSTS)

    fun select(tab: ActivityTab) {
        handle[KEY_TAB] = tab
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivityState> = selectedTab
        .flatMapLatest { tab ->
            val source: Flow<ActivityState> = when {
                uid == null -> flowOf(ActivityState.Posts(emptyList()))
                tab == ActivityTab.POSTS -> items.observePostedBy(uid).map { ActivityState.Posts(it) }
                else -> myClaimRows(uid).map { ActivityState.Claims(it) }
            }
            source
                .onStart { emit(ActivityState.Loading) }
                .catch { emit(ActivityState.Failed(ErrorMessages.forException(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ActivityState.Loading)

    /** Each claim joined with its live item, newest claim first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun myClaimRows(uid: String): Flow<List<MyClaimRow>> =
        claims.observeMyClaims(uid).flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(list.map { claim -> items.observeItem(claim.itemId).map { MyClaimRow(claim, it) } }) {
                    it.toList()
                }
            }
        }

    private companion object {
        const val KEY_TAB = "tab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
