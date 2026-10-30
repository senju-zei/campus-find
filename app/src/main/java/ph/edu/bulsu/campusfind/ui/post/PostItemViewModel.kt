package ph.edu.bulsu.campusfind.ui.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.domain.CoverUrlBuilder
import ph.edu.bulsu.campusfind.domain.Isbn
import ph.edu.bulsu.campusfind.model.BookLookupResult
import ph.edu.bulsu.campusfind.model.ItemDraft
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import java.io.IOException

data class PostUiState(
    val posting: Boolean = false,
    val lookingUp: Boolean = false,
    /** Set by a successful look-up when Open Library has a cover. */
    val coverUrl: String? = null,
)

sealed interface PostEvent {
    data class BookFound(val title: String, val authors: String?) : PostEvent
    data class Show(val message: UiMessage, val offerRetry: Boolean = false) : PostEvent
    data object Posted : PostEvent
}

class PostItemViewModel(private val handle: SavedStateHandle) : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private val users = ServiceLocator.userRepository
    private val items = ServiceLocator.itemRepository
    private val books = ServiceLocator.bookLookupRepository

    private val _state = MutableStateFlow(PostUiState(coverUrl = handle[KEY_COVER]))
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    private val _events = Channel<PostEvent>(Channel.BUFFERED)
    val events: Flow<PostEvent> = _events.receiveAsFlow()

    /** The fragment checks the ISBN checksum first, so an invalid ISBN never reaches the network. */
    fun lookUp(isbn: String) {
        if (_state.value.lookingUp) return
        _state.update { it.copy(lookingUp = true) }
        handle[KEY_ISBN] = isbn
        viewModelScope.launch {
            try {
                when (val result = books.lookUp(isbn)) {
                    is BookLookupResult.Found -> {
                        setCover(if (result.hasCover) CoverUrlBuilder.forIsbn(isbn) else null)
                        _events.send(PostEvent.BookFound(result.title, result.authors))
                    }
                    BookLookupResult.NotFound -> {
                        setCover(null)
                        _events.send(PostEvent.Show(UiMessage(R.string.book_not_found)))
                    }
                }
            } catch (e: IOException) {
                _events.send(PostEvent.Show(UiMessage(R.string.error_no_connection), offerRetry = true))
            } catch (e: Exception) {
                _events.send(PostEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _state.update { it.copy(lookingUp = false) }
            }
        }
    }

    /**
     * Drops the cover from an earlier look-up once the ISBN no longer matches it. Restoring the
     * same text after rotation keeps the cover.
     */
    fun onIsbnChanged(raw: String) {
        if (Isbn.normalize(raw) != handle.get<String>(KEY_ISBN)) setCover(null)
    }

    fun post(draft: ItemDraft) {
        if (_state.value.posting) return
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: error("Not signed in")
                val poster = users.getProfile(uid)
                if (poster == null) {
                    _events.send(PostEvent.Show(UiMessage(R.string.error_profile_missing)))
                } else {
                    items.postItem(draft.copy(coverUrl = _state.value.coverUrl), poster)
                    _events.send(PostEvent.Posted)
                }
            } catch (e: Exception) {
                _events.send(PostEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _state.update { it.copy(posting = false) }
            }
        }
    }

    private fun setCover(url: String?) {
        handle[KEY_COVER] = url
        _state.update { it.copy(coverUrl = url) }
    }

    private companion object {
        const val KEY_COVER = "coverUrl"
        const val KEY_ISBN = "lookedUpIsbn"
    }
}
