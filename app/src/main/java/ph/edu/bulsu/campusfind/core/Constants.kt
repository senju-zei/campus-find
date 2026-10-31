package ph.edu.bulsu.campusfind.core

/**
 * Values stored in the database and limits shared with the rules in
 * firebase/database.rules.json. Display labels live in strings.xml.
 */
object Constants {
    /** To make sign-up BulSU only, remove gmail.com here and in the rules. */
    val ALLOWED_DOMAINS = listOf("ms.bulsu.edu.ph", "gmail.com")

    /** Stored values. Same order as R.array.locations. */
    val LOCATIONS = listOf(
        "CS Office", "Laboratory", "Classroom", "Library", "Canteen", "Hallway", "Gym", "Other",
    )

    /** Stored values. Same order as R.array.categories. */
    val CATEGORIES = listOf(
        "Book", "Bag", "ID or card", "Electronics", "Clothing", "Keys", "Other",
    )
    const val CATEGORY_BOOK = "Book"

    /** Stored color keys. Same order as R.array.item_colors; must match the rules regex. */
    val COLORS = listOf("black", "white", "gray", "blue", "red", "green", "yellow", "brown", "pink", "other")
    const val COLOR_OTHER = "other"

    const val NAME_MAX = 60
    const val PASSWORD_MIN = 6
    const val TITLE_MAX = 100
    const val SHORT_FIELD_MAX = 60
    const val PUBLIC_DESCRIPTION_MAX = 300
    const val HIDDEN_DETAIL_MAX = 300
    const val BOOK_AUTHORS_MAX = 200
    const val BRAND_MAX = 60
    const val HELD_AT_MAX = 100

    /** Profile picture: square JPEG side in pixels, and the Base64 length limit (same as the rules). */
    const val PHOTO_SIZE_PX = 256
    const val PHOTO_MAX_CHARS = 60_000

    /** Item photos: longest side in pixels, Base64 length limit (same as the rules), photos per item. */
    const val ITEM_PHOTO_SIZE_PX = 640
    const val ITEM_PHOTO_MAX_CHARS = 80_000
    const val ITEM_PHOTOS_MAX = 2

    /** Settled items (RETURNED, CLOSED) leave Feed after this long. */
    const val SETTLED_HIDE_MS = 3L * 24 * 60 * 60 * 1000
    const val CLAIM_DETAILS_MIN = 10
    const val CLAIM_DETAILS_MAX = 500
    const val CLAIM_OPTIONAL_MAX = 200
    const val REJECT_REASON_MAX = 200

    const val OPEN_LIBRARY_BOOKS_URL = "https://openlibrary.org/api/books"
    const val OPEN_LIBRARY_COVERS_URL = "https://covers.openlibrary.org/b/isbn/"
    const val DICEBEAR_IDENTICON_URL = "https://api.dicebear.com/9.x/identicon/png"
    const val USER_AGENT = "CampusFind/0.1 (BulSU College of Science pilot; Android)"
}

/** Database paths. */
object DbPaths {
    const val USERS = "users"
    const val ITEMS = "items"
    const val ITEM_SECRETS = "itemSecrets"
    const val ITEM_PHOTOS = "itemPhotos"
    const val CLAIMS = "claims"
    const val MY_CLAIMS = "myClaims"
}
