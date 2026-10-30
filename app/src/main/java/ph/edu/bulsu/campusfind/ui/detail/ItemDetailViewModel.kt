package ph.edu.bulsu.campusfind.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.domain.StatusTransitions
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.Role
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.UiMessage

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Shown(
        val item: Item,
        /** The signed-in user's own claim on this item, if any. */
        val myClaim: Claim?,
        val canClaim: Boolean,
        val canClose: Boolean,
        val canMarkTurnedIn: Boolean,
        val canOverride: Boolean,
        /** Staff only: the approved claim, to check the pickup code at the CS Office. */
        val approvedForStaff: Claim? = null,
    ) : DetailState
}

class ItemDetailViewModel(handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository
    private val uid = ServiceLocator.authRepository.currentUser?.uid
    private val itemId: String = checkNotNull(handle[NavArgs.ITEM_ID]) { "itemId is required" }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val role: Flow<Role> =
        if (uid == null) {
            flowOf(Role.STUDENT)
        } else {
            ServiceLocator.userRepository.observeProfile(uid).map { it?.role ?: Role.STUDENT }
                .catch { emit(Role.STUDENT) }
        }

    private val myClaim: Flow<Claim?> =
        if (uid == null) {
            flowOf(null)
        } else {
            ServiceLocator.claimRepository.observeMyClaim(itemId, uid).catch { emit(null) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val approvedClaim: Flow<Claim?> = role.flatMapLatest { r ->
        if (!r.isStaff) {
            flowOf(null)
        } else {
            ServiceLocator.claimRepository.observeClaimsForItem(itemId)
                .map { list -> list.firstOrNull { it.status == ClaimStatus.APPROVED } }
                .catch { emit(null) }
        }
    }

    /** Live: claims and staff changes made on other phones show without a refresh. */
    val state: StateFlow<DetailState> =
        combine(items.observeItem(itemId), role, myClaim, approvedClaim) { item, role, claim, approved ->
        if (item == null) {
            DetailState.Missing
        } else {
            val isPoster = item.postedBy == uid
            // A student may claim again only after a rejection.
            val claimAllowed = claim == null || claim.status == ClaimStatus.REJECTED
            DetailState.Shown(
                item = item,
                myClaim = claim,
                canClaim = claimAllowed && StatusTransitions.canClaim(item.status, item.type, isPoster),
                canClose = StatusTransitions.canTransition(
                    item.status, ItemStatus.CLOSED, item.type, Role.STUDENT, isPoster,
                ),
                canMarkTurnedIn = role.isStaff && item.type == ItemType.FOUND && item.status == ItemStatus.OPEN,
                canOverride = role == Role.FACULTY,
                approvedForStaff = approved,
            )
        }
    }
        .catch { emit(DetailState.Missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailState.Loading)

    /** Poster of a LOST item who found it themselves. */
    fun closeReport() = run(R.string.msg_report_closed) { items.closeLostItem(itemId) }

    /** Clerk or faculty: the FOUND item is now at the CS Office. */
    fun markTurnedIn() = run(R.string.msg_marked_turned_in) { items.markTurnedIn(itemId) }

    /** Faculty override to any status. */
    fun overrideStatus(status: ItemStatus) = run(R.string.msg_status_changed) { items.overrideStatus(itemId, status) }

    private fun run(@StringRes done: Int, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                _messages.send(UiMessage(done))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
