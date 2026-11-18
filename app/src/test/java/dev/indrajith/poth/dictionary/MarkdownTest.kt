package dev.indrajith.poth.dictionary

import dev.indrajith.poth.dictionary.Markdown.Block
import dev.indrajith.poth.dictionary.Markdown.Kind
import dev.indrajith.poth.dictionary.Markdown.Span
import dev.indrajith.poth.dictionary.Markdown.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun picturesAreBlocksOfTheirOwn() {
        assertEquals(
            listOf(
                Block(Kind.PARAGRAPH, "Seen on the ship:"),
                Block(Kind.IMAGE, "", target = "img:3f9a.png"),
                Block(Kind.IMAGE, "Tozbek's map", target = "img:77b0.jpg"),
                Block(Kind.PARAGRAPH, "and more."),
            ),
            Markdown.parse("Seen on the ship:\n![](img:3f9a.png)\n![Tozbek's map](img:77b0.jpg) and more."),
        )
    }

    @Test
    fun blocks() {
        val blocks = Markdown.parse(
            """
            # Parshendi
            Warlike people on the plains.
            Sing in rhythms.

            - one
              - nested
            3. third
            > a quote
            > goes on
            ---
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Block(Kind.HEADING, "Parshendi", level = 1),
                Block(Kind.PARAGRAPH, "Warlike people on the plains.\nSing in rhythms."),
                Block(Kind.BULLET, "one"),
                Block(Kind.BULLET, "nested", level = 1),
                Block(Kind.NUMBERED, "third", marker = "3."),
                Block(Kind.QUOTE, "a quote\ngoes on"),
                Block(Kind.RULE, ""),
            ),
            blocks,
        )
    }

    @Test
    fun inlineStyles() {
        val (text, spans) = Markdown.inline("A **bold *mixed* word**, `code`, [[Alethi|their people]] and [site](https://x.y) snake_case _it_")
        assertEquals("A bold mixed word, code, their people and site snake_case it", text)
        fun range(span: Span) = text.substring(span.start, span.end)
        assertEquals(
            listOf(
                "bold mixed word" to Style.BOLD,
                "mixed" to Style.ITALIC,
                "code" to Style.CODE,
                "their people" to Style.ENTRY,
                "site" to Style.LINK,
                "it" to Style.ITALIC,
            ),
            spans.map { range(it) to it.style },
        )
        assertEquals("Alethi", spans.first { it.style == Style.ENTRY }.target)
        assertEquals("https://x.y", spans.first { it.style == Style.LINK }.target)
    }

    @Test
    fun unclosedMarkersStayAsText() {
        assertEquals("2 * 3 and **open" to emptyList<Span>(), Markdown.inline("2 * 3 and **open"))
    }
}
