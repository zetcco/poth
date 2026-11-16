package dev.indrajith.poth.reader

import dev.indrajith.poth.data.PageView
import org.junit.Assert.assertEquals
import org.junit.Test

class PageClockTest {
    private var time = 1_000L
    private val views = mutableListOf<PageView>()
    private val clock = PageClock("book", views::add) { time }

    @Test
    fun eachPageIsRecordedWhenItLeavesTheScreen() {
        clock.show(spine = 3, start = 0, end = 900, words = 160, arrivedBy = Arrival.OPEN)
        time += 45_000
        clock.show(spine = 3, start = 900, end = 1800, words = 150, arrivedBy = Arrival.NEXT)
        time += 2_000
        clock.stop()
        clock.stop() // nothing more to record

        assertEquals(
            listOf(
                PageView("book", 1_000, 46_000, 3, 0, 900, 160, Arrival.OPEN),
                PageView("book", 46_000, 48_000, 3, 900, 1800, 150, Arrival.NEXT),
            ),
            views,
        )
    }

    @Test
    fun goingToTheBackEndsTheViewAndComingBackStartsANewOne() {
        clock.show(spine = 1, start = 10, end = 20, words = 2, arrivedBy = Arrival.OPEN)
        time += 5_000
        clock.pause()
        time += 600_000 // ten minutes away: not part of any view
        clock.resume()
        time += 3_000
        clock.stop()

        assertEquals(
            listOf(
                PageView("book", 1_000, 6_000, 1, 10, 20, 2, Arrival.OPEN),
                PageView("book", 606_000, 609_000, 1, 10, 20, 2, Arrival.RESUME),
            ),
            views,
        )
    }

    @Test
    fun resumeWithoutPauseDoesNothing() {
        clock.resume()
        clock.stop()
        assertEquals(emptyList<PageView>(), views)
    }
}
