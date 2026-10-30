package ph.edu.bulsu.campusfind.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemStatus.CLAIM_PENDING
import ph.edu.bulsu.campusfind.model.ItemStatus.CLOSED
import ph.edu.bulsu.campusfind.model.ItemStatus.OPEN
import ph.edu.bulsu.campusfind.model.ItemStatus.RETURNED
import ph.edu.bulsu.campusfind.model.ItemStatus.TURNED_IN
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.ItemType.FOUND
import ph.edu.bulsu.campusfind.model.ItemType.LOST
import ph.edu.bulsu.campusfind.model.Role
import ph.edu.bulsu.campusfind.model.Role.CLERK
import ph.edu.bulsu.campusfind.model.Role.FACULTY
import ph.edu.bulsu.campusfind.model.Role.STUDENT

/** T1-6: the table in section 3, plus the owner's multi-claim change. */
class StatusTransitionsTest {
    private fun can(
        from: ItemStatus?,
        to: ItemStatus,
        type: ItemType = FOUND,
        role: Role = STUDENT,
        poster: Boolean = false,
    ) = StatusTransitions.canTransition(from, to, type, role, poster)

    @Test fun `new items start OPEN only`() {
        assertTrue(can(null, OPEN, poster = true))
        assertFalse(can(null, RETURNED, poster = true))
    }

    @Test fun `staff mark found items turned in`() {
        assertTrue(can(OPEN, TURNED_IN, role = CLERK))
        assertFalse(can(OPEN, TURNED_IN, role = STUDENT, poster = true))
        assertFalse(can(OPEN, TURNED_IN, type = LOST, role = CLERK))
    }

    @Test fun `students other than the poster claim found items`() {
        assertTrue(StatusTransitions.canClaim(OPEN, FOUND, isPoster = false))
        assertTrue(StatusTransitions.canClaim(TURNED_IN, FOUND, isPoster = false))
        assertTrue(StatusTransitions.canClaim(CLAIM_PENDING, FOUND, isPoster = false))
        assertFalse(StatusTransitions.canClaim(OPEN, FOUND, isPoster = true))
        assertFalse(StatusTransitions.canClaim(OPEN, LOST, isPoster = false))
        assertFalse(StatusTransitions.canClaim(RETURNED, FOUND, isPoster = false))
    }

    @Test fun `approve and reject are staff only`() {
        assertTrue(can(CLAIM_PENDING, RETURNED, role = CLERK))
        assertFalse(can(CLAIM_PENDING, RETURNED, role = STUDENT, poster = true))
        assertTrue(can(CLAIM_PENDING, OPEN, role = CLERK))
        assertTrue(can(CLAIM_PENDING, TURNED_IN, role = CLERK))
    }

    @Test fun `reject target follows turnedInAt`() {
        assertEquals(TURNED_IN, StatusTransitions.rejectTarget(123L))
        assertEquals(OPEN, StatusTransitions.rejectTarget(null))
    }

    @Test fun `poster closes own open lost report`() {
        assertTrue(can(OPEN, CLOSED, type = LOST, poster = true))
        assertFalse(can(OPEN, CLOSED, type = FOUND, poster = true))
        assertFalse(can(OPEN, CLOSED, type = LOST, poster = false))
        assertFalse(can(CLAIM_PENDING, CLOSED, type = LOST, poster = true))
    }

    @Test fun `faculty override anything`() {
        assertTrue(can(RETURNED, OPEN, role = FACULTY))
        assertTrue(can(CLOSED, CLAIM_PENDING, type = LOST, role = FACULTY))
    }
}
