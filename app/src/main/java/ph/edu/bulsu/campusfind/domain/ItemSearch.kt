package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType

/** Feed search and "Might be yours" matching. Both run on the phone; nothing extra is stored. */
object ItemSearch {

    /** Every word of [query] must appear in the title, brand, color, category, place or description. */
    fun matches(item: Item, query: String): Boolean {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val text = listOfNotNull(
            item.title, item.brand, item.color, item.category, item.location, item.room,
            item.publicDescription, item.bookAuthors, item.isbn,
        ).joinToString(" ").lowercase()
        return words.all { it in text }
    }

    private const val MATCH_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
    private const val MATCH_LIMIT = 5

    /**
     * Found items that might be the [lost] one: same category, still claimable, posted within 14 days
     * of it. Same color first, then same location, then newest.
     */
    fun suggestionsFor(lost: Item, found: List<Item>): List<Item> {
        if (lost.type != ItemType.LOST || lost.category == null) return emptyList()
        return found
            .asSequence()
            .filter { it.type == ItemType.FOUND && it.category == lost.category }
            .filter { it.status == ItemStatus.OPEN || it.status == ItemStatus.TURNED_IN || it.status == ItemStatus.CLAIM_PENDING }
            .filter { kotlin.math.abs(it.createdAt - lost.createdAt) <= MATCH_WINDOW_MS }
            .sortedWith(
                compareByDescending<Item> { lost.color != null && it.color == lost.color }
                    .thenByDescending { lost.location != null && it.location == lost.location }
                    .thenByDescending { it.createdAt },
            )
            .take(MATCH_LIMIT)
            .toList()
    }
}
