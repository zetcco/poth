package dev.indrajith.poth.dictionary

import dev.indrajith.poth.dictionary.Forms.Form
import dev.indrajith.poth.dictionary.Forms.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormsTest {
    @Test
    fun plurals() {
        assertEquals("brutes", Forms.plural("brute"))
        assertEquals("witnesses", Forms.plural("witness"))
        assertEquals("boxes", Forms.plural("box"))
        assertEquals("churches", Forms.plural("church"))
        assertEquals("cities", Forms.plural("city"))
        assertEquals("days", Forms.plural("day"))
        assertEquals("Kennedys", Forms.plural("Kennedy"))
        assertEquals("Shardbearers", Forms.plural("Shardbearer"))
        // Unsure endings are left alone: heroes but photos, leaves but roofs.
        assertNull(Forms.plural("hero"))
        assertNull(Forms.plural("leaf"))
        assertNull(Forms.plural("knife"))
        assertNull(Forms.plural("crisis"))
    }

    @Test
    fun verbEndings() {
        assertEquals(listOf("lounged", "stopped", "cried", "played", "fixed", "showed", "visited", "quitted"), listOf("lounge", "stop", "cry", "play", "fix", "show", "visit", "quit").map(Forms::past))
        assertEquals(listOf("lounging", "stopping", "crying", "dying", "seeing", "visiting", "quitting"), listOf("lounge", "stop", "cry", "die", "see", "visit", "quit").map(Forms::ing))
    }

    @Test
    fun comparisonsOnlyForShortAdjectives() {
        assertEquals(listOf("happier", "happiest"), Forms.regular("happy", setOf(Kind.ADJECTIVE)))
        assertEquals(listOf("bigger", "biggest"), Forms.regular("big", setOf(Kind.ADJECTIVE)))
        assertEquals(listOf("ruder", "rudest"), Forms.regular("rude", setOf(Kind.ADJECTIVE)))
        assertEquals(emptyList<String>(), Forms.regular("careful", setOf(Kind.ADJECTIVE)))
    }

    @Test
    fun formsForEachKind() {
        assertEquals(listOf("lounges", "lounged", "lounging"), Forms.regular("lounge", setOf(Kind.NOUN, Kind.VERB)))
        assertEquals(listOf("brutes"), Forms.regular("brute", setOf(Kind.NOUN)))
        // Not for phrases or very short words.
        assertEquals(emptyList<String>(), Forms.regular("Jasnah Kholin", setOf(Kind.NOUN)))
        assertEquals(emptyList<String>(), Forms.regular("ox", setOf(Kind.NOUN)))
    }

    @Test
    fun kindsFromTheFirstPartsOfSpeech() {
        assertEquals(setOf(Kind.NOUN, Kind.VERB), Forms.kinds(listOf("verb", "noun")))
        assertEquals(setOf(Kind.ADJECTIVE), Forms.kinds(listOf("adjective", "noun", "verb")))
        assertEquals(setOf(Kind.NOUN), Forms.kinds(listOf("noun", "adjective")))
        assertEquals(emptySet<Kind>(), Forms.kinds(listOf("adverb", "noun")))
        assertEquals(emptySet<Kind>(), Forms.kinds(emptyList()))
    }

    @Test
    fun offeredSureOnesAndTheLikelyOnesTheBookUses() {
        val counts = mapOf("lounged" to 7, "lounging" to 8, "shardbearers" to 70)
        assertEquals(
            listOf(Form("lounging", 8), Form("lounged", 7), Form("lounges", null)),
            Forms.offer("lounge", sure = listOf("lounges", "lounged", "lounging"), likely = emptyList(), counts),
        )
        assertEquals(listOf(Form("Shardbearers", 70)), Forms.offer("Shardbearer", emptyList(), listOf("Shardbearers"), counts))
        // A likely form the book never uses isn't offered, nor is one without counts.
        assertEquals(emptyList<Form>(), Forms.offer("Liss", emptyList(), listOf("Lisses"), counts))
        assertEquals(emptyList<Form>(), Forms.offer("Shardbearer", emptyList(), listOf("Shardbearers"), null))
    }
}
