package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.core.Constants
import java.net.URLEncoder
import java.util.Locale

/**
 * DiceBear identicon badge, built at display time and never stored.
 * Colors are passed in from colors.xml so light and dark follow the theme.
 */
object BadgeUrlBuilder {
    fun build(seed: String, rowColor: Int, backgroundColor: Int, sizePx: Int): String {
        val encodedSeed = URLEncoder.encode(seed, "UTF-8")
        val size = sizePx.coerceIn(16, 256)
        return "${Constants.DICEBEAR_IDENTICON_URL}?seed=$encodedSeed" +
            "&rowColor=${toHex(rowColor)}&backgroundColor=${toHex(backgroundColor)}&size=$size"
    }

    /** Plain URL, used if the color parameters turn out to be ignored. */
    fun buildPlain(seed: String, sizePx: Int): String {
        val encodedSeed = URLEncoder.encode(seed, "UTF-8")
        return "${Constants.DICEBEAR_IDENTICON_URL}?seed=$encodedSeed&size=${sizePx.coerceIn(16, 256)}"
    }

    /** RGB hex without '#' or alpha, upper case, e.g. 2E5E4E. */
    fun toHex(color: Int): String = String.format(Locale.ROOT, "%06X", color and 0xFFFFFF)
}

object CoverUrlBuilder {
    /** default=false makes Open Library return 404 when there is no cover, so the badge shows. */
    fun forIsbn(isbn: String): String =
        "${Constants.OPEN_LIBRARY_COVERS_URL}${Isbn.normalize(isbn)}-M.jpg?default=false"
}
