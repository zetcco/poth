package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Definition
import dev.indrajith.poth.data.Lookup
import dev.indrajith.poth.data.Wiktionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WiktionaryTest {
    // The shape of https://en.wiktionary.org/api/rest_v1/page/definition/quartet, cut down.
    private val answer = """
        {"en":[
          {"partOfSpeech":"Noun","language":"English","definitions":[
            {"definition":"A group of four people or things, particularly <a href=\"/wiki/x\">such</a>"},
            {"definition":""},
            {"definition":"A group of <b>four</b> musicians."},
            {"definition":"A piece of music for four."},
            {"definition":"A fourth sense, left out."}
          ]},
          {"partOfSpeech":"Verb","language":"English","definitions":[{"definition":"To play in fours."}]}
        ],
        "fr":[{"partOfSpeech":"Noun","language":"French","definitions":[{"definition":"Quatuor."}]}]}
    """.trimIndent()

    @Test
    fun definitionsForTheLanguageWithoutHtmlAFewPerPartOfSpeech() {
        assertEquals(
            listOf(
                Definition("noun", "A group of four people or things, particularly such"),
                Definition("noun", "A group of four musicians."),
                Definition("noun", "A piece of music for four."),
                Definition("verb", "To play in fours."),
            ),
            Wiktionary.parse(answer, "en"),
        )
        assertEquals(listOf(Definition("noun", "Quatuor.")), Wiktionary.parse(answer, "fr"))
        assertEquals(emptyList<Definition>(), Wiktionary.parse(answer, "de"))
        assertEquals(emptyList<Definition>(), Wiktionary.parse("not json", "en"))
    }

    @Test
    fun aFormOfAnotherWordSaysWhich() {
        // As https://en.wiktionary.org/api/rest_v1/page/definition/lounged has it, cut down.
        val lounged = """
            {"en":[{"partOfSpeech":"Verb","language":"English","definitions":[{"definition":"<span class=\"form-of-definition use-with-mention\">simple <a href=\"/wiki/Appendix:Glossary#past_tense\">past</a> and past participle of <span class=\"form-of-definition-link\"><i class=\"Latn mention\" lang=\"en\"><a href=\"/wiki/lounge#English\" title=\"lounge\">lounge</a></i></span></span>"}]}]}
        """.trimIndent()
        val parsed = Wiktionary.parse(lounged, "en")
        assertEquals(listOf(Definition("verb", "simple past and past participle of lounge", formOf = "lounge")), parsed)
        assertEquals("lounge", Lookup.Found(parsed).formOf)
        assertNull(Lookup.Found(Wiktionary.parse(answer, "en")).formOf)
        assertEquals(listOf("noun", "verb"), Lookup.Found(Wiktionary.parse(answer, "en")).partsOfSpeech)
    }

    @Test
    fun asMarkdown() {
        assertEquals("*adverb* — In a prim manner.", Wiktionary.markdown(Definition("adverb", "In a prim manner.")))
    }
}
