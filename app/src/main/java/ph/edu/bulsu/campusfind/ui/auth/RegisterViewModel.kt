package ph.edu.bulsu.campusfind.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.data.AuthRepository
import ph.edu.bulsu.campusfind.ui.common.ErrorMessages
import ph.edu.bulsu.campusfind.ui.common.UiMessage

sealed interface RegisterEvent {
    /** Account exists. Go back to Login, which shows the verification notice and [message]. */
    data class GoToLogin(val message: UiMessage) : RegisterEvent
    data class Show(val message: UiMessage) : RegisterEvent
}

class RegisterViewModel(
    private val auth: AuthRepository = ServiceLocator.authRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    /** The fragment validates fields first, including the domain, so no account is made for other domains. */
    fun register(name: String, email: String, password: String) {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val emailSent = auth.register(name, email, password)
                val user = auth.currentUser?.email.orEmpty()
                val message = if (emailSent) {
                    UiMessage(R.string.verify_email_sent, user)
                } else {
                    // Account and profile exist; the email can be resent from Login.
                    UiMessage(R.string.verify_email_not_sent)
                }
                _events.send(RegisterEvent.GoToLogin(message))
            } catch (e: Exception) {
                _events.send(RegisterEvent.Show(UiMessage(ErrorMessages.forException(e))))
            } finally {
                _loading.value = false
            }
        }
    }
}
