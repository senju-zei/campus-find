package ph.edu.bulsu.campusfind.data

import android.util.LruCache
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.core.DbPaths
import ph.edu.bulsu.campusfind.domain.Isbn
import ph.edu.bulsu.campusfind.domain.Settled
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

    /** Live hidden detail. Readable only by the poster and staff. */
    fun observeHiddenDetail(itemId: String): Flow<String?> =
        root.child(DbPaths.ITEM_SECRETS).child(itemId).child("hiddenDetail").valueEvents()
            .map { it.getValue(String::class.java) }

    /** Photos never change after posting, so each item's photos download once per app session. */
    private val photoCache = object : LruCache<String, List<String>>(PHOTO_CACHE_CHARS) {
        override fun sizeOf(key: String, value: List<String>) = value.sumOf { it.length }.coerceAtLeast(1)
    }

    /**
     * Item photos (Base64 JPEG). Lost items: anyone signed in. Found items: the poster and staff
     * only; everyone else gets an empty list, because the rules refuse the read.
     */
    suspend fun getPhotos(itemId: String): List<String> {
        photoCache.get(itemId)?.let { return it }
        val photos = try {
            root.child(DbPaths.ITEM_PHOTOS).child(itemId).readOnce().children.mapNotNull { it.getValue(String::class.java) }
        } catch (e: DatabaseException) {
            return emptyList()
        }
        photoCache.put(itemId, photos)
        return photos
    }

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
            putIfNotBlank("color", draft.color)
            putIfNotBlank("brand", draft.brand)
            putIfNotBlank("heldAt", draft.heldAt)
            // turnedInAt is left out until a clerk marks the item turned in.
            put("createdAt", ServerValue.TIMESTAMP)
            put("updatedAt", ServerValue.TIMESTAMP)
        }
        val updates = mutableMapOf<String, Any>("${DbPaths.ITEMS}/$itemId" to item)
        if (draft.photos.isNotEmpty()) {
            updates["${DbPaths.ITEM_PHOTOS}/$itemId"] =
                draft.photos.take(Constants.ITEM_PHOTOS_MAX).withIndex().associate { (i, photo) -> i.toString() to photo }
        }
        val hidden = draft.hiddenDetail?.trim()
        if (draft.type == ItemType.FOUND && !hidden.isNullOrEmpty()) {
            updates["${DbPaths.ITEM_SECRETS}/$itemId"] = mapOf("hiddenDetail" to hidden)
        }
        root.updateChildren(updates).awaitWrite()
        return itemId
    }

    /** Poster of a LOST item. Only status is written: the rules do not let posters write updatedAt. */
    suspend fun closeLostItem(itemId: String) {
        // updatedAt marks when it settled (the rules allow the poster this one stamp).
        val base = "${DbPaths.ITEMS}/$itemId"
        root.updateChildren(
            mapOf("$base/status" to ItemStatus.CLOSED.name, "$base/updatedAt" to ServerValue.TIMESTAMP),
        ).awaitWrite()
    }

    /** Staff. RETURNED and CLOSED items, most recently settled first. */
    fun observeSettled(): Flow<List<Item>> =
        combine(observeByStatus(ItemStatus.RETURNED), observeByStatus(ItemStatus.CLOSED)) { a, b ->
            (a + b).sortedByDescending { Settled.settledAt(it) }
        }

    /** Staff. Removes items, their hidden details and their claims for everyone, in one update. */
    suspend fun deletePermanently(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        val updates = mutableMapOf<String, Any?>()
        itemIds.forEach { id ->
            updates["${DbPaths.ITEMS}/$id"] = null
            updates["${DbPaths.ITEM_SECRETS}/$id"] = null
            updates["${DbPaths.ITEM_PHOTOS}/$id"] = null
            photoCache.remove(id)
            updates["${DbPaths.CLAIMS}/$id"] = null
        }
        root.updateChildren(updates).awaitWrite()
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

    private companion object {
        /** About 4 MB of Base64 text. */
        const val PHOTO_CACHE_CHARS = 4_000_000
    }

    private fun MutableMap<String, Any>.putIfNotBlank(key: String, value: String?) {
        val trimmed = value?.trim()
        if (!trimmed.isNullOrEmpty()) put(key, trimmed)
    }
}
