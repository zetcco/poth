package dev.indrajith.poth.stats

import dev.indrajith.poth.data.PageView
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsTest {
    private val zone = ZoneId.of("Asia/Colombo")
    private val rules = ReadingRules()
    private val wpm = 230.0

    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    /** A page of [words] words on screen for [seconds] from [start]. */
    private fun view(start: Long, seconds: Int, words: Int = 230, book: String = "b", arrivedBy: String = "next") =
        PageView(book, start, start + seconds * 1_000L, 0, 0, 0, words, arrivedBy)

    /** Pages read one after another from [start]: each 230 words for [seconds]. */
    private fun pages(start: Long, count: Int, seconds: Int = 60, book: String = "b") =
        List(count) { view(start + it * seconds * 1_000L, seconds, book = book) }

    @Test
    fun aPageCountsAfterAShareOfItsReadingTimeAndIsCapped() {
        // 230 words at 230 wpm is a minute: it counts from 18 s, and up to 3 minutes of it.
        assertNull(ReadingStats.countedMillis(view(0, 10), wpm, rules))
        assertEquals(20_000L, ReadingStats.countedMillis(view(0, 20), wpm, rules))
        assertEquals(180_000L, ReadingStats.countedMillis(view(0, 600), wpm, rules))
        // A picture page: no words, counts from 3 s, up to a minute.
        assertNull(ReadingStats.countedMillis(view(0, 2, words = 0), wpm, rules))
        assertEquals(60_000L, ReadingStats.countedMillis(view(0, 300, words = 0), wpm, rules))
    }

    @Test
    fun glancesAreLeftOutAndSittingsAddUp() {
        val views = listOf(
            // A glance: two quick pages in the morning, 50 s in all.
            view(at(16, 8), 25), view(at(16, 8, 0, 25), 25),
            // A sitting: five minutes' reading, one page left open for ten minutes (capped at 3).
            *pages(at(16, 21), 5).toTypedArray(),
            view(at(16, 21, 5), 600),
            // Flipping through: too quick to count, but in the same sitting.
            view(at(16, 21, 15), 2, arrivedBy = "next"),
        )
        val summary = ReadingStats.summarize(views, zone)
        val day = summary.days.getValue(LocalDate.of(2026, 11, 16))
        assertEquals(8 * 60_000L, day.millis)
        assertEquals(6, day.pages)
        assertEquals(6 * 230, day.words)
        assertEquals(8L, summary.minutesOn(LocalDate.of(2026, 11, 16)))
        assertEquals(6, summary.books.getValue("b").pages)
    }

    @Test
    fun aLongGapOrAnotherBookStartsANewSitting() {
        val views = pages(at(16, 9), 1, seconds = 50) + // 50 s alone: a glance
            pages(at(16, 9, 10), 1, seconds = 50) +      // ten minutes later: another glance
            pages(at(16, 9, 11), 2, seconds = 50, book = "other") // another book, 100 s: counts
        val summary = ReadingStats.summarize(views, zone)
        assertEquals(setOf("other"), summary.books.keys)
        assertEquals(100_000L, summary.days.getValue(LocalDate.of(2026, 11, 16)).millis)
    }

    @Test
    fun timeGoesToTheDayEachPageWasShown() {
        val views = pages(at(16, 23, 58), 4) // 23:58 to 00:02
        val summary = ReadingStats.summarize(views, zone)
        assertEquals(2 * 60_000L, summary.days.getValue(LocalDate.of(2026, 11, 16)).millis)
        assertEquals(2 * 60_000L, summary.days.getValue(LocalDate.of(2026, 11, 17)).millis)
    }

    @Test
    fun streaks() {
        val views = listOf(10, 11, 12, 14, 15, 16).flatMap { day -> pages(at(day, 20), 2) }
        val summary = ReadingStats.summarize(views, zone)
        assertEquals(Streak(current = 3, longest = 3), summary.streak(LocalDate.of(2026, 11, 16)))
        // Not read yet today: yesterday's streak still stands.
        assertEquals(Streak(current = 3, longest = 3), summary.streak(LocalDate.of(2026, 11, 17)))
        assertEquals(Streak(current = 0, longest = 3), summary.streak(LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun readingSpeedIsMeasuredOnceThereAreEnoughPages() {
        val few = ReadingStats.summarize(pages(at(16, 20), 10, seconds = 30), zone)
        assertEquals(230.0, few.wordsPerMinute, 0.01)
        assertEquals(10, few.speedPages)
        assertFalse(few.speedMeasured)
        // 230 words in 30 s is 460 wpm; a page left open and a jump are not measured.
        val many = pages(at(16, 20), 25, seconds = 30) + view(at(16, 22), 900) + view(at(16, 23), 5, arrivedBy = "jump")
        val summary = ReadingStats.summarize(many, zone)
        assertEquals(460.0, summary.wordsPerMinute, 0.01)
        assertEquals(25, summary.speedPages)
        assertTrue(summary.speedMeasured)
    }

    @Test
    fun speedMeasuredAfreshLeavesOutEarlierPages() {
        // 25 skimmed pages, then the speed is measured again from 21:00: 22 pages at 115 wpm.
        val views = pages(at(16, 20), 25, seconds = 30) + pages(at(16, 21), 22, seconds = 120)
        val summary = ReadingStats.summarize(views, zone, ReadingRules(speedSince = at(16, 21)))
        assertEquals(115.0, summary.wordsPerMinute, 0.01)
        assertEquals(22, summary.speedPages)
        // Fewer than 20 pages since: the default again, until there are enough.
        val early = ReadingStats.summarize(views.take(30), zone, ReadingRules(speedSince = at(16, 21)))
        assertEquals(230.0, early.wordsPerMinute, 0.01)
        assertEquals(5, early.speedPages)
    }

    @Test
    fun heatmapLevels() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0L, 14L, 15L, 59L, 120L).map(ReadingStats::level))
    }
}
