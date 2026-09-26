package org.panchang.verify.vaisnava

/**
 * Minimal HTML-to-text reduction.
 *
 * Deliberately not a DOM parser. The community pages are hand-edited CMS output with
 * unbalanced tags, and a strict parser would reject pages a regex reads fine. What
 * matters for correctness here is not structural fidelity but that nothing is silently
 * lost, which the callers enforce by reporting what they could not interpret.
 *
 * If a source ever needs real DOM traversal, add jsoup to the version catalog rather than
 * growing this file.
 */
object HtmlText {

    private val SCRIPT_OR_STYLE = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>")
    private val TAG = Regex("(?s)<[^>]+>")
    private val WHITESPACE = Regex("[ \\t\\u00A0]+")

    /** Strips markup, decodes the entities these pages actually use, normalises spaces. */
    fun toText(html: String): String =
        html.replace(SCRIPT_OR_STYLE, " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6])>"), "\n")
            .replace(TAG, " ")
            .let { decodeEntities(it) }
            .replace(WHITESPACE, " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    fun decodeEntities(s: String): String {
        var out = s
        for ((entity, replacement) in ENTITIES) out = out.replace(entity, replacement)
        // Numeric entities, decimal and hex.
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = Regex("&#[xX]([0-9a-fA-F]+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return out
    }

    private val ENTITIES = listOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&ndash;" to "-",
        "&mdash;" to "-",
        "&rsquo;" to "'",
        "&lsquo;" to "'",
        "&ldquo;" to "\"",
        "&rdquo;" to "\"",
    )
}
