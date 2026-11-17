package dev.indrajith.poth.stats

import dev.indrajith.poth.data.PageView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * What counts as reading. The page views are stored raw; these rules are applied when stats are
 * worked out, so changing them changes every past day too.
 */
data class ReadingRules(
    /** Reading speed until there are enough pages to measure the reader's own. */
    val defaultWordsPerMinute: Double = 230.0,
    /** A page counts once it has been on screen this share of the time it takes to read it… */
    val minShareOfPage: Double = 0.3,
    /** …and at least this long (also how long a picture page needs). */
    val minPageMillis: Long = 3_000,
    /** Time on a page beyond this many times its reading time does not count… */
    val capTimesPage: Double = 3.0,
    /** …though a page is always allowed this much. */
    val minCapMillis: Long = 60_000,
    /** Pages further apart than this are separate sittings. */
    val sessionGapMillis: Long = 5 * 60_000,
    /** A sitting with less reading than this was a glance, and is left out altogether. */
    val minSessionMillis: Long = 60_000,
    /** How many pages it takes to measure the reader's own speed. */
    val speedPages: Int = 20,
    /** Speed is measured from pages read from this time on (milliseconds since the epoch), so it can be measured afresh. */
    val speedSince: Long = 0,
)

data class DayStats(val date: LocalDate, val millis: Long, val pages: Int, val words: Int)

data class BookStats(val bookId: String, val millis: Long, val pages: Int, val words: Int, val lastReadAt: Long)

/** Reading, day by day and book by book, as the rules count it. */
class ReadingSummary(
    val days: Map<LocalDate, DayStats>,
    val books: Map<String, BookStats>,
    val wordsPerMinute: Double,
    /** How many pages have measured the speed; until there are enough, it is the default. */
    val speedPages: Int,
    val speedMeasured: Boolean,
) {
    fun minutesOn(date: LocalDate): Long = (days[date]?.millis ?: 0) / 60_000

    /**
     * Days read in a row up to [today]. Today not read yet does not break it (the day is not
     * over); [longest] is the longest run ever.
     */
    fun streak(today: LocalDate): Streak {
        var current = 0
        var day = if (today in days) today else today.minusDays(1)
        while (day in days) {
            current++
            day = day.minusDays(1)
        }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in days.keys.sorted()) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            longest = max(longest, run)
            previous = date
        }
        return Streak(current, longest)
    }
}

data class Streak(val current: Int, val longest: Int)

object ReadingStats {
    fun summarize(views: List<PageView>, zone: ZoneId, rules: ReadingRules = ReadingRules()): ReadingSummary {
        val speeds = speeds(views, rules)
        val measured = speeds.size >= rules.speedPages
        val wpm = if (measured) speeds[speeds.size / 2] else rules.defaultWordsPerMinute
        val sorted = views.sortedBy { it.shownAt }

        // Sittings: page views of one book with no long gap between them.
        val sessions = mutableListOf<MutableList<PageView>>()
        for (view in sorted) {
            val last = sessions.lastOrNull()?.last()
            if (last == null || last.bookId != view.bookId || view.shownAt - last.hiddenAt > rules.sessionGapMillis) {
                sessions += mutableListOf(view)
            } else {
                sessions.last() += view
            }
        }

        val days = HashMap<LocalDate, DayStats>()
        val books = HashMap<String, BookStats>()
        for (session in sessions) {
            val counted = session.mapNotNull { view -> countedMillis(view, wpm, rules)?.let { view to it } }
            if (counted.sumOf { it.second } < rules.minSessionMillis) continue
            for ((view, millis) in counted) {
                val date = Instant.ofEpochMilli(view.shownAt).atZone(zone).toLocalDate()
                val day = days[date] ?: DayStats(date, 0, 0, 0)
                days[date] = day.copy(millis = day.millis + millis, pages = day.pages + 1, words = day.words + view.words)
                val book = books[view.bookId] ?: BookStats(view.bookId, 0, 0, 0, 0)
                books[view.bookId] = book.copy(
                    millis = book.millis + millis,
                    pages = book.pages + 1,
                    words = book.words + view.words,
                    lastReadAt = max(book.lastReadAt, view.hiddenAt),
                )
            }
        }
        return ReadingSummary(days, books, wpm, speeds.size, measured)
    }

    /** How much of [view] counts as reading, or null if it was only glanced at. */
    internal fun countedMillis(view: PageView, wordsPerMinute: Double, rules: ReadingRules): Long? {
        val onScreen = max(0, view.hiddenAt - view.shownAt)
        val readingTime = view.words / wordsPerMinute * 60_000
        if (onScreen < max(rules.minShareOfPage * readingTime, rules.minPageMillis.toDouble())) return null
        return min(onScreen, max(rules.capTimesPage * readingTime, rules.minCapMillis.toDouble()).toLong())
    }

    /**
     * The speeds the reader's own is the median of: pages turned forward with a fair amount of
     * text, leaving out ones barely seen or left open. Slowest first.
     */
    internal fun speeds(views: List<PageView>, rules: ReadingRules): List<Double> = views
        .filter { it.arrivedBy == "next" && it.words >= 100 && it.shownAt >= rules.speedSince }
        .map { it.words / (max(1, it.hiddenAt - it.shownAt) / 60_000.0) }
        .filter { it in 60.0..1_000.0 }
        .sorted()

    /** The heatmap's shade for a day: 0 for none, then up to 15 minutes, 30, an hour, and more. */
    fun level(minutes: Long): Int = when {
        minutes <= 0 -> 0
        minutes < 15 -> 1
        minutes < 30 -> 2
        minutes < 60 -> 3
        else -> 4
    }
}
