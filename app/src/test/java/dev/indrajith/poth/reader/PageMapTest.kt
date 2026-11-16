package dev.indrajith.poth.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageMapTest {
    // Spine items 0 (cover, 1 page), 2 (empty), 3 (5 pages), 5 (3 pages); 1 and 4 are not linear.
    private val map = PageMap(readingOrder = listOf(0, 2, 3, 5), counts = intArrayOf(1, 0, 5, 3))

    @Test
    fun numbersRunThroughTheBook() {
        assertEquals(9, map.total)
        assertEquals(0, map.number(spine = 0, index = 0))
        assertEquals(1, map.number(spine = 3, index = 0))
        assertEquals(5, map.number(spine = 3, index = 4))
        assertEquals(8, map.number(spine = 5, index = 2))
        assertEquals(5, map.number(spine = 3, index = 99)) // a stale index stays in its chapter
        assertEquals(listOf(0, 1, 6), map.chapterStarts)
    }

    @Test
    fun locateSkipsChaptersWithoutPages() {
        assertEquals(0 to 0, map.locate(0))
        assertEquals(3 to 0, map.locate(1))
        assertEquals(3 to 4, map.locate(5))
        assertEquals(5 to 0, map.locate(6))
        assertEquals(5 to 2, map.locate(100))
        assertEquals(0 to 0, map.locate(-3))
    }
}
