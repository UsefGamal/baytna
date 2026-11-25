package com.baytna.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {
    @Test fun generatedCodesAreGroupedAndNormalize() {
        val code = InviteCodes.generate()
        assertTrue(code, Regex("^([0-9A-HJKMNP-TV-Z]{4}-){4}[0-9A-HJKMNP-TV-Z]{4}$").matches(code))
        assertEquals(code.replace("-", ""), InviteCodes.normalize(code))
    }

    @Test fun normalizeAcceptsCommonTypingVariations() {
        val canonical = "0123456789ABCDEFGHJK"
        assertEquals(canonical, InviteCodes.normalize("o123-4567 89ab-cdef ghjk"))
        assertEquals(canonical, InviteCodes.normalize("٠١٢٣٤٥٦٧٨٩ABCDEFGHJK"))
        assertEquals("1111222233334444AAAA", InviteCodes.normalize("IiLl-2222-3333-4444-aaaa"))
    }

    @Test fun normalizeRejectsWrongLengthOrCharacters() {
        assertNull(InviteCodes.normalize("ABCD-EFGH"))
        assertNull(InviteCodes.normalize("ABCD-EFGH-JKMN-PQRS-TVWU"))
        assertNull(InviteCodes.normalize(""))
    }

    @Test fun hashIsStableHexAndDiffersPerCode() {
        val h = InviteCodes.hash("0123456789ABCDEFGHJK")
        assertEquals(64, h.length)
        assertEquals(h, InviteCodes.hash("0123456789ABCDEFGHJK"))
        assertNotEquals(h, InviteCodes.hash("0123456789ABCDEFGHJM"))
    }

    @Test fun codesDoNotRepeat() {
        assertEquals(500, List(500) { InviteCodes.generate() }.toSet().size)
    }
}
