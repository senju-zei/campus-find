package ph.edu.bulsu.campusfind.ui.claim

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.model.ClaimDraft
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.UiMessage

sealed interface ClaimEvent {
    data object Submitted : ClaimEvent
    data class Show(val message: UiMessage) : ClaimEvent
}

class ClaimViewModel(handle: SavedStateHandle) : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private val users = ServiceLocator.userRepository
    private val claims = ServiceLocator.claimRepository
    private val itemId: String = checkNotNull(handle[NavArgs.ITEM_ID]) { "itemId is required" }

    val item: StateFlow<Item?> = ServiceLocator.itemRepository.observeItem(itemId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _events = Channel<ClaimEvent>(Channel.BUFFERED)
    val events: Flow<ClaimEvent> = _events.receiveAsFlow()

    /**
     * Writes the claim, myClaims and CLAIM_PENDING in one update. If someone else claimed
     * first, the rules refuse it and the user sees "This action is not allowed."
     */
    fun submit(draft: ClaimDraft) {
        if (_submitting.value) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: error("Not signed in")
                val profile = users.getProfile(uid)
                if (profile == null) {
                    _events.send(ClaimEvent.Show(UiMessage(R.string.error_profile_missing)))
                } else {
                    claims.submitClaim(itemId, profile, draft)
                    _events.send(ClaimEvent.Submitted)
                }
            } catch (e: Exception) {
                _events.send(ClaimEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _submitting.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
