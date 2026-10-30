package ph.edu.bulsu.campusfind.model

import com.google.firebase.database.DataSnapshot

enum class ItemType { LOST, FOUND }

enum class ItemStatus { OPEN, TURNED_IN, CLAIM_PENDING, RETURNED, CLOSED }

enum class ClaimStatus { PENDING, APPROVED, REJECTED }

enum class Role {
    STUDENT, CLERK, FACULTY;

    val isStaff: Boolean get() = this == CLERK || this == FACULTY

    companion object {
        fun from(value: String?): Role = when (value) {
            "clerk" -> CLERK
            "faculty" -> FACULTY
            else -> STUDENT
        }
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role,
    val createdAt: Long,
) {
    companion object {
        fun from(snapshot: DataSnapshot): UserProfile? {
            val uid = snapshot.key ?: return null
            val name = snapshot.child("name").getValue(String::class.java) ?: return null
            return UserProfile(
                uid = uid,
                name = name,
                email = snapshot.child("email").getValue(String::class.java).orEmpty(),
                role = Role.from(snapshot.child("role").getValue(String::class.java)),
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            )
        }
    }
}

data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val category: String?,
    val location: String?,
    val room: String?,
    val publicDescription: String?,
    val status: ItemStatus,
    val postedBy: String,
    val postedByName: String,
    val isbn: String?,
    val bookAuthors: String?,
    val coverUrl: String?,
    /** Color key from Constants.COLORS, or null. Tints the category icon. */
    val color: String?,
    /** Brand or model, e.g. "Casio fx-991ES". */
    val brand: String?,
    /** Null until a clerk marks the item TURNED_IN. Decides where a reject goes. */
    val turnedInAt: Long?,
    val createdAt: Long,
    val updatedAt: Long?,
) {
    companion object {
        fun from(snapshot: DataSnapshot): Item? {
            val id = snapshot.key ?: return null
            fun str(key: String) = snapshot.child(key).getValue(String::class.java)
            fun long(key: String) = snapshot.child(key).getValue(Long::class.java)
            return Item(
                id = id,
                type = enumOrNull<ItemType>(str("type")) ?: return null,
                title = str("title") ?: return null,
                category = str("category"),
                location = str("location"),
                room = str("room"),
                publicDescription = str("publicDescription"),
                status = enumOrNull<ItemStatus>(str("status")) ?: return null,
                postedBy = str("postedBy") ?: return null,
                postedByName = str("postedByName").orEmpty(),
                isbn = str("isbn"),
                bookAuthors = str("bookAuthors"),
                coverUrl = str("coverUrl"),
                color = str("color"),
                brand = str("brand"),
                turnedInAt = long("turnedInAt"),
                createdAt = long("createdAt") ?: 0L,
                updatedAt = long("updatedAt"),
            )
        }
    }
}

/** What the poster enters on Post item. The repository adds ids, names and timestamps. */
data class ItemDraft(
    val type: ItemType,
    val title: String,
    val category: String?,
    val location: String?,
    val room: String?,
    val publicDescription: String?,
    val hiddenDetail: String?,
    val isbn: String?,
    val bookAuthors: String?,
    val coverUrl: String?,
    val color: String? = null,
    val brand: String? = null,
)

data class Claim(
    val itemId: String,
    val claimant: String,
    val claimantName: String,
    val secretDetails: String,
    val whenWhere: String?,
    val contactNote: String?,
    val status: ClaimStatus,
    val rejectReason: String?,
    val reviewedBy: String?,
    /** Private 6-digit code set on approval; shown only to the claimant and staff. */
    val pickupCode: String?,
    val createdAt: Long,
) {
    companion object {
        fun from(itemId: String, snapshot: DataSnapshot): Claim? {
            fun str(key: String) = snapshot.child(key).getValue(String::class.java)
            return Claim(
                itemId = itemId,
                claimant = str("claimant") ?: snapshot.key ?: return null,
                claimantName = str("claimantName").orEmpty(),
                secretDetails = str("secretDetails").orEmpty(),
                whenWhere = str("whenWhere"),
                contactNote = str("contactNote"),
                status = enumOrNull<ClaimStatus>(str("status")) ?: return null,
                rejectReason = str("rejectReason"),
                reviewedBy = str("reviewedBy"),
                pickupCode = str("pickupCode"),
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            )
        }
    }
}

data class ClaimDraft(
    val secretDetails: String,
    val whenWhere: String?,
    val contactNote: String?,
)

/** Result of an Open Library lookup. */
sealed interface BookLookupResult {
    data class Found(val title: String, val authors: String?, val hasCover: Boolean) : BookLookupResult
    data object NotFound : BookLookupResult
}
