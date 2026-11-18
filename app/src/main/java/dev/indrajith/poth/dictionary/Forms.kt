package dev.indrajith.poth.dictionary

import java.util.Locale

/**
 * The regular English forms of a word, for an entry to match as well: plurals, verb endings and
 * comparisons, made by rule. Rules guess wrong now and then (see → "seed", run → "runned"), so a
 * guess is only offered once something vouches for it: Wiktionary calling it a form of the word,
 * or the book using it. Irregular forms (ran, mice) and unsure endings (heroes or photos? leaves
 * or roofs?) are not guessed at all.
 */
object Forms {
    enum class Kind { NOUN, VERB, ADJECTIVE }

    /** A form to offer, and how many times the book has it (null if not counted). */
    data class Form(val text: String, val count: Int?)

    /** The most forms offered at once. */
    const val MOST = 6

    /**
     * The kinds of word to make forms for, from Wiktionary's parts of speech in its order: the
     * first, or a noun and a verb both when those are the first two (lounge). Later senses are
     * left out, so a rare "happy" verb does not bring "happied".
     */
    fun kinds(partsOfSpeech: List<String>): Set<Kind> {
        val first = partsOfSpeech.distinct().take(2).map(::kind)
        val both = setOf(Kind.NOUN, Kind.VERB)
        return if (first.toSet() == both) both else setOfNotNull(first.firstOrNull())
    }

    private fun kind(partOfSpeech: String) = when (partOfSpeech.lowercase(Locale.ROOT)) {
        "noun" -> Kind.NOUN
        "verb" -> Kind.VERB
        "adjective" -> Kind.ADJECTIVE
        else -> null
    }

    /** The rule-made forms of [word] for [kinds]; none for a phrase, or a word under three letters. */
    fun regular(word: String, kinds: Set<Kind>): List<String> {
        if (word.length < 3 || !word.all(Char::isLetter)) return emptyList()
        return buildList {
            if (Kind.NOUN in kinds) plural(word)?.let(::add)
            if (Kind.VERB in kinds) {
                plural(word)?.let(::add)
                add(past(word))
                add(ing(word))
            }
            if (Kind.ADJECTIVE in kinds && short(word)) {
                add(compare(word, "er"))
                add(compare(word, "est"))
            }
        }.distinct()
    }

    /**
     * What to offer: the [sure] forms, and the [likely] ones the book has ([counts] by
     * [Words.key]), most used first. Not the word itself, nor a form twice.
     */
    fun offer(word: String, sure: List<String>, likely: List<String>, counts: Map<String, Int>?): List<Form> {
        fun count(form: String) = counts?.get(Words.key(form))
        return (sure.map { Form(it, count(it)) } + likely.mapNotNull { form -> count(form)?.takeIf { it > 0 }?.let { Form(form, it) } })
            .distinctBy { it.text.lowercase(Locale.ROOT) }
            .filter { !it.text.equals(word, ignoreCase = true) }
            .sortedByDescending { it.count ?: 0 }
            .take(MOST)
    }

    /** The plural, or a verb's -s. Null where the ending leaves it unsure: -o, -f, -fe, -is. */
    internal fun plural(word: String): String? {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("o") || lower.endsWith("f") || lower.endsWith("fe") || lower.endsWith("is") -> null
            listOf("s", "x", "z", "ch", "sh").any(lower::endsWith) -> word + "es"
            // Names keep their y: the Kennedys.
            consonantY(lower) && !word.first().isUpperCase() -> word.dropLast(1) + "ies"
            else -> word + "s"
        }
    }

    internal fun past(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("e") -> word + "d"
            consonantY(lower) -> word.dropLast(1) + "ied"
            doubles(lower) -> word + word.last() + "ed"
            else -> word + "ed"
        }
    }

    internal fun ing(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("ie") -> word.dropLast(2) + "ying"
            lower.endsWith("e") && !listOf("ee", "oe", "ye").any(lower::endsWith) -> word.dropLast(1) + "ing"
            doubles(lower) -> word + word.last() + "ing"
            else -> word + "ing"
        }
    }

    /** -er or -est on an adjective. */
    internal fun compare(word: String, ending: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("e") -> word + ending.drop(1)
            consonantY(lower) -> word.dropLast(1) + "i" + ending
            doubles(lower) -> word + word.last() + ending
            else -> word + ending
        }
    }

    /** Whether an adjective takes -er and -est: one syllable, or two ending in -y (happy). */
    private fun short(lower: String): Boolean {
        val syllables = vowelGroups(lower) - (if (lower.endsWith("e") && !lower.endsWith("le")) 1 else 0)
        return syllables <= 1 || syllables == 2 && lower.endsWith("y")
    }

    private fun isVowel(lower: String, i: Int): Boolean {
        val c = lower[i]
        // The u of qu is a consonant (quit), and y is a vowel after the first letter (happy).
        return c in "aeio" || c == 'u' && !(i > 0 && lower[i - 1] == 'q') || c == 'y' && i > 0
    }

    private fun vowelGroups(lower: String) = lower.indices.count { isVowel(lower, it) && (it == 0 || !isVowel(lower, it - 1)) }

    private fun consonantY(lower: String) = lower.length >= 2 && lower.endsWith("y") && !isVowel(lower, lower.length - 2)

    /**
     * Whether the last consonant doubles before -ed, -ing, -er: a one-syllable word ending in a
     * single vowel and a consonant other than w, x or y (stop → stopped, but rain, fix, show).
     * Longer words depend on stress (preferred, visited), so they are left as they are.
     */
    private fun doubles(lower: String): Boolean {
        val n = lower.length
        if (n < 3 || vowelGroups(lower) != 1) return false
        return !isVowel(lower, n - 1) && lower[n - 1] !in "wxy" && isVowel(lower, n - 2) && !isVowel(lower, n - 3)
    }
}
