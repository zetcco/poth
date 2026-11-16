package dev.indrajith.poth.reader

import kotlin.math.abs

/**
 * How a one-finger swipe on the page is read. A swipe goes the way it mostly moves: one that is
 * more up or down than sideways turns no page. One that starts at a side edge goes back.
 */
object Swipe {
    /** Whether a move of [dx], [dy] is more sideways than up or down. */
    fun sideways(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)

    /**
     * Where a page swipe ends: on to the next page (true), the previous one (false), or back where
     * it was (null). [dx], [dy] is the whole swipe, [vx], [vy] its speed as the finger lifted,
     * [offset] how far the page has moved and [width] the page's width.
     * A quick flick turns the page, and so does a slow swipe past a quarter of the page. A swipe
     * that ended up more up or down than sideways turns nothing, and neither does a flick
     * that goes more up or down at the end.
     */
    fun turn(dx: Float, dy: Float, vx: Float, vy: Float, offset: Float, width: Float, flingSpeed: Float): Boolean? {
        if (!sideways(dx, dy)) return null
        val v = if (sideways(vx, vy)) vx else 0f
        return when {
            v < -flingSpeed || (v <= 0 && offset < -width / 4) -> true
            v > flingSpeed || (v >= 0 && offset > width / 4) -> false
            else -> null
        }
    }

    /**
     * Whether a swipe from a side edge goes back: once it has come [distance] in from the edge
     * ([inward]), more sideways than up or down ([dy]).
     */
    fun back(inward: Float, dy: Float, distance: Float): Boolean = inward >= distance && sideways(inward, dy)
}
