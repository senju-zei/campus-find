package ph.edu.bulsu.campusfind.domain

import org.json.JSONObject
import ph.edu.bulsu.campusfind.model.BookLookupResult

/**
 * Parses https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data.
 * An unknown ISBN returns `{}`.
 */
object OpenLibraryParser {
    fun parse(isbn: String, body: String): BookLookupResult {
        val root = JSONObject(body)
        val book = root.optJSONObject("ISBN:$isbn") ?: return BookLookupResult.NotFound
        val title = book.optString("title").trim()
        if (title.isEmpty()) return BookLookupResult.NotFound

        val authors = book.optJSONArray("authors")?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.optString("name")?.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
        }?.takeIf { it.isNotEmpty() }

        val hasCover = book.optJSONObject("cover")?.length()?.let { it > 0 } ?: false
        return BookLookupResult.Found(title = title, authors = authors, hasCover = hasCover)
    }
}
