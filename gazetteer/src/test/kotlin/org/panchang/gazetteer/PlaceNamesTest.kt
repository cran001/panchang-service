package org.panchang.gazetteer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceNamesTest {

    @Test
    fun `diacritics are stripped`() {
        assertEquals("maharashtra", normalizePlaceName("Mahārāshtra"))
        assertEquals("navadwip", normalizePlaceName("Navadwīp"))
        assertEquals("bengaluru", normalizePlaceName("Bengalūru"))
        assertEquals("sao paulo", normalizePlaceName("São Paulo"))
        assertEquals("zurich", normalizePlaceName("Zürich"))
    }

    @Test
    fun `punctuation and runs of whitespace collapse to single spaces`() {
        assertEquals("new delhi", normalizePlaceName("New   Delhi"))
        assertEquals("port au prince", normalizePlaceName("Port-au-Prince"))
        assertEquals("s hertogenbosch", normalizePlaceName("'s-Hertogenbosch"))
        assertEquals("nadia", normalizePlaceName("  Nadia. "))
    }

    @Test
    fun `case folding does not depend on the default locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            // In a Turkish locale String.lowercase() maps 'I' to dotless 'ı', which would make
            // the index's keys depend on the server's locale setting. This must not.
            assertEquals("indore", normalizePlaceName("INDORE"))
            assertEquals("imphal", normalizePlaceName("Imphal"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    /**
     * Devanagari vowel signs are spacing combining marks (category Mc), for which
     * `isLetterOrDigit` is false. Treating them as punctuation would shatter नवद्वीप into
     * space-separated fragments and the native-script half of the index would quietly stop
     * matching anything a user typed.
     */
    @Test
    fun `indic names stay one token`() {
        val out = normalizePlaceName("नवद्वीप")
        assertTrue(out.isNotEmpty(), "the Devanagari name normalised away entirely")
        assertFalse(out.contains(' '), "the name was split into fragments: \"$out\"")
        assertEquals(out, normalizePlaceName(" नवद्वीप "))
        assertEquals(normalizePlaceName("पुरी"), normalizePlaceName("पुरी "))
        assertFalse(normalizePlaceName("पुरी").contains(' '))
    }

    @Test
    fun `an all-punctuation name normalises to empty and is therefore not indexed`() {
        assertEquals("", normalizePlaceName("---"))
        assertEquals("", normalizePlaceName(""))
        assertEquals("", normalizePlaceName("   "))
    }

    @Test
    fun `normalisation is idempotent`() {
        for (s in listOf("Mahārāshtra", "Port-au-Prince", "São Paulo", "  Nadia. ", "नवद्वीप")) {
            val once = normalizePlaceName(s)
            assertEquals(once, normalizePlaceName(once), "normalising twice changed \"$s\"")
        }
    }
}
