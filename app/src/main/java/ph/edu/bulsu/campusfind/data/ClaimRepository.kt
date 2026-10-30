package ph.edu.bulsu.campusfind.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import ph.edu.bulsu.campusfind.core.DbPaths
import ph.edu.bulsu.campusfind.domain.StatusTransitions
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimDraft
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.UserProfile

class ClaimRepository(private val root: DatabaseReference) {

    private val claims = root.child(DbPaths.CLAIMS)

    /**
     * Writes the claim, users/{uid}/myClaims/{itemId} and the item's CLAIM_PENDING status in
     * one update; the rules refuse any of them alone. The whole claim object is replaced, so a
     * resubmission after a reject drops the old rejectReason and reviewedBy.
     * items/{id}/updatedAt is not written: the rules do not let a claimant write it.
     */
    suspend fun submitClaim(itemId: String, claimant: UserProfile, draft: ClaimDraft) {
        val claim = buildMap<String, Any> {
            put("claimant", claimant.uid)
            put("claimantName", claimant.name)
            put("secretDetails", draft.secretDetails.trim())
            draft.whenWhere?.trim()?.takeIf { it.isNotEmpty() }?.let { put("whenWhere", it) }
            draft.contactNote?.trim()?.takeIf { it.isNotEmpty() }?.let { put("contactNote", it) }
            put("status", ClaimStatus.PENDING.name)
            put("createdAt", ServerValue.TIMESTAMP)
        }
        root.updateChildren(
            mapOf(
                "${DbPaths.CLAIMS}/$itemId/${claimant.uid}" to claim,
                "${DbPaths.USERS}/${claimant.uid}/${DbPaths.MY_CLAIMS}/$itemId" to true,
                "${DbPaths.ITEMS}/$itemId/status" to ItemStatus.CLAIM_PENDING.name,
            ),
        ).awaitWrite()
    }

    fun observeMyClaim(itemId: String, uid: String): Flow<Claim?> =
        claims.child(itemId).child(uid).valueEvents().map { snapshot ->
            if (snapshot.exists()) Claim.from(itemId, snapshot) else null
        }

    /** Claims listed in users/{uid}/myClaims, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMyClaims(uid: String): Flow<List<Claim>> =
        root.child(DbPaths.USERS).child(uid).child(DbPaths.MY_CLAIMS).valueEvents()
            .map { snapshot -> snapshot.children.mapNotNull { it.key } }
            .flatMapLatest { itemIds ->
                if (itemIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(itemIds.map { observeMyClaim(it, uid) }) { results ->
                        results.filterNotNull().sortedByDescending { it.createdAt }
                    }
                }
            }

    /** Staff only. Every claim, oldest first. */
    fun observeAllClaims(): Flow<List<Claim>> =
        claims.valueEvents().map { snapshot ->
            snapshot.children.flatMap { itemNode ->
                val itemId = itemNode.key ?: return@flatMap emptyList()
                itemNode.children.mapNotNull { Claim.from(itemId, it) }
            }.sortedBy { it.createdAt }
        }

    /** Staff. Claim APPROVED and item RETURNED in one update. */
    suspend fun approve(claim: Claim, reviewerUid: String) {
        val claimPath = "${DbPaths.CLAIMS}/${claim.itemId}/${claim.claimant}"
        val itemPath = "${DbPaths.ITEMS}/${claim.itemId}"
        root.updateChildren(
            mapOf(
                "$claimPath/status" to ClaimStatus.APPROVED.name,
                "$claimPath/reviewedBy" to reviewerUid,
                "$itemPath/status" to ItemStatus.RETURNED.name,
                "$itemPath/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).awaitWrite()
    }

    /** Staff. Claim REJECTED with a reason; item back to TURNED_IN if it was turned in, else OPEN. */
    suspend fun reject(claim: Claim, reviewerUid: String, reason: String, itemTurnedInAt: Long?) {
        val claimPath = "${DbPaths.CLAIMS}/${claim.itemId}/${claim.claimant}"
        val itemPath = "${DbPaths.ITEMS}/${claim.itemId}"
        root.updateChildren(
            mapOf(
                "$claimPath/status" to ClaimStatus.REJECTED.name,
                "$claimPath/rejectReason" to reason.trim(),
                "$claimPath/reviewedBy" to reviewerUid,
                "$itemPath/status" to StatusTransitions.rejectTarget(itemTurnedInAt).name,
                "$itemPath/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).awaitWrite()
    }
}
