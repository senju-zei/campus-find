package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus

/** When a RETURNED or CLOSED item settled, and what that allows (owner decision: hidden from Feed after 3 days). */
object Settled {
    fun isSettled(item: Item): Boolean = item.status == ItemStatus.RETURNED || item.status == ItemStatus.CLOSED

    /** updatedAt is stamped when staff approve or a poster closes; older data falls back to createdAt. */
    fun settledAt(item: Item): Long = item.updatedAt ?: item.createdAt

    fun hiddenFromFeed(item: Item, now: Long): Boolean =
        isSettled(item) && now - settledAt(item) > Constants.SETTLED_HIDE_MS

    /**
     * Owner decision: staff may delete any settled item. Deleting a RETURNED item warns first,
     * because the student loses their pickup code.
     */
    @Suppress("UNUSED_PARAMETER")
    fun deletable(item: Item, now: Long): Boolean = isSettled(item)

    /** Deleting this item takes away an approved student's pickup code. */
    fun deleteNeedsWarning(item: Item): Boolean = item.status == ItemStatus.RETURNED

    /** A reviewed claim record (staff History) can always be deleted. */
    @Suppress("UNUSED_PARAMETER")
    fun recordDeletable(claim: Claim, item: Item?, now: Long): Boolean =
        // Owner decision: staff may delete any reviewed record; approved ones warn about the pickup code.
        claim.status != ClaimStatus.PENDING

    /** A student may remove a finished (approved or rejected) claim from their own Profile. */
    fun claimHideable(claim: Claim): Boolean = claim.status != ClaimStatus.PENDING
}
