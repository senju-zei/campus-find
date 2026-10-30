package ph.edu.bulsu.campusfind.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import ph.edu.bulsu.campusfind.domain.EmailValidator

class AuthRepository(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
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

    suspend fun logIn(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(EmailValidator.normalize(email), password).await().user
            ?: error("Sign-in returned no user")

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

    fun logOut() = auth.signOut()
}
