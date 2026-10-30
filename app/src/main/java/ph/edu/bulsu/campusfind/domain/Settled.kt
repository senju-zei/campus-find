package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus

/** When a RETURNED or CLOSED item settled, and what that allows (owner decision: 3 and 30 days). */
object Settled {
    fun isSettled(item: Item): Boolean = item.status == ItemStatus.RETURNED || item.status == ItemStatus.CLOSED

    /** updatedAt is stamped when staff approve or a poster closes; older data falls back to createdAt. */
    fun settledAt(item: Item): Long = item.updatedAt ?: item.createdAt

    fun hiddenFromFeed(item: Item, now: Long): Boolean =
        isSettled(item) && now - settledAt(item) > Constants.SETTLED_HIDE_MS

    /** Closed reports any time; returned items 30 days after they settle (pickup must be done). */
    fun deletable(item: Item, now: Long): Boolean = when (item.status) {
        ItemStatus.CLOSED -> true
        ItemStatus.RETURNED -> now - settledAt(item) > Constants.SETTLED_DELETE_MS
        else -> false
    }

    /**
     * A reviewed claim record (staff History): approved ones follow [deletable], so the pickup code
     * stays until the item can go; rejected ones once the item has settled. Gone items: any time.
     */
    fun recordDeletable(claim: Claim, item: Item?, now: Long): Boolean = when {
        item == null -> true
        claim.status == ClaimStatus.APPROVED -> deletable(item, now)
        else -> isSettled(item)
    }
}
