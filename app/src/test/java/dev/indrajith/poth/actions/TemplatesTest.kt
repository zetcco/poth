package dev.indrajith.poth.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplatesTest {
    private val context = LookupContext(
        word = "Parshendi",
        sentence = "She passed the Parshendi delegation.",
        book = "Words of Radiance",
        author = "Brandon Sanderson",
        chapter = "Prologue: To Question",
    )

    @Test
    fun placeholdersAreFilledIn() {
        assertEquals("Parshendi", Templates.fill(Templates.WORD, context))
        assertEquals(
            "In the book \"Words of Radiance\" by Brandon Sanderson, what does \"Parshendi\" mean? I'm reading " +
                "Prologue: To Question, at this sentence: \"She passed the Parshendi delegation.\". Answer without " +
                "spoiling anything that happens after this point.",
            Templates.fill(Templates.ASK, context),
        )
        assertEquals("{unknown} Parshendi", Templates.fill("{unknown} {word}", context))
        assertEquals("at the book", Templates.fill("at {chapter}", context.copy(chapter = "")))
    }
}
