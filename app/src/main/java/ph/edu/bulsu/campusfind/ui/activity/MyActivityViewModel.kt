package ph.edu.bulsu.campusfind.ui.activity

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.model.UserProfile
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages

enum class ActivityTab { POSTS, CLAIMS }

/** A claim this staff member reviewed, with its item (null if the item was deleted). */
data class HistoryRow(val claim: Claim, val item: Item?) {
    val key: String get() = "${claim.itemId}/${claim.claimant}"
}

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

    /** Swipe-down refresh: bumping it re-subscribes to the database. */
    private val refresh = MutableStateFlow(0)

    fun refresh() {
        ServiceLocator.reconnectDatabase()
        refresh.update { it + 1 }
    }

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

    /**
     * Students: my FOUND items still OPEN and not left somewhere else, so not yet at the CS Office.
     * Feeds the reminder on Profile.
     */
    val turnInReminder: StateFlow<Int> = (
        uid?.let { id ->
            items.observePostedBy(id).map { posts ->
                posts.count { it.type == ItemType.FOUND && it.status == ItemStatus.OPEN && it.heldAt.isNullOrBlank() }
            }
        } ?: flowOf(0)
        )
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    fun logOut() = ServiceLocator.authRepository.logOut()

    /** True while a profile picture is being saved. */
    private val _photoBusy = MutableStateFlow(false)
    val photoBusy: StateFlow<Boolean> = _photoBusy

    /** Saves the encoded picture (see ProfilePhoto), or removes it when [photo] is null. */
    fun setPhoto(photo: String?) {
        val me = uid ?: return
        if (_photoBusy.value) return
        _photoBusy.value = true
        viewModelScope.launch {
            try {
                ServiceLocator.userRepository.setPhoto(me, photo)
                _messages.send(UiMessage(if (photo == null) R.string.msg_photo_removed else R.string.msg_photo_saved))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            } finally {
                _photoBusy.value = false
            }
        }
    }

    fun photoFailed() {
        viewModelScope.launch { _messages.send(UiMessage(R.string.error_photo_read)) }
    }

    val isStaff: StateFlow<Boolean> = profile.map { it?.role?.isStaff == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Staff only (null for students): claims I approved or rejected, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<HistoryRow>?> = combine(isStaff, refresh) { staff, _ -> staff }.flatMapLatest { staff ->
        if (!staff || uid == null) {
            flowOf(null)
        } else {
            claims.observeAllClaims()
                .map { all -> all.filter { it.reviewedBy == uid && it.status != ClaimStatus.PENDING } }
                .flatMapLatest { mine ->
                    if (mine.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(mine.map { c -> items.observeItem(c.itemId).catch { emit(null) }.map { HistoryRow(c, it) } }) {
                            it.toList().sortedByDescending { row -> row.item?.updatedAt ?: row.claim.createdAt }
                        }
                    }
                }
                .catch { emit(emptyList()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Staff. Deletes the chosen History records for everyone. */
    fun deleteHistory(keys: List<String>) {
        val pairs = keys.mapNotNull { key -> key.split("/").takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        viewModelScope.launch {
            try {
                claims.deleteClaims(pairs)
                _messages.send(UiMessage(R.plurals.msg_deleted_count, count = pairs.size))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            }
        }
    }

    /** Student. Removes the chosen finished claims from their own Profile; staff keep the records. */
    fun hideClaims(itemIds: List<String>) {
        val me = uid ?: return
        viewModelScope.launch {
            try {
                claims.hideFromMyClaims(me, itemIds)
                _messages.send(UiMessage(R.plurals.msg_claims_removed, count = itemIds.size))
            } catch (e: Exception) {
                _messages.send(UiMessage(ErrorMessages.forException(e)))
            }
        }
    }

    val selectedTab: StateFlow<ActivityTab> = handle.getStateFlow(KEY_TAB, ActivityTab.POSTS)

    fun select(tab: ActivityTab) {
        handle[KEY_TAB] = tab
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivityState> = combine(selectedTab, refresh) { tab, _ -> tab }
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
