package ph.edu.bulsu.campusfind

import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType

/** Small builders so tests only name the fields they care about. */
object TestItems {
    const val DAY = 24L * 60 * 60 * 1000

    fun item(
        id: String = "i1",
        type: ItemType = ItemType.FOUND,
        title: String = "Calculus textbook",
        category: String? = "Book",
        location: String? = "Laboratory",
        status: ItemStatus = ItemStatus.OPEN,
        color: String? = null,
        brand: String? = null,
        createdAt: Long = 0L,
        updatedAt: Long? = null,
    ) = Item(
        id = id, type = type, title = title, category = category, location = location, room = null,
        publicDescription = null, status = status, postedBy = "poster", postedByName = "Ana Cruz",
        isbn = null, bookAuthors = null, coverUrl = null, color = color, brand = brand, heldAt = null,
        turnedInAt = null, createdAt = createdAt, updatedAt = updatedAt,
    )

    fun claim(status: ClaimStatus, itemId: String = "i1") = Claim(
        itemId = itemId, claimant = "ben", claimantName = "Ben Reyes", secretDetails = "Name inside the cover",
        whenWhere = null, contactNote = null, status = status, rejectReason = null, reviewedBy = null,
        pickupCode = null, createdAt = 0L,
    )
}
