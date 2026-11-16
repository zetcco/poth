package dev.indrajith.poth.reader

import dev.indrajith.poth.data.PageView

/** How a page came onto the screen; stored with each page view. */
object Arrival {
    const val OPEN = "open"         // the book was opened at it
    const val NEXT = "next"         // turned forward to it
    const val PREVIOUS = "previous" // turned back to it
    const val JUMP = "jump"         // the contents, the scrubber, a link
    const val RESUME = "resume"     // the app came back to the front on it
    const val RELAYOUT = "relayout" // the same place, laid out again (rotation, settings)
}

/**
 * Times the page on screen and hands over a [PageView] when it goes: turned away from, the app
 * sent to the back, or the book closed. No judgement here about what counts as reading.
 */
class PageClock(
    private val bookId: String,
    private val record: (PageView) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Shown(val spine: Int, val start: Int, val end: Int, val words: Int, val arrivedBy: String, val at: Long)

    private var shown: Shown? = null
    private var paused: Shown? = null

    fun show(spine: Int, start: Int, end: Int, words: Int, arrivedBy: String) {
        stop()
        paused = null
        shown = Shown(spine, start, end, words, arrivedBy, now())
    }

    /** The app went to the back: the page view ends, and [resume] starts a new one on the same page. */
    fun pause() {
        val current = shown ?: return
        stop()
        paused = current
    }

    fun resume() {
        val page = paused ?: return
        paused = null
        shown = Shown(page.spine, page.start, page.end, page.words, Arrival.RESUME, now())
    }

    /** Ends the current page view, if there is one. */
    fun stop() {
        val page = shown ?: return
        shown = null
        record(PageView(bookId, page.at, now(), page.spine, page.start, page.end, page.words, page.arrivedBy))
    }
}
