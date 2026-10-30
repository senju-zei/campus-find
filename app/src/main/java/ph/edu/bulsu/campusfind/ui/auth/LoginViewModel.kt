package ph.edu.bulsu.campusfind.ui.auth

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
import ph.edu.bulsu.campusfind.data.AuthRepository
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.UiMessage

data class LoginUiState(
    val loading: Boolean = false,
    /** Set when the signed-in account has not verified its email yet. */
    val pendingVerificationEmail: String? = null,
)

sealed interface LoginEvent {
    data object GoToFeed : LoginEvent
    data class Show(val message: UiMessage) : LoginEvent
}

class LoginViewModel(
    private val auth: AuthRepository = ServiceLocator.authRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    init {
        syncPendingVerification()
    }

    /** Called on start and after Register, so a signed-in, unverified account shows the notice. */
    fun syncPendingVerification() {
        val user = auth.currentUser
        _state.update {
            it.copy(pendingVerificationEmail = user?.takeIf { u -> !u.isEmailVerified }?.email)
        }
    }

    fun logIn(email: String, password: String) = runOnce {
        auth.logIn(email, password)
        if (auth.refreshVerification()) {
            _events.send(LoginEvent.GoToFeed)
        } else {
            syncPendingVerification()
            _events.send(LoginEvent.Show(UiMessage(R.string.verify_still_unverified)))
        }
    }

    fun resendVerification() = runOnce {
        auth.resendVerification()
        val email = auth.currentUser?.email
        _events.send(LoginEvent.Show(UiMessage(R.string.verify_email_sent, email)))
    }

    /** "I have verified": reload the user and force a fresh token before opening Feed. */
    fun checkVerified() = runOnce {
        if (auth.refreshVerification()) {
            _state.update { it.copy(pendingVerificationEmail = null) }
            _events.send(LoginEvent.GoToFeed)
        } else {
            _events.send(LoginEvent.Show(UiMessage(R.string.verify_still_unverified)))
        }
    }

    /** Ignores taps while a request is running, so a double tap sends one request. */
    private fun runOnce(block: suspend () -> Unit) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _events.send(LoginEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
