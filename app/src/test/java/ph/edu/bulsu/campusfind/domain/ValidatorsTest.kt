package ph.edu.bulsu.campusfind.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-1, T1-2, T1-4. */
class ValidatorsTest {

    @Test fun `T1-1 allowed email domains`() {
        assertTrue(EmailValidator.isAllowedDomain("a@ms.bulsu.edu.ph"))
        assertTrue(EmailValidator.isAllowedDomain("a@gmail.com"))
        assertTrue(EmailValidator.isAllowedDomain("A@GMAIL.COM"))
        assertTrue(EmailValidator.isAllowedDomain("  a@gmail.com "))
        assertFalse(EmailValidator.isAllowedDomain("a@yahoo.com"))
        assertFalse(EmailValidator.isAllowedDomain("a@gmail.com.evil.com"))
        assertFalse(EmailValidator.isAllowedDomain("a@evilgmail.com"))
        assertFalse(EmailValidator.isAllowedDomain("a@@gmail.com"))
        assertFalse(EmailValidator.isAllowedDomain("@gmail.com"))
        assertFalse(EmailValidator.isAllowedDomain(""))
    }

    @Test fun `T1-2 ISBN-10 and ISBN-13 checksums`() {
        assertTrue(Isbn.isValid("9780134685991"))
        assertTrue(Isbn.isValid("978-0-13-468599-1"))
        assertTrue(Isbn.isValid("0306406152"))
        assertTrue(Isbn.isValid("080442957X"))
        assertTrue(Isbn.isValid("080442957x"))
        assertFalse(Isbn.isValid("9780134685992"))
        assertFalse(Isbn.isValid("0306406153"))
        assertFalse(Isbn.isValid("97801346859"))
        assertFalse(Isbn.isValid("X804429570"))
        assertFalse(Isbn.isValid(""))
        assertEquals("080442957X", Isbn.normalize("0-8044-2957-x"))
    }

    @Test fun `T1-4 claim text length 10 to 500`() {
        assertFalse(TextRules.isValidClaimDetails("a".repeat(9)))
        assertTrue(TextRules.isValidClaimDetails("a".repeat(10)))
        assertTrue(TextRules.isValidClaimDetails("a".repeat(500)))
        assertFalse(TextRules.isValidClaimDetails("a".repeat(501)))
        // Spaces around the text do not count.
        assertFalse(TextRules.isValidClaimDetails("   " + "a".repeat(9) + "   "))
    }

    @Test fun `other length rules mirror the database rules`() {
        assertFalse(TextRules.isValidTitle(" "))
        assertTrue(TextRules.isValidTitle("a".repeat(100)))
        assertFalse(TextRules.isValidTitle("a".repeat(101)))
        assertFalse(TextRules.isValidRejectReason(""))
        assertTrue(TextRules.isValidRejectReason("a".repeat(200)))
        assertFalse(TextRules.isValidRejectReason("a".repeat(201)))
        assertFalse(TextRules.isValidName("a".repeat(61)))
        assertFalse(TextRules.isValidPassword("12345"))
        assertTrue(TextRules.isValidPassword("123456"))
    }

    @Test fun `pickup codes are six digits`() {
        val sixDigits = Regex("^[0-9]{6}$")
        repeat(200) { assertTrue(PickupCode.generate().matches(sixDigits)) }
    }
}
