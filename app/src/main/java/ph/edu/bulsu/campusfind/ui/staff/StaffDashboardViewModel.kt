package ph.edu.bulsu.campusfind.ui.staff

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
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.UiMessage

enum class StaffTab { CLAIMS, TURN_IN, SETTLED }

/** A pending claim with its item and the finder's hidden detail, shown side by side. */
data class StaffClaimRow(val claim: Claim, val item: Item?, val hiddenDetail: String?)

sealed interface StaffState {
    data object Loading : StaffState
    data class Claims(val rows: List<StaffClaimRow>) : StaffState
    data class TurnIn(val items: List<Item>) : StaffState
    data class Settled(val items: List<Item>) : StaffState
    data class Failed(@param:StringRes val message: Int) : StaffState
}

class StaffDashboardViewModel(private val handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository
    private val claims = ServiceLocator.claimRepository
    private val uid = ServiceLocator.authRepository.currentUser?.uid

    val selectedTab: StateFlow<StaffTab> = handle.getStateFlow(KEY_TAB, StaffTab.CLAIMS)

    fun select(tab: StaffTab) {
        handle[KEY_TAB] = tab
    }

    private val isStaff: Flow<Boolean> =
        if (uid == null) {
            flowOf(false)
        } else {
            ServiceLocator.userRepository.observeProfile(uid).map { it?.role?.isStaff == true }
        }

    /** Live: a new claim appears beside its hidden detail as soon as it is submitted. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StaffState> = combine(isStaff, selectedTab) { staff, tab -> staff to tab }
        .flatMapLatest { (staff, tab) ->
            val source: Flow<StaffState> = when {
                // The rules refuse the reads anyway; this gives a clear message instead.
                !staff -> flowOf(StaffState.Failed(R.string.error_staff_only))
                tab == StaffTab.CLAIMS -> pendingClaimRows().map { StaffState.Claims(it) }
                tab == StaffTab.TURN_IN -> items.observeByStatus(ItemStatus.OPEN)
                    .map { list -> StaffState.TurnIn(list.filter { it.type == ItemType.FOUND }) }
                else -> items.observeSettled().map { StaffState.Settled(it) }
            }
            source
                .onStart { emit(StaffState.Loading) }
                .catch { emit(StaffState.Failed(ErrorMessages.forException(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StaffState.Loading)

    /** Pending claims, oldest first, each joined with its live item and hidden detail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pendingClaimRows(): Flow<List<StaffClaimRow>> =
        claims.observeAllClaims()
            .map { all -> all.filter { it.status == ClaimStatus.PENDING } }
            .flatMapLatest { pending ->
                if (pending.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        pending.map { claim ->
                            combine(
                                items.observeItem(claim.itemId),
                                items.observeHiddenDetail(claim.itemId).catch { emit(null) },
                            ) { item, hidden -> StaffClaimRow(claim, item, hidden) }
                        },
                    ) { it.toList() }
                }
            }

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Deletes the selected settled items, their hidden details and claims, in one update. */
    fun deleteItems(ids: List<String>) {
        viewModelScope.launch {
            try {
                items.deletePermanently(ids)
                _messages.send(UiMessage(R.plurals.msg_deleted_count, count = ids.size))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            }
        }
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
