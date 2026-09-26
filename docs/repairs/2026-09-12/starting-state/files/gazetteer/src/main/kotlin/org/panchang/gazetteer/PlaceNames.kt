package org.panchang.gazetteer

import java.text.Normalizer


/**
 * The single normalisation applied to every name, both when the index is built and when a
 * query is looked up. Index and query must go through *this* function and no other — a name
 * index whose two sides normalise differently fails silently, returning nothing for a name
 * that is plainly present.
 *
 * The steps, in order:
 *  1. NFD decomposition, then removal of every combining mark. `Mahārāshtra` and
 *     `Maharashtra` become the same key. Users type ASCII; GeoNames publishes diacritics.
 *  2. Locale-independent per-character lowercasing ([Char.lowercaseChar]). Deliberately not
 *     `String.lowercase()` with a default locale: in a Turkish default locale that maps `I`
 *     to `ı`, which would make the index's contents depend on the server's locale setting.
 *  3. Every run of characters that is neither a letter nor a digit collapses to one space.
 *     This folds `Thiruvananthapuram`, `Thiruvananthapuram (Trivandrum)` punctuation,
 *     hyphens and multiple spaces onto one shape.
 *  4. Trim.
 *
 * Non-Latin scripts pass through steps 1–2 unchanged and remain matchable in their own
 * script, which is why the index carries both the native `name` and the `asciiName`.
 */
fun normalizePlaceName(raw: String): String {
    val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    var pendingSpace = false
    for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
        // Spacing combining marks are kept. In Devanagari and its neighbours the vowel signs
        // (the matras of नवद्वीप) are category Mc, and `isLetterOrDigit` is false for them, so
        // without this they would be treated as punctuation and blow a name apart into
        // fragments separated by spaces. Only the *non-spacing* marks — Latin accents, the
        // virama — are the "diacritics" this function strips.
        if (ch.isLetterOrDigit() || Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt()) {
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(ch.lowercaseChar())
        } else {
            pendingSpace = true
        }
    }
    return out.toString()
}
