package ph.edu.bulsu.campusfind.ui.staff

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.domain.PickupCode
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.UiMessage

data class ReviewState(
    val item: Item?,
    val hiddenDetail: String?,
    val claim: Claim?,
    /** Every claim on this item, so approving one can reject the others. */
    val allClaims: List<Claim> = emptyList(),
)

sealed interface ReviewEvent {
    data class Done(val message: UiMessage) : ReviewEvent
    data class Show(val message: UiMessage) : ReviewEvent
}

class ReviewClaimViewModel(handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository
    private val claims = ServiceLocator.claimRepository
    private val reviewer = ServiceLocator.authRepository.currentUser?.uid
    val reviewerUid: String? get() = reviewer
    private val itemId: String = checkNotNull(handle[NavArgs.ITEM_ID]) { "itemId is required" }
    private val claimant: String = checkNotNull(handle[NavArgs.CLAIMANT]) { "claimant is required" }

    val state: StateFlow<ReviewState> = combine(
        items.observeItem(itemId).catch { emit(null) },
        items.observeHiddenDetail(itemId).catch { emit(null) },
        claims.observeClaimsForItem(itemId).catch { emit(emptyList()) },
    ) { item, hidden, all -> ReviewState(item, hidden, all.firstOrNull { it.claimant == claimant }, all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReviewState(null, null, null))

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events: Flow<ReviewEvent> = _events.receiveAsFlow()

    /** Claim APPROVED with a pickup code, other pending claims REJECTED, item RETURNED: one update. */
    fun approve(otherReason: String) = act(R.string.msg_claim_approved) { claim, _, uid ->
        val otherPending = state.value.allClaims.filter { it.status == ClaimStatus.PENDING && it.claimant != claim.claimant }
        claims.approve(claim, uid, PickupCode.generate(), otherPending, otherReason)
    }

    /** Claim REJECTED with the reason; item back to TURNED_IN if it was turned in, else OPEN. */
    fun reject(reason: String) = act(R.string.msg_claim_rejected) { claim, item, uid ->
        val othersPending = state.value.allClaims.any { it.status == ClaimStatus.PENDING && it.claimant != claim.claimant }
        claims.reject(claim, uid, reason, item.turnedInAt, othersPending)
    }

    private fun act(done: Int, action: suspend (Claim, Item, String) -> Unit) {
        val current = state.value
        val claim = current.claim ?: return
        val item = current.item ?: return
        val uid = reviewer ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action(claim, item, uid)
                _events.send(ReviewEvent.Done(UiMessage(done)))
            } catch (e: Exception) {
                _events.send(ReviewEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
