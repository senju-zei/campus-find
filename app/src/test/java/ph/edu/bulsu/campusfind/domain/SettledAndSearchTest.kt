package ph.edu.bulsu.campusfind.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.bulsu.campusfind.TestItems.DAY
import ph.edu.bulsu.campusfind.TestItems.claim
import ph.edu.bulsu.campusfind.TestItems.item
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType

class SettledAndSearchTest {

    @Test fun `settled items leave Feed after 3 days`() {
        val returned = item(status = ItemStatus.RETURNED, createdAt = 0, updatedAt = 10 * DAY)
        assertFalse(Settled.hiddenFromFeed(returned, 12 * DAY))
        assertTrue(Settled.hiddenFromFeed(returned, 14 * DAY))
        assertFalse(Settled.hiddenFromFeed(item(status = ItemStatus.OPEN), 100 * DAY))
    }

    @Test fun `staff may delete any settled item, returned ones with a warning`() {
        val returned = item(status = ItemStatus.RETURNED, updatedAt = 0)
        assertTrue(Settled.deletable(returned, now = 1))
        assertTrue(Settled.deleteNeedsWarning(returned))
        val closed = item(type = ItemType.LOST, status = ItemStatus.CLOSED)
        assertTrue(Settled.deletable(closed, now = 1))
        assertFalse(Settled.deleteNeedsWarning(closed))
        assertFalse(Settled.deletable(item(status = ItemStatus.CLAIM_PENDING), now = 1))
    }

    @Test fun `students remove only finished claims`() {
        assertTrue(Settled.claimHideable(claim(ClaimStatus.APPROVED)))
        assertTrue(Settled.claimHideable(claim(ClaimStatus.REJECTED)))
        assertFalse(Settled.claimHideable(claim(ClaimStatus.PENDING)))
    }

    @Test fun `search needs every word`() {
        val calc = item(title = "Scientific calculator", brand = "Casio fx-991ES", color = "blue", category = "Electronics")
        assertTrue(ItemSearch.matches(calc, ""))
        assertTrue(ItemSearch.matches(calc, "casio BLUE"))
        assertFalse(ItemSearch.matches(calc, "casio red"))
    }

    @Test fun `might be yours ranks same color then same place`() {
        val lost = item(id = "lost", type = ItemType.LOST, category = "Bag", color = "red", location = "Library", createdAt = 20 * DAY)
        val sameColor = item(id = "a", category = "Bag", color = "red", location = "Gym", createdAt = 19 * DAY)
        val samePlace = item(id = "b", category = "Bag", color = "blue", location = "Library", createdAt = 21 * DAY)
        val otherCategory = item(id = "c", category = "Book", color = "red", createdAt = 20 * DAY)
        val tooOld = item(id = "d", category = "Bag", color = "red", createdAt = 1 * DAY)
        val returned = item(id = "e", category = "Bag", color = "red", status = ItemStatus.RETURNED, createdAt = 20 * DAY)
        val result = ItemSearch.suggestionsFor(lost, listOf(samePlace, otherCategory, sameColor, tooOld, returned))
        assertEquals(listOf("a", "b"), result.map { it.id })
    }
}
