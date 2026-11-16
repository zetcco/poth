package dev.indrajith.poth.epub

/** One CSS rule with a single selector, as Jsoup's select() understands it. */
data class CssRule(val selector: String, val declarations: Map<String, String>, val specificity: Int)

/**
 * Just enough CSS for book stylesheets: rules and their declarations. Matching is left to
 * Jsoup's selector engine; a selector it cannot handle (a pseudo-element, say) is skipped there.
 */
object Css {
    private val comments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val tagNames = Regex("(^|[\\s>+~])([a-zA-Z][\\w-]*)")

    /** The style rules in [css], one per selector. At-rules (@page, @font-face, @media…) are skipped. */
    fun parse(css: String): List<CssRule> {
        val text = css.replace(comments, "")
        val rules = mutableListOf<CssRule>()
        var i = 0
        while (i < text.length) {
            val open = text.indexOf('{', i)
            if (open < 0) break
            val close = closingBrace(text, open)
            // Statements like @charset "utf-8"; or @import …; end with a semicolon before the rule.
            val prelude = text.substring(i, open).substringAfterLast(';').trim()
            if (!prelude.startsWith("@")) {
                val declarations = declarations(text.substring(open + 1, close))
                if (declarations.isNotEmpty()) {
                    for (selector in prelude.split(',').map(String::trim).filter(String::isNotEmpty)) {
                        rules += CssRule(selector, declarations, specificity(selector))
                    }
                }
            }
            i = close + 1
        }
        return rules
    }

    /** "font-style: italic; margin: 0 !important" as property -> value, both lowercase (no value poth
     * reads is case-sensitive). */
    fun declarations(block: String): Map<String, String> = block.split(';').mapNotNull { declaration ->
        val colon = declaration.indexOf(':')
        if (colon <= 0) return@mapNotNull null
        val property = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1).replace("!important", "", ignoreCase = true).trim().lowercase()
        if (property.isEmpty() || value.isEmpty()) null else property to value
    }.toMap()

    /** Ids, then classes (and attributes, pseudo-classes), then tag names: the usual CSS order. */
    internal fun specificity(selector: String): Int {
        val ids = selector.count { it == '#' }
        val classes = selector.count { it == '.' || it == '[' || it == ':' }
        val tags = tagNames.findAll(selector).count()
        return ids * 10_000 + classes * 100 + tags
    }

    private fun closingBrace(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return text.length
    }
}
