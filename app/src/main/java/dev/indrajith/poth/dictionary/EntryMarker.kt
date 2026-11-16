package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.reader.Mark
import dev.indrajith.poth.reader.Marker

/** Underlines the words a book's dictionary entries match; general (library) words more faintly. */
class EntryMarker(entries: List<Entry>) : Marker {
    private val scopes = entries.associate { it.id to it.scope }
    val matcher = Matcher(entries.flatMap { entry -> entry.terms.map { it to entry.id } })

    override fun marks(text: CharSequence, from: Int, to: Int): List<Mark> = matcher.find(text, from, to).map { match ->
        Mark(match.start, match.end, faint = match.entries.all { scopes[it] == Scope.LIBRARY }, entries = match.entries)
    }
}
