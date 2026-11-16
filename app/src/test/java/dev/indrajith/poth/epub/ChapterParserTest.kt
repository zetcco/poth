package dev.indrajith.poth.epub

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** XHTML and CSS -> blocks of styled text. Plain JVM. */
class ChapterParserTest {
    private fun parse(html: String, css: String = "", path: String = "text/ch.html"): List<Block> {
        val parser = ChapterParser { if (it == "style.css") Css.parse(css) else emptyList() }
        return parser.parse(Jsoup.parse(html), path).blocks
    }

    private fun page(body: String) =
        """<html><head><link href="../style.css" rel="stylesheet" type="text/css"/></head><body class="calibre">$body</body></html>"""

    // The shape of a Calibre conversion: sizes that multiply out to 1.0, classes for everything.
    private val calibreCss = """
        @charset "utf-8";
        /* comment { with: braces } */
        .calibre { display: block; font-size: 1.29167em; line-height: 1.2; margin: 0 5pt }
        .CN { display: block; text-align: center; text-indent: 0; margin: 0 0 2em }
        .CDL { display: block; font-size: 0.77419em; text-align: center; text-indent: 0; margin: 0 0 1.2em }
        .TX { display: block; font-size: 0.77419em; line-height: 1.2; text-align: justify; text-indent: 1.2em; margin: 0 }
        .ePub-B { font-weight: bold }
        .ePub-I { font-style: italic }
        @media amzn-kf8 { .TX { color: red } }
    """.trimIndent()

    @Test
    fun calibreChapterOpening() {
        val blocks = parse(
            page(
                """
                <p class="CN" id="pro"><img src="../images/00004.jpeg" alt="image" class="calibre1"/></p>
                <p class="CDL"><span class="ePub-B">SIX YEARS AGO</span></p>
                <p class="TX">She said <span class="ePub-I">that</span> was it.</p>
                """,
            ),
            calibreCss,
        )
        assertEquals(3, blocks.size)

        val image = blocks[0] as ImageBlock
        assertEquals("images/00004.jpeg", image.path)
        assertEquals(0, image.start)
        assertEquals(2 * 1.29167f, image.style.marginBottom, 1e-3f)

        val heading = blocks[1] as TextBlock
        assertEquals("SIX YEARS AGO", heading.text)
        assertEquals(1, heading.start)
        assertEquals(Align.CENTER, heading.style.align)
        assertEquals(1f, heading.style.size, 1e-3f)
        assertTrue(heading.runs.single().style.bold)

        val text = blocks[2] as TextBlock
        assertEquals("She said that was it.", text.text)
        assertEquals(1 + "SIX YEARS AGO".length, text.start)
        assertEquals(Align.JUSTIFY, text.style.align)
        assertEquals(1.2f, text.style.indent, 1e-3f)
        assertEquals(1.2f, text.style.lineHeight!!, 1e-3f)
        assertEquals(5f / 12, text.style.marginLeft, 1e-3f) // the body's 5pt
        assertEquals(
            listOf("She said " to false, "that" to true, " was it." to false),
            text.runs.map { text.text.substring(it.start, it.end) to it.style.italic },
        )
    }

    @Test
    fun whitespaceBreaksAndHiddenElements() {
        val blocks = parse(
            page(
                """
                <div>  Loose   text
                  <p>One<br/>  two  </p>
                  <p style="display: none">hidden</p>
                  <p class="gone">also hidden</p>
                  <p>&nbsp;</p>
                </div>
                """,
            ),
            ".gone { display: none }",
        )
        assertEquals(listOf("Loose text", "One\ntwo", " "), blocks.map { (it as TextBlock).text })
    }

    @Test
    fun marginsCollapseAndPassThroughEmptyBlocks() {
        val blocks = parse(
            page("""<p class="a">A</p><div class="empty"></div><p class="b">B</p>"""),
            ".calibre { margin: 0 } .a { margin: 0 0 1em } .empty { margin: 3em 0 0 } .b { margin: 2em 0 0 }",
        ).map { it as TextBlock }
        assertEquals(1f, blocks[0].style.marginBottom, 1e-3f)
        assertEquals(3f, blocks[1].style.marginTop, 1e-3f) // the empty div's 3em wins over B's own 2em
    }

    @Test
    fun specificityBeatsOrderAndInlineStyleBeatsBoth() {
        val blocks = parse(
            page("""<p class="c">A</p><p class="c" style="text-align: right">B</p>"""),
            ".c { text-align: center } p { text-align: left } p::first-letter { font-size: 3em } a:hover { color: red }",
        ).map { it as TextBlock }
        assertEquals(Align.CENTER, blocks[0].style.align)
        assertEquals(Align.END, blocks[1].style.align)
        assertEquals(1f, blocks[0].style.size, 1e-3f) // the pseudo-element rule is skipped
    }

    @Test
    fun smallCapsHeadingsListsRulesAndPageBreaks() {
        val blocks = parse(
            page(
                """
                <h2>Part One</h2>
                <p class="sc">Kal</p>
                <hr/>
                <ol><li>first</li><li>second</li></ol>
                <p class="break">Next</p>
                """,
            ),
            ".calibre { margin: 0 } .sc { font-variant: small-caps } .break { page-break-before: always }",
        )
        val heading = blocks[0] as TextBlock
        assertTrue(heading.style.keepWithNext)
        assertEquals(1.4f, heading.style.size, 1e-3f)
        assertTrue(heading.runs.single().style.bold)

        val smallCaps = blocks[1] as TextBlock
        assertEquals("KAL", smallCaps.text)
        assertEquals(listOf(1f, 0.8f), smallCaps.runs.map { it.style.size })

        assertTrue(blocks[2] is RuleBlock)
        assertEquals(listOf("1. first", "2. second"), blocks.subList(3, 5).map { (it as TextBlock).text })
        assertTrue(blocks[5].style.pageBreakBefore)
        // Offsets run on without gaps: text counts its characters, the rule counts one.
        for (i in 1 until blocks.size) assertEquals(blocks[i - 1].start + blocks[i - 1].length, blocks[i].start)
    }

    @Test
    fun emDashesStayWithTheWordBefore() {
        val block = parse(page("<p>that shadow—skin, and — this</p>")).single() as TextBlock
        assertEquals("that shadow⁠—skin, and — this", block.text)
    }

    @Test
    fun svgCoverImage() {
        val blocks = parse(
            """<html><body><svg><image width="600" height="800" xlink:href="cover.jpeg"/></svg></body></html>""",
            path = "titlepage.xhtml",
        )
        assertEquals("cover.jpeg", (blocks.single() as ImageBlock).path)
    }
}
