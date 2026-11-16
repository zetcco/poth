package dev.indrajith.poth.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * The page magnified like a zoomed web page, without laying it out again: a point on the page is
 * drawn at point × [scale] + [pan]. The page always covers the screen.
 */
data class Zoom(val scale: Float = 1f, val pan: Offset = Offset.Zero) {
    val zoomed get() = scale > 1f

    /** Zoomed by [factor] about [centroid] (a point on the screen), then moved by [move]. */
    fun by(factor: Float, move: Offset, centroid: Offset, screen: Size): Zoom {
        val newScale = (scale * factor).coerceIn(1f, MAX)
        // The point under the fingers stays under them.
        val moved = centroid - (centroid - pan) * (newScale / scale) + move
        return Zoom(
            newScale,
            Offset(moved.x.coerceIn(screen.width * (1 - newScale), 0f), moved.y.coerceIn(screen.height * (1 - newScale), 0f)),
        )
    }

    /** A pinch let go just above 1× is taken as 1×. */
    fun settled(): Zoom = if (scale < SNAP) Zoom() else this

    /** Part of the way from this zoom back to 1×, for animating out: [t] goes from 0 to 1. */
    fun towardsNone(t: Float) = if (t >= 1f) Zoom() else Zoom(scale + (1f - scale) * t, pan * (1f - t))

    companion object {
        const val MAX = 5f
        private const val SNAP = 1.05f
    }
}
