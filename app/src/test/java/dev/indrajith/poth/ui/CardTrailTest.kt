package dev.indrajith.poth.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTrailTest {
    @Test
    fun backGoesThroughTheLinksFollowed() {
        val trail = CardTrail()
        trail.open(listOf(1))
        assertNull(trail.previous)
        trail.follow(listOf(2))
        trail.follow(listOf(3, 4))
        assertEquals(listOf(3L, 4L), trail.ids)
        assertEquals(listOf(2L), trail.previous)
        trail.back()
        assertEquals(listOf(2L), trail.ids)
        trail.back()
        assertEquals(listOf(1L), trail.ids)
        assertNull(trail.previous)
    }

    @Test
    fun aLinkToTheEntryShownGoesNowhere() {
        val trail = CardTrail()
        trail.open(listOf(1))
        trail.follow(listOf(1))
        assertNull(trail.previous)
    }

    @Test
    fun openingTheCardForAWordStartsAfresh() {
        val trail = CardTrail()
        trail.open(listOf(1))
        trail.follow(listOf(2))
        trail.open(listOf(5))
        assertEquals(listOf(5L), trail.ids)
        assertNull(trail.previous)
        trail.close()
        assertEquals(emptyList<Long>(), trail.ids)
    }
}
