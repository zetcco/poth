package dev.indrajith.poth.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CssTest {
    @Test
    fun rulesCommentsAndAtRules() {
        val rules = Css.parse(
            """
            @charset "utf-8";
            @page { margin-bottom: 5pt }
            /* p { color: red } */
            h1, .Title { Font-Weight: Bold !important; margin: 0 }
            @media screen { p { color: blue } }
            p.x>span{font-style:italic}
            empty { }
            """.trimIndent(),
        )
        assertEquals(listOf("h1", ".Title", "p.x>span"), rules.map { it.selector })
        assertEquals(mapOf("font-weight" to "bold", "margin" to "0"), rules[0].declarations)
        assertEquals(mapOf("font-style" to "italic"), rules[2].declarations)
    }

    @Test
    fun specificityOrdersIdsClassesTags() {
        assertEquals(1, Css.specificity("p"))
        assertEquals(100, Css.specificity(".TX"))
        assertEquals(102, Css.specificity("div p.TX"))
        assertEquals(10_000, Css.specificity("#pro"))
        assertTrue(Css.specificity(".a .b") > Css.specificity("body div p"))
    }
}
