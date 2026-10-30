package ph.edu.bulsu.campusfind.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.edu.bulsu.campusfind.core.DbPaths
import ph.edu.bulsu.campusfind.domain.Isbn
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemDraft
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.UserProfile

class ItemRepository(private val root: DatabaseReference) {

    private val items = root.child(DbPaths.ITEMS)

    /** One orderByChild per query: filter by type on the server, sort newest first here. */
    fun observeByType(type: ItemType): Flow<List<Item>> =
        items.orderByChild("type").equalTo(type.name).valueEvents().map { snapshot ->
            snapshot.children.mapNotNull { Item.from(it) }.sortedByDescending { it.createdAt }
        }

    fun observeByStatus(status: ItemStatus): Flow<List<Item>> =
        items.orderByChild("status").equalTo(status.name).valueEvents().map { snapshot ->
            snapshot.children.mapNotNull { Item.from(it) }.sortedBy { it.createdAt }
        }

    /** postedBy has no index in the rules, so the client filters. Fine at pilot size. */
    fun observePostedBy(uid: String): Flow<List<Item>> =
        items.orderByChild("postedBy").equalTo(uid).valueEvents().map { snapshot ->
            snapshot.children.mapNotNull { Item.from(it) }.sortedByDescending { it.createdAt }
        }

    fun observeItem(itemId: String): Flow<Item?> =
        items.child(itemId).valueEvents().map { Item.from(it) }

    /** Readable only by the poster and staff. */
    suspend fun getHiddenDetail(itemId: String): String? =
        root.child(DbPaths.ITEM_SECRETS).child(itemId).child("hiddenDetail")
            .readOnce().getValue(String::class.java)

    /** Writes items/{id} and, for FOUND items, itemSecrets/{id} in one update. Returns the id. */
    suspend fun postItem(draft: ItemDraft, poster: UserProfile): String {
        val itemId = items.push().key ?: error("Could not create an item id")
        val item = buildMap<String, Any> {
            put("type", draft.type.name)
            put("title", draft.title.trim())
            put("status", ItemStatus.OPEN.name)
            put("postedBy", poster.uid)
            put("postedByName", poster.name)
            putIfNotBlank("category", draft.category)
            putIfNotBlank("location", draft.location)
            putIfNotBlank("room", draft.room)
            putIfNotBlank("publicDescription", draft.publicDescription)
            putIfNotBlank("isbn", draft.isbn?.let(Isbn::normalize))
            putIfNotBlank("bookAuthors", draft.bookAuthors)
            putIfNotBlank("coverUrl", draft.coverUrl)
            // turnedInAt is left out until a clerk marks the item turned in.
            put("createdAt", ServerValue.TIMESTAMP)
            put("updatedAt", ServerValue.TIMESTAMP)
        }
        val updates = mutableMapOf<String, Any>("${DbPaths.ITEMS}/$itemId" to item)
        val hidden = draft.hiddenDetail?.trim()
        if (draft.type == ItemType.FOUND && !hidden.isNullOrEmpty()) {
            updates["${DbPaths.ITEM_SECRETS}/$itemId"] = mapOf("hiddenDetail" to hidden)
        }
        root.updateChildren(updates).awaitWrite()
        return itemId
    }

    /** Poster of a LOST item. Only status is written: the rules do not let posters write updatedAt. */
    suspend fun closeLostItem(itemId: String) {
        items.child(itemId).child("status").setValue(ItemStatus.CLOSED.name).awaitWrite()
    }

    /** Clerk or faculty. */
    suspend fun markTurnedIn(itemId: String) {
        val base = "${DbPaths.ITEMS}/$itemId"
        root.updateChildren(
            mapOf(
                "$base/status" to ItemStatus.TURNED_IN.name,
                "$base/turnedInAt" to ServerValue.TIMESTAMP,
                "$base/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).awaitWrite()
    }

    /** Faculty override. */
    suspend fun overrideStatus(itemId: String, status: ItemStatus) {
        val base = "${DbPaths.ITEMS}/$itemId"
        root.updateChildren(
            mapOf(
                "$base/status" to status.name,
                "$base/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).awaitWrite()
    }

    private fun MutableMap<String, Any>.putIfNotBlank(key: String, value: String?) {
        val trimmed = value?.trim()
        if (!trimmed.isNullOrEmpty()) put(key, trimmed)
    }
}
