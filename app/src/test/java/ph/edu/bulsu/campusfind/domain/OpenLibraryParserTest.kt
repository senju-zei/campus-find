package ph.edu.bulsu.campusfind.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.edu.bulsu.campusfind.model.BookLookupResult

/** T1-3 and T1-5. */
class OpenLibraryParserTest {
    private val isbn = "9780134685991"

    @Test fun `T1-3 book found with cover and authors`() {
        val body = """{"ISBN:$isbn":{"title":"Effective Java","authors":[{"name":"Joshua Bloch"}],"cover":{"medium":"x"}}}"""
        assertEquals(BookLookupResult.Found("Effective Java", "Joshua Bloch", hasCover = true), OpenLibraryParser.parse(isbn, body))
    }

    @Test fun `T1-3 empty result`() {
        assertEquals(BookLookupResult.NotFound, OpenLibraryParser.parse(isbn, "{}"))
    }

    @Test fun `T1-3 missing cover and missing authors`() {
        val body = """{"ISBN:$isbn":{"title":"Effective Java"}}"""
        assertEquals(BookLookupResult.Found("Effective Java", null, hasCover = false), OpenLibraryParser.parse(isbn, body))
    }

    @Test fun `T1-3 several authors are joined and blanks dropped`() {
        val body = """{"ISBN:$isbn":{"title":"T","authors":[{"name":"A"},{"name":" "},{"name":"B"}]}}"""
        assertEquals(BookLookupResult.Found("T", "A, B", hasCover = false), OpenLibraryParser.parse(isbn, body))
    }

    @Test fun `T1-5 badge URL uses the seed and hex colors without hash`() {
        val url = BadgeUrlBuilder.build("item 1", 0xFF2E5E4E.toInt(), 0xFFE4EDE8.toInt(), 96)
        assertEquals(
            "https://api.dicebear.com/9.x/identicon/png?seed=item+1&rowColor=2E5E4E&backgroundColor=E4EDE8&size=96",
            url,
        )
        assertEquals("7FB59E", BadgeUrlBuilder.toHex(0xFF7FB59E.toInt()))
        assertEquals(
            "https://covers.openlibrary.org/b/isbn/080442957X-M.jpg?default=false",
            CoverUrlBuilder.forIsbn("0-8044-2957-x"),
        )
    }
}
