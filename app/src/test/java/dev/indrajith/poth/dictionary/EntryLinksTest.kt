package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryLinksTest {
    @Test
    fun aLinkIsOpenUntilItIsClosed() {
        assertEquals(EntryLinks.Open(6, "Par"), EntryLinks.open("see [[Par", 9))
        assertEquals(EntryLinks.Open(2, ""), EntryLinks.open("[[", 2))
        assertNull(EntryLinks.open("see [[Par]] then", 16))
        assertNull(EntryLinks.open("see [[Par\nnext", 14)) // another line
        assertNull(EntryLinks.open("[[Szeth|the assassin", 20)) // the shown text
        assertNull(EntryLinks.open("no link", 7))
    }

    @Test
    fun finishingALinkClosesIt() {
        val text = "see [[Par and more"
        val (finished, cursor) = EntryLinks.complete(text, 9, EntryLinks.open(text, 9)!!, "Parshendi")
        assertEquals("see [[Parshendi]] and more", finished)
        assertEquals("see [[Parshendi]]".length, cursor)
    }

    @Test
    fun finishingAClosedLinkDoesNotCloseItTwice() {
        val text = "see [[Par]] here"
        val (finished, cursor) = EntryLinks.complete(text, 9, EntryLinks.open(text, 9)!!, "Parshendi")
        assertEquals("see [[Parshendi]] here", finished)
        assertEquals("see [[Parshendi]]".length, cursor)
    }

    @Test
    fun finishingALinkKeepsItsShownText() {
        val text = "[[Sz|the assassin]] came"
        val (finished, cursor) = EntryLinks.complete(text, 4, EntryLinks.open(text, 4)!!, "Szeth")
        assertEquals("[[Szeth|the assassin]] came", finished)
        assertEquals("[[Szeth|the assassin]]".length, cursor)
    }

    @Test
    fun selectedWordsBecomeALink() {
        assertEquals("the [[Parshendi]] came" to 17, EntryLinks.start("the Parshendi came", 4, 13))
        // A space caught in the selection stays outside the link.
        assertEquals("the [[Parshendi]] came" to 17, EntryLinks.start("the Parshendi came", 3, 13))
    }

    @Test
    fun withNothingSelectedALinkIsStarted() {
        assertEquals("see [[" to 6, EntryLinks.start("see ", 4, 4))
        assertEquals("see [[" to 6, EntryLinks.start("see", 3, 3))
        assertEquals("[[" to 2, EntryLinks.start("", 0, 0))
    }

    private val entries = listOf(
        Entry(id = 1, headword = "Parshendi", scope = Scope.SHELF, updatedAt = 10),
        Entry(id = 2, headword = "Parshmen", scope = Scope.SHELF, updatedAt = 30),
        Entry(id = 3, headword = "Szeth", aliases = listOf("Assassin in White"), scope = Scope.SHELF, updatedAt = 20),
        Entry(id = 4, headword = "spren", scope = Scope.LIBRARY, updatedAt = 5),
        Entry(id = 5, headword = "Parshendi", scope = Scope.BOOK, updatedAt = 40), // another meaning
    )

    @Test
    fun suggestionsMatchTheStartOfANameOrOfAWordInIt() {
        assertEquals(listOf("Parshendi", "Parshmen"), EntryLinks.suggest(entries, "pars", writing = "spren").map { it.term })
        val white = EntryLinks.suggest(entries, "white", writing = "spren")
        assertEquals(listOf(EntryLinks.Suggestion("Assassin in White", "Szeth")), white)
    }

    @Test
    fun anExactNameComesFirst() {
        val list = listOf(
            Entry(id = 1, headword = "Shardblade", scope = Scope.SHELF),
            Entry(id = 2, headword = "Shard", scope = Scope.SHELF),
        )
        assertEquals(listOf("Shard", "Shardblade"), EntryLinks.suggest(list, "shard", writing = "").map { it.term })
    }

    @Test
    fun withNothingTypedTheLatestComeFirstAndNeverTheEntryBeingWritten() {
        val suggestions = EntryLinks.suggest(entries, "", writing = "Parshmen").map { it.term }
        assertEquals(listOf("Parshendi", "Szeth", "spren"), suggestions)
    }
}
