package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry

/** Looking through the whole dictionary. */
object Browse {
    /** The entries [query] finds in their word, other names or notes; all of them for no query. */
    fun search(entries: List<Entry>, query: String): List<Entry> {
        val wanted = query.trim()
        if (wanted.isEmpty()) return entries
        return entries.filter { entry ->
            entry.terms.any { it.contains(wanted, ignoreCase = true) } || entry.body.contains(wanted, ignoreCase = true)
        }
    }

    /** The start of an entry's notes as plain text, for a list. */
    fun preview(body: String): String =
        Markdown.parse(body).filter { it.text.isNotBlank() }.take(2).joinToString(" ") { it.text.replace('\n', ' ') }
}
