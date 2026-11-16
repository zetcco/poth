package dev.indrajith.poth.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeTest {
    private val width = 1080f
    private val fling = 1050f

    @Test
    fun aSwipeGoesTheWayItMostlyMoves() {
        assertTrue(Swipe.sideways(-30f, 10f))
        assertFalse(Swipe.sideways(-10f, 30f))
        assertFalse(Swipe.sideways(20f, -20f)) // a tie is not sideways
    }

    @Test
    fun aFlickOrALongSwipeTurnsThePage() {
        assertEquals(true, Swipe.turn(-200f, 20f, -3000f, 100f, offset = -150f, width, fling))
        assertEquals(false, Swipe.turn(200f, 20f, 3000f, 100f, offset = 150f, width, fling))
        // Slow, but past a quarter of the page.
        assertEquals(true, Swipe.turn(-400f, 30f, -100f, 0f, offset = -400f, width, fling))
        // Slow and short: back where it was.
        assertNull(Swipe.turn(-100f, 10f, -100f, 0f, offset = -100f, width, fling))
    }

    @Test
    fun aSwipeThatEndsUpMostlyUpOrDownTurnsNothing() {
        // Started sideways, then went on up the page.
        assertNull(Swipe.turn(-150f, -600f, -3000f, -200f, offset = -150f, width, fling))
    }

    @Test
    fun aFlickUpOrDownAtTheEndIsNotAFlickSideways() {
        // Sideways overall, too short for its distance to count, and the last flick went up.
        assertNull(Swipe.turn(-200f, -100f, -1500f, -4000f, offset = -200f, width, fling))
    }

    @Test
    fun aFlickTheOtherWaySpringsBack() {
        // Dragged far forward, then flicked back.
        assertEquals(false, Swipe.turn(-300f, 0f, 2000f, 0f, offset = -300f, width, fling))
        assertNull(Swipe.turn(-300f, 0f, 500f, 0f, offset = -300f, width, fling))
    }

    @Test
    fun anEdgeSwipeGoesBackOnceItIsFarEnoughIn() {
        assertTrue(Swipe.back(inward = 120f, dy = 30f, distance = 105f))
        assertFalse(Swipe.back(inward = 80f, dy = 10f, distance = 105f))
        assertFalse(Swipe.back(inward = 120f, dy = 300f, distance = 105f)) // mostly up or down
        assertFalse(Swipe.back(inward = -200f, dy = 0f, distance = 105f)) // out towards the edge
    }
}
