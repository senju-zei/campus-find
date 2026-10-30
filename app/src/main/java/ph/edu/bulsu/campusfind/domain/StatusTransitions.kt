package ph.edu.bulsu.campusfind.domain

import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.Role

/**
 * The status lifecycle from section 3. The rules enforce it on the server; the app uses
 * this to decide which actions to show.
 */
object StatusTransitions {

    /** Faculty may override to any status. Clerks are not offered overrides in the app. */
    fun canTransition(
        from: ItemStatus?,
        to: ItemStatus,
        type: ItemType,
        role: Role,
        isPoster: Boolean,
    ): Boolean {
        if (role == Role.FACULTY) return true
        if (from == null) return to == ItemStatus.OPEN && isPoster
        return when (to) {
            // Mark turned in, or a reject of a claim on an item that was turned in.
            ItemStatus.TURNED_IN ->
                role.isStaff && type == ItemType.FOUND &&
                    (from == ItemStatus.OPEN || from == ItemStatus.CLAIM_PENDING)
            ItemStatus.CLAIM_PENDING ->
                !isPoster && type == ItemType.FOUND &&
                    (from == ItemStatus.OPEN || from == ItemStatus.TURNED_IN)
            ItemStatus.RETURNED ->
                role.isStaff && from == ItemStatus.CLAIM_PENDING
            // A reject of a claim on an item that was never turned in.
            ItemStatus.OPEN ->
                role.isStaff && from == ItemStatus.CLAIM_PENDING
            ItemStatus.CLOSED ->
                isPoster && type == ItemType.LOST && from == ItemStatus.OPEN
        }
    }

    /** Where a rejected claim sends the item. */
    fun rejectTarget(turnedInAt: Long?): ItemStatus =
        if (turnedInAt != null) ItemStatus.TURNED_IN else ItemStatus.OPEN

    fun canClaim(status: ItemStatus, type: ItemType, isPoster: Boolean): Boolean =
        canTransition(status, ItemStatus.CLAIM_PENDING, type, Role.STUDENT, isPoster)
}
