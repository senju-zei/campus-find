package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus

/** When a RETURNED or CLOSED item settled, and what that allows (owner decision: 3 and 30 days). */
object Settled {
    fun isSettled(item: Item): Boolean = item.status == ItemStatus.RETURNED || item.status == ItemStatus.CLOSED

    /** updatedAt is stamped when staff approve or a poster closes; older data falls back to createdAt. */
    fun settledAt(item: Item): Long = item.updatedAt ?: item.createdAt

    fun hiddenFromFeed(item: Item, now: Long): Boolean =
        isSettled(item) && now - settledAt(item) > Constants.SETTLED_HIDE_MS

    fun deletable(item: Item, now: Long): Boolean =
        isSettled(item) && now - settledAt(item) > Constants.SETTLED_DELETE_MS
}
