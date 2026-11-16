package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsTest {
    @Test
    fun tokensKeepApostrophesInsideWordsAndDropPossessivesFromTheKey() {
        val text = "Elhokar’s wife—Aesudan—didn't; the knights' gap-toothed man"
        assertEquals(
            listOf("elhokar", "wife", "aesudan", "didn't", "the", "knights", "gap", "toothed", "man"),
            Words.tokens(text).map { it.key },
        )
        assertEquals("Elhokar’s", Words.tokens(text).first().let { text.substring(it.start, it.end) })
    }

    @Test
    fun wordAtAnOffset() {
        val text = "the Parshendi drummers."
        assertEquals("parshendi", Words.at(text, 6)?.key)
        assertEquals("parshendi", Words.at(text, 13)?.key) // just after the word
        assertEquals("drummers", Words.at(text, 22)?.key)  // on the full stop
        assertNull(Words.at(" — ", 1))
    }

    @Test
    fun sentenceStarts() {
        val text = "It was late. “Storms,” she said. Then Kaladin left."
        assertTrue(Words.startsSentence(text, 0))
        assertTrue(Words.startsSentence(text, text.indexOf("Storms")))
        assertTrue(Words.startsSentence(text, text.indexOf("Then")))
        assertFalse(Words.startsSentence(text, text.indexOf("Kaladin")))
    }

    @Test
    fun scopeSuggestions() {
        assertEquals(Scope.LIBRARY, Words.suggestScope("primly", startsSentence = false, appearsInLowercase = true, onShelf = true))
        assertEquals(Scope.SHELF, Words.suggestScope("Parshendi", startsSentence = false, appearsInLowercase = false, onShelf = true))
        assertEquals(Scope.BOOK, Words.suggestScope("Parshendi", startsSentence = false, appearsInLowercase = false, onShelf = false))
        // Capitalised only for starting a sentence, and found in lowercase elsewhere: a general word.
        assertEquals(Scope.LIBRARY, Words.suggestScope("Storms", startsSentence = true, appearsInLowercase = true, onShelf = true))
        assertEquals(Scope.SHELF, Words.suggestScope("Kaladin", startsSentence = true, appearsInLowercase = false, onShelf = true))

        assertEquals("Elhokar", Words.headword("Elhokar’s", Scope.SHELF))
        assertEquals("storms", Words.headword("Storms", Scope.LIBRARY))
    }

    @Test
    fun shelfSuggestions() {
        assertEquals("The Stormlight Archive", Words.suggestShelf("Words of Radiance (Stormlight Archive, The)", null))
        assertEquals("Mistborn", Words.suggestShelf("The Final Empire (Mistborn)", null))
        assertEquals("The Expanse", Words.suggestShelf("Leviathan Wakes", "The Expanse"))
        assertEquals("", Words.suggestShelf("Piranesi", null))
    }

    @Test
    fun matcherFindsWholeWordsLongestFirst() {
        val matcher = Matcher(listOf("Jasnah" to 1L, "Jasnah Kholin" to 2L, "Alethi" to 3L, "alethi" to 4L, "gap-toothed" to 5L))
        val text = "Jasnah Kholin’s brother; the Alethi court. Jasnah. Kholin stayed, gap-toothed and Alethikar."
        val found = matcher.find(text).map { text.substring(it.start, it.end) to it.entries }
        assertEquals(
            listOf(
                "Jasnah Kholin’s" to listOf(2L),
                "Alethi" to listOf(3L, 4L), // one term, two entries (say, a book's and the library's)
                "Jasnah" to listOf(1L),     // not "Jasnah. Kholin": the full stop ends the term
                "gap-toothed" to listOf(5L),
            ),
            found,
        )
        assertEquals(listOf(2L), matcher.lookup("jasnah kholin"))
        assertEquals(emptyList<Long>(), matcher.lookup("Kholin"))
    }

    @Test
    fun matcherLooksOnlyWhereAsked() {
        val matcher = Matcher(listOf("Syl" to 1L))
        val text = "Syl laughed. Syl flew."
        assertEquals(listOf(13), matcher.find(text, 10, text.length).map { it.start })
    }
}
