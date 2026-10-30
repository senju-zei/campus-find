package ph.edu.bulsu.campusfind.domain

import java.security.SecureRandom

/** Random 6-digit pickup code shown to the approved claimant and checked by the CS Office. */
object PickupCode {
    private val random = SecureRandom()

    fun generate(): String = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000))
}
