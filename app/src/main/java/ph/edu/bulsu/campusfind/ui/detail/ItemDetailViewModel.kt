package ph.edu.bulsu.campusfind.ui.detail

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.domain.StatusTransitions
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.Role
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.UiMessage

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Shown(
        val item: Item,
        val canClaim: Boolean,
        val canClose: Boolean,
    ) : DetailState
}

class ItemDetailViewModel(handle: SavedStateHandle) : ViewModel() {

    private val items = ServiceLocator.itemRepository
    private val uid = ServiceLocator.authRepository.currentUser?.uid
    private val itemId: String = checkNotNull(handle[ARG_ITEM_ID]) { "itemId is required" }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Live: status changes made by others (a claim, a clerk) show without a refresh. */
    val state: StateFlow<DetailState> = items.observeItem(itemId)
        .map { item ->
            if (item == null) {
                DetailState.Missing
            } else {
                val isPoster = item.postedBy == uid
                DetailState.Shown(
                    item = item,
                    canClaim = StatusTransitions.canClaim(item.status, item.type, isPoster),
                    canClose = StatusTransitions.canTransition(
                        item.status, ItemStatus.CLOSED, item.type, Role.STUDENT, isPoster,
                    ),
                )
            }
        }
        .catch { emit(DetailState.Missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailState.Loading)

    /** Poster of a LOST item who found it themselves. */
    fun closeReport() {
        val shown = state.value as? DetailState.Shown ?: return
        if (_busy.value || shown.item.type != ItemType.LOST) return
        _busy.value = true
        viewModelScope.launch {
            try {
                items.closeLostItem(itemId)
                _messages.send(UiMessage(R.string.msg_report_closed))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        /** Matches the "itemId" argument of itemDetailFragment in nav_graph.xml. */
        const val ARG_ITEM_ID = "itemId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
