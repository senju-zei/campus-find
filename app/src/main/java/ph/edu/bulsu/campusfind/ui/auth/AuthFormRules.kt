package ph.edu.bulsu.campusfind.ui.auth

import androidx.annotation.StringRes
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.domain.EmailValidator
import ph.edu.bulsu.campusfind.domain.TextRules

/** Field checks for Login and Register. Each returns the error to show, or null if valid. */
object AuthFormRules {

    @StringRes
    fun loginEmail(email: String): Int? = when {
        email.isBlank() -> R.string.error_required
        !EmailValidator.isWellFormed(email) -> R.string.error_email_format
        else -> null
    }

    @StringRes
    fun loginPassword(password: String): Int? =
        if (password.isEmpty()) R.string.error_required else null

    @StringRes
    fun name(name: String): Int? = when {
        name.isBlank() -> R.string.error_required
        !TextRules.isValidName(name) -> R.string.error_name_length
        else -> null
    }

    /** Blocks other domains before any account is created. */
    @StringRes
    fun registerEmail(email: String): Int? = when {
        email.isBlank() -> R.string.error_required
        !EmailValidator.isWellFormed(email) -> R.string.error_email_format
        !EmailValidator.isAllowedDomain(email) -> R.string.error_email_domain
        else -> null
    }

    @StringRes
    fun registerPassword(password: String): Int? = when {
        password.isEmpty() -> R.string.error_required
        !TextRules.isValidPassword(password) -> R.string.error_password_short
        else -> null
    }
}
