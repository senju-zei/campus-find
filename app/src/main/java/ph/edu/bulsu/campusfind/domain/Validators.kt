package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.core.Constants

/** Same domain check as the rules: one '@', allowed domain at the very end, case-insensitive. */
object EmailValidator {
    private val FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalize(email: String): String = email.trim().lowercase()

    fun isWellFormed(email: String): Boolean = FORMAT.matches(normalize(email))

    fun isAllowedDomain(email: String): Boolean {
        val value = normalize(email)
        if (!isWellFormed(value)) return false
        val domain = value.substringAfter('@')
        return domain in Constants.ALLOWED_DOMAINS
    }
}

object Isbn {
    /** Removes hyphens and spaces and upper-cases a trailing x. Stored ISBNs use this form. */
    fun normalize(raw: String): String = raw.replace("-", "").replace(" ", "").trim().uppercase()

    fun isValid(raw: String): Boolean {
        val isbn = normalize(raw)
        return when (isbn.length) {
            10 -> isValidIsbn10(isbn)
            13 -> isValidIsbn13(isbn)
            else -> false
        }
    }

    private fun isValidIsbn10(isbn: String): Boolean {
        if (!isbn.substring(0, 9).all { it.isDigit() }) return false
        val last = isbn[9]
        if (!last.isDigit() && last != 'X') return false
        var sum = 0
        for (i in 0 until 9) sum += (isbn[i] - '0') * (10 - i)
        sum += if (last == 'X') 10 else last - '0'
        return sum % 11 == 0
    }

    private fun isValidIsbn13(isbn: String): Boolean {
        if (!isbn.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 12) sum += (isbn[i] - '0') * (if (i % 2 == 0) 1 else 3)
        val check = (10 - sum % 10) % 10
        return check == isbn[12] - '0'
    }
}

/** Length checks that mirror the rules. Inputs are trimmed before checking. */
object TextRules {
    fun isValidName(name: String): Boolean = name.trim().length in 1..Constants.NAME_MAX

    fun isValidPassword(password: String): Boolean = password.length >= Constants.PASSWORD_MIN

    fun isValidTitle(title: String): Boolean = title.trim().length in 1..Constants.TITLE_MAX

    fun isValidClaimDetails(text: String): Boolean =
        text.trim().length in Constants.CLAIM_DETAILS_MIN..Constants.CLAIM_DETAILS_MAX

    fun isValidHiddenDetail(text: String): Boolean =
        text.trim().length in 1..Constants.HIDDEN_DETAIL_MAX

    fun isValidRejectReason(text: String): Boolean =
        text.trim().length in 1..Constants.REJECT_REASON_MAX

    fun fitsMax(text: String, max: Int): Boolean = text.trim().length <= max
}
