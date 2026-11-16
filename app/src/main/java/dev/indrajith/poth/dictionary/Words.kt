package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Scope
import java.util.Locale

/** A word in some text: [start] until [end], and the [key] it is matched by. */
data class Token(val start: Int, val end: Int, val key: String)

/** Finding words in text the way the dictionary matches them. */
object Words {
    private val apostrophes = setOf('\'', '’')

    /**
     * The words in [text] from [from] until [to]. An apostrophe between letters belongs to the
     * word (Kholin’s, don’t); hyphens and dashes separate words.
     */
    fun tokens(text: CharSequence, from: Int = 0, to: Int = text.length): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = from
        while (i < to) {
            if (!text[i].isLetterOrDigit()) {
                i++
                continue
            }
            val start = i
            while (i < to && (text[i].isLetterOrDigit() || text[i] in apostrophes && i + 1 < to && text[i + 1].isLetter())) i++
            tokens += Token(start, i, key(text.subSequence(start, i).toString()))
        }
        return tokens
    }

    /** How a word is matched: lowercase, without a possessive ending (Elhokar’s, knights’). */
    fun key(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("'s") || lower.endsWith("’s") -> lower.dropLast(2)
            else -> lower.trimEnd('\'', '’')
        }
    }

    /** The word at [offset], or just before it (a tap at a word's end), or null. */
    fun at(text: CharSequence, offset: Int): Token? {
        var i = offset.coerceIn(0, text.length)
        if ((i == text.length || !text[i].isLetterOrDigit()) && i > 0 && text[i - 1].isLetterOrDigit()) i--
        if (i >= text.length || !text[i].isLetterOrDigit()) return null
        var start = i
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] in apostrophes && start >= 2 && text[start - 2].isLetter())) start--
        return tokens(text, start, text.length).firstOrNull()
    }

    /** Whether the word at [start] begins a sentence: nothing but quotes and space since . ! ? or the start. */
    fun startsSentence(text: CharSequence, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] in "\"“‘'([—⁠")) i--
        return i < 0 || text[i] in ".!?…"
    }

    /**
     * Where a new entry for [word] probably belongs. A lowercase word is a general word: the
     * library. A capitalised one is a name, for the shelf (or the book, if it is on none), unless
     * it is only capitalised for starting a sentence and appears in lowercase elsewhere.
     */
    fun suggestScope(word: String, startsSentence: Boolean, appearsInLowercase: Boolean, onShelf: Boolean): Scope {
        val capitalised = word.firstOrNull()?.isUpperCase() == true
        if (!capitalised || startsSentence && appearsInLowercase) return Scope.LIBRARY
        return if (onShelf) Scope.SHELF else Scope.BOOK
    }

    /** The headword for a new entry: without a possessive, and lowercase for a general word. */
    fun headword(word: String, scope: Scope): String {
        val bare = when {
            word.endsWith("'s") || word.endsWith("’s") -> word.dropLast(2)
            else -> word.trimEnd('\'', '’')
        }
        return if (scope == Scope.LIBRARY) bare.lowercase(Locale.ROOT) else bare
    }

    /**
     * A shelf name for a book that has none: its series, or the series in parentheses in its
     * title ("Words of Radiance (Stormlight Archive, The)" -> "The Stormlight Archive").
     */
    fun suggestShelf(title: String, series: String?): String {
        if (!series.isNullOrBlank()) return series.trim()
        val inParentheses = Regex("\\(([^)]+)\\)\\s*$").find(title)?.groupValues?.get(1)?.trim() ?: return ""
        val article = Regex("^(.*),\\s*(The|A|An)$").find(inParentheses)
        return if (article != null) "${article.groupValues[2]} ${article.groupValues[1]}" else inParentheses
    }
}

/** A dictionary term found in text: [start] until [end], standing for [entries]. */
data class Match(val start: Int, val end: Int, val entries: List<Long>)

/**
 * Finds dictionary terms in text. A term may be several words ("Jasnah Kholin"); it matches
 * whole words, ignoring case and possessives, and the longest term at a place wins.
 */
class Matcher(terms: List<Pair<String, Long>>) {
    private class Term(val keys: List<String>, val entries: List<Long>)

    private val byFirstKey: Map<String, List<Term>> = terms
        .mapNotNull { (text, entry) -> Words.tokens(text).map { it.key }.takeIf { it.isNotEmpty() }?.let { it to entry } }
        .groupBy({ it.first }, { it.second })
        .map { (keys, entries) -> Term(keys, entries.distinct()) }
        .groupBy { it.keys.first() }
        .mapValues { (_, terms) -> terms.sortedByDescending { it.keys.size } }

    val isEmpty get() = byFirstKey.isEmpty()

    fun find(text: CharSequence, from: Int = 0, to: Int = text.length): List<Match> {
        if (isEmpty) return emptyList()
        val tokens = Words.tokens(text, from, to)
        val matches = mutableListOf<Match>()
        var i = 0
        while (i < tokens.size) {
            val term = byFirstKey[tokens[i].key]?.firstOrNull { matchesAt(it, tokens, i, text) }
            if (term == null) {
                i++
                continue
            }
            matches += Match(tokens[i].start, tokens[i + term.keys.size - 1].end, term.entries)
            i += term.keys.size
        }
        return matches
    }

    /** The entries a term stands for, as written in a [[link]]. */
    fun lookup(term: String): List<Long> {
        val keys = Words.tokens(term).map { it.key }
        return byFirstKey[keys.firstOrNull()]?.firstOrNull { it.keys == keys }?.entries.orEmpty()
    }

    private fun matchesAt(term: Term, tokens: List<Token>, at: Int, text: CharSequence): Boolean {
        if (at + term.keys.size > tokens.size) return false
        for (k in term.keys.indices) {
            if (tokens[at + k].key != term.keys[k]) return false
            // The words of a term follow each other with only space or a hyphen between them.
            if (k > 0 && !text.subSequence(tokens[at + k - 1].end, tokens[at + k].start).all { it.isWhitespace() || it in "-‑⁠" }) return false
        }
        return true
    }
}
