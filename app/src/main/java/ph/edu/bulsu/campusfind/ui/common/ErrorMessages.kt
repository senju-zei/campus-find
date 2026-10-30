package ph.edu.bulsu.campusfind.ui.common

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseException
import ph.edu.bulsu.campusfind.R
import java.io.IOException
import java.io.Serializable

/** Maps Firebase and network failures to plain, specific copy (section 7). */
object ErrorMessages {

    @StringRes
    fun forException(error: Throwable): Int = when (error) {
        is FirebaseNetworkException, is IOException -> R.string.error_no_connection
        is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
        // Weak password is a subclass of invalid credentials, so it must come first.
        is FirebaseAuthWeakPasswordException -> R.string.error_password_short
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
        is FirebaseAuthInvalidUserException -> R.string.error_unknown_email
        is FirebaseAuthInvalidCredentialsException -> R.string.error_wrong_password
        is FirebaseAuthException ->
            if (error.errorCode == "ERROR_TOO_MANY_REQUESTS") {
                R.string.error_too_many_requests
            } else {
                R.string.error_generic
            }
        is DatabaseException ->
            if (error.message.orEmpty().contains("permission", ignoreCase = true)) {
                R.string.error_permission_denied
            } else {
                R.string.error_generic
            }
        else -> R.string.error_generic
    }
}

/**
 * A one-time message for a Snackbar, with an optional format argument. Serializable so it can
 * pass between screens through a SavedStateHandle.
 */
data class UiMessage(@param:StringRes val text: Int, val arg: String? = null) : Serializable
