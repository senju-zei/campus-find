package ph.edu.bulsu.campusfind.ui.feed

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages

sealed interface FeedState {
    data object Loading : FeedState
    data class Loaded(val type: ItemType, val items: List<Item>) : FeedState
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

    fun select(type: ItemType) {
        handle[KEY_TYPE] = type
    }

    /** Live list for the selected tab; updates without a refresh when anyone posts. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FeedState> = selectedType
        .flatMapLatest { type ->
            items.observeByType(type)
                .map<List<Item>, FeedState> { FeedState.Loaded(type, it) }
                .onStart { emit(FeedState.Loading) }
                .catch { emit(FeedState.Failed(ErrorMessages.forException(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedState.Loading)

    private companion object {
        const val KEY_TYPE = "type"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
