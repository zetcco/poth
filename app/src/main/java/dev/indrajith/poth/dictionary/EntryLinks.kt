package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry

/**
 * Writing [[links]] to other entries in an entry's notes: noticing one being typed, offering
 * entries for it, and finishing it.
 */
object EntryLinks {
    /**
     * For each entry, the others among [entries] whose notes link to it, in [entries]' order:
     * links followed backwards. Not one it links to itself, which its own notes already show.
     */
    fun backlinks(entries: List<Entry>): Map<Long, List<Entry>> {
        val matcher = Matcher(entries.flatMap { entry -> entry.terms.map { it to entry.id } })
        val linksTo = entries.associate { entry ->
            entry.id to targets(entry.body).flatMap(matcher::lookup).filter { it != entry.id }.toSet()
        }
        val backlinks = HashMap<Long, MutableList<Entry>>()
        for (from in entries) {
            for (to in linksTo[from.id].orEmpty()) {
                if (from.id !in linksTo[to].orEmpty()) backlinks.getOrPut(to) { mutableListOf() } += from
            }
        }
        return backlinks
    }

    /** The entries [notes] link to, as written. */
    private fun targets(notes: String): List<String> =
        Markdown.parse(notes).flatMap { block -> block.spans.filter { it.style == Markdown.Style.ENTRY }.mapNotNull { it.target } }

    /** A link being typed: "[[" ends at [start], and [query] is what has been typed after it. */
    data class Open(val start: Int, val query: String)

    /** An entry offered for a link. [term] is what gets written: its word, or another name of it. */
    data class Suggestion(val term: String, val headword: String)

    private const val LONGEST = 60

    /**
     * The link being typed at [cursor], if there is one: a "[[" before the cursor on the same
     * line that hasn't been closed. Not after a "|", where the link's shown text goes.
     */
    fun open(text: String, cursor: Int): Open? {
        if (cursor < 2 || cursor > text.length) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val brackets = text.lastIndexOf("[[", cursor - 2)
        if (brackets < lineStart) return null
        val query = text.substring(brackets + 2, cursor)
        if (query.length > LONGEST || query.any { it in "[]|" }) return null
        return Open(brackets + 2, query)
    }

    /**
     * [text] with the link at [open] finished as [term], and the cursor after it. Whatever is
     * already written after the cursor up to the link's end is replaced, so finishing a link
     * that is already closed doesn't close it twice. A "|" and the shown text after it are kept.
     */
    fun complete(text: String, cursor: Int, open: Open, term: String): Pair<String, Int> {
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val rest = text.substring(cursor, lineEnd)
        val close = rest.indexOf("]]")
        val nextOpen = rest.indexOf('[')
        if (close < 0 || nextOpen in 0 until close) {
            val finished = text.substring(0, open.start) + term + "]]" + text.substring(cursor)
            return finished to open.start + term.length + 2
        }
        val pipe = rest.indexOf('|').takeIf { it in 0 until close }
        val keepFrom = cursor + (pipe ?: close)
        val finished = text.substring(0, open.start) + term + text.substring(keepFrom)
        val linkEnd = open.start + term.length + (cursor + close + 2 - keepFrom)
        return finished to linkEnd
    }

    /**
     * Starts a link at the selection from [start] to [end]: the selected words become the
     * link, or with nothing selected, "[[" is typed for a suggestion to finish. Returns the
     * new text and where the cursor goes.
     */
    fun start(text: String, start: Int, end: Int): Pair<String, Int> {
        if (end > start) {
            val words = text.substring(start, end)
            val trimmed = words.trim()
            val lead = words.length - words.trimStart().length
            val linked = words.substring(0, lead) + "[[" + trimmed + "]]" + words.substring(lead + trimmed.length)
            return text.replaceRange(start, end, linked) to start + lead + trimmed.length + 4
        }
        val before = text.getOrNull(start - 1)
        val insert = if (before == null || before.isWhitespace() || before in "([") "[[" else " [["
        return text.replaceRange(start, start, insert) to start + insert.length
    }

    /**
     * The entries to offer for [query], best first: one called [query], then ones starting
     * with it, then ones with a word starting with it. With nothing typed yet, the most recently
     * changed. Each name once, and not [writing], the word of the entry being written.
     */
    fun suggest(entries: List<Entry>, query: String, writing: String, limit: Int = 12): List<Suggestion> {
        val self = writing.trim().lowercase()
        val others = entries.filter { it.headword.lowercase() != self }
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) {
            return others.sortedByDescending { it.updatedAt }
                .distinctBy { it.headword.lowercase() }
                .take(limit)
                .map { Suggestion(it.headword, it.headword) }
        }
        val wantedKey = Words.key(wanted)
        fun rank(term: String): Int? {
            val lower = term.lowercase()
            return when {
                Words.key(lower) == wantedKey -> 0
                lower.startsWith(wanted) -> 1
                Words.tokens(lower).any { lower.substring(it.start).startsWith(wanted) } -> 2
                else -> null
            }
        }
        return others
            .flatMap { entry -> entry.terms.mapNotNull { term -> rank(term)?.let { Triple(it, term, entry) } } }
            .sortedWith(compareBy({ it.first }, { it.second.lowercase() }))
            .distinctBy { it.second.lowercase() }
            .take(limit)
            .map { (_, term, entry) -> Suggestion(term, entry.headword) }
    }
}
