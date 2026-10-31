package ph.edu.bulsu.campusfind.data

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import ph.edu.bulsu.campusfind.domain.EmailValidator

class AuthRepository(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
    /** Restarts the database connection so it uses the new sign-in (see ServiceLocator). */
    private val reconnectDatabase: () -> Unit = {},
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * Creates the account, writes users/{uid}, then sends the verification email.
     * If the profile write is refused, the new account is deleted so no orphan is left.
     * Returns false if the account was made but the email could not be sent (for example,
     * rate limited); the user can resend it from Login.
     */
    suspend fun register(name: String, email: String, password: String): Boolean {
        val normalizedEmail = EmailValidator.normalize(email)
        val user = auth.createUserWithEmailAndPassword(normalizedEmail, password).await().user
            ?: error("Sign-up returned no user")
        try {
            users.createProfile(user.uid, name.trim(), normalizedEmail)
        } catch (e: Exception) {
            runCatching { user.delete().await() }
            throw e
        }
        return runCatching { user.sendEmailVerification().await() }.isSuccess
    }

    suspend fun logIn(email: String, password: String): FirebaseUser {
        val user = auth.signInWithEmailAndPassword(EmailValidator.normalize(email), password).await().user
            ?: error("Sign-in returned no user")
        reconnectDatabase()
        return user
    }

    /**
     * Checks a saved sign-in with the server. Returns false only when the account was deleted or
     * disabled (the app then signs out); network problems count as still signed in.
     */
    suspend fun sessionStillValid(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            user.reload().await()
            true
        } catch (e: FirebaseAuthInvalidUserException) {
            logOut()
            false
        } catch (e: FirebaseNetworkException) {
            true
        } catch (e: FirebaseException) {
            // A refresh token the server no longer accepts comes back as a plain FirebaseException.
            if (SESSION_ENDED_CODES.any { e.message.orEmpty().contains(it) }) {
                logOut()
                false
            } else {
                Log.w(TAG, "Could not check the saved sign-in", e)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check the saved sign-in", e)
            true
        }
    }

    suspend fun resendVerification() {
        val user = auth.currentUser ?: error("Not signed in")
        user.sendEmailVerification().await()
    }

    /**
     * Reloads the user and forces a new ID token. The rules read email_verified from the
     * token, which stays stale until it is refreshed. Returns whether the email is verified.
     */
    suspend fun refreshVerification(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        val refreshed = auth.currentUser ?: return false
        refreshed.getIdToken(true).await()
        return refreshed.isEmailVerified
    }

    fun logOut() {
        auth.signOut()
        reconnectDatabase()
    }

    private companion object {
        const val TAG = "AuthRepository"
        val SESSION_ENDED_CODES = listOf("INVALID_REFRESH_TOKEN", "TOKEN_EXPIRED", "USER_NOT_FOUND", "USER_DISABLED")
    }
}
