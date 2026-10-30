package ph.edu.bulsu.campusfind.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.domain.Isbn
import ph.edu.bulsu.campusfind.domain.OpenLibraryParser
import ph.edu.bulsu.campusfind.model.BookLookupResult
import java.io.IOException

class BookLookupRepository(private val client: OkHttpClient) {

    /**
     * Callers check [Isbn.isValid] first so an invalid ISBN never reaches the network.
     * Throws IOException when offline or on a server error.
     */
    suspend fun lookUp(rawIsbn: String): BookLookupResult = withContext(Dispatchers.IO) {
        require(Isbn.isValid(rawIsbn)) { "Invalid ISBN" }
        val isbn = Isbn.normalize(rawIsbn)
        val url = Constants.OPEN_LIBRARY_BOOKS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("bibkeys", "ISBN:$isbn")
            .addQueryParameter("format", "json")
            .addQueryParameter("jscmd", "data")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open Library returned ${response.code}")
            OpenLibraryParser.parse(isbn, response.body?.string().orEmpty())
        }
    }
}
