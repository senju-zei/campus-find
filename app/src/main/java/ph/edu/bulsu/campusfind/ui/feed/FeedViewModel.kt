package ph.edu.bulsu.campusfind.ui.feed

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.domain.ItemSearch
import ph.edu.bulsu.campusfind.domain.Settled
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages

sealed interface FeedState {
    data object Loading : FeedState

    /** [filtered] is true when a search or category narrowed the list. */
    data class Loaded(val type: ItemType, val items: List<Item>, val filtered: Boolean) : FeedState
    data class Failed(@param:StringRes val message: Int) : FeedState
}

class FeedViewModel(private val handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository

    /** Shows the Staff dashboard icon for clerks and faculty. The rules still guard every staff action. */
    val isStaff: StateFlow<Boolean> = ServiceLocator.authRepository.currentUser?.uid
        ?.let { uid -> ServiceLocator.userRepository.observeProfile(uid).map { it?.role?.isStaff == true } }
        ?.catch { emit(false) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        ?: MutableStateFlow(false)

    /** The selected tab, kept across rotation and process death. */
    val selectedType: StateFlow<ItemType> = handle.getStateFlow(KEY_TYPE, ItemType.FOUND)
    private val query: StateFlow<String> = handle.getStateFlow(KEY_QUERY, "")

    /** Stored category value, or null for all. */
    val category: StateFlow<String?> = handle.getStateFlow(KEY_CATEGORY, null)

    /** Swipe-down refresh: bumping it re-subscribes to the database. */
    private val refresh = MutableStateFlow(0)

    fun select(type: ItemType) {
        handle[KEY_TYPE] = type
    }

    fun search(text: String) {
        handle[KEY_QUERY] = text
    }

    fun filterCategory(stored: String?) {
        handle[KEY_CATEGORY] = stored
    }

    fun refresh() {
        ServiceLocator.reconnectDatabase()
        refresh.update { it + 1 }
    }

    /** Emits once if the saved sign-in no longer works (account deleted or disabled). */
    private val _sessionEnded = Channel<Unit>(Channel.CONFLATED)
    val sessionEnded: Flow<Unit> = _sessionEnded.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (!ServiceLocator.authRepository.sessionStillValid()) _sessionEnded.send(Unit)
        }
    }

    /** Found and Lost tab counts: items Feed shows, before search and category filters. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tabCounts: StateFlow<Pair<Int, Int>> = refresh.flatMapLatest {
        combine(items.observeByType(ItemType.FOUND), items.observeByType(ItemType.LOST)) { found, lost ->
            val now = System.currentTimeMillis()
            found.count { !Settled.hiddenFromFeed(it, now) } to lost.count { !Settled.hiddenFromFeed(it, now) }
        }
    }
        .catch { emit(0 to 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0 to 0)

    /** Staff only: pending claims, shown as a badge on the Staff dashboard icon. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingClaims: StateFlow<Int> = isStaff.flatMapLatest { staff ->
        if (!staff) {
            flowOf(0)
        } else {
            ServiceLocator.claimRepository.observeAllClaims().map { all -> all.count { it.status == ClaimStatus.PENDING } }
        }
    }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** Live list for the selected tab; updates without a refresh when anyone posts. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FeedState> = combine(selectedType, refresh) { type, _ -> type }
        .flatMapLatest { type ->
            combine(items.observeByType(type), query, category) { list, text, cat ->
                val now = System.currentTimeMillis()
                val visible = list
                    .filterNot { Settled.hiddenFromFeed(it, now) }
                    .filter { cat == null || it.category == cat }
                    .filter { ItemSearch.matches(it, text) }
                FeedState.Loaded(type, visible, filtered = text.isNotBlank() || cat != null) as FeedState
            }
                .onStart { emit(FeedState.Loading) }
                .catch { emit(FeedState.Failed(ErrorMessages.forException(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedState.Loading)

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
