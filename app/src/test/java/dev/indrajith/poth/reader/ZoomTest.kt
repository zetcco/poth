package dev.indrajith.poth.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomTest {
    private val screen = Size(1000f, 2000f)

    private fun onScreen(zoom: Zoom, point: Offset) = point * zoom.scale + zoom.pan
    private fun onPage(zoom: Zoom, point: Offset) = (point - zoom.pan) / zoom.scale

    @Test
    fun thePointUnderTheFingersStaysThere() {
        val fingers = Offset(300f, 700f)
        val once = Zoom().by(2f, Offset.Zero, fingers, screen)
        assertEquals(2f, once.scale)
        assertEquals(fingers, onScreen(once, fingers))

        val elsewhere = Offset(600f, 1200f)
        val pagePoint = onPage(once, elsewhere)
        val twice = once.by(1.5f, Offset.Zero, elsewhere, screen)
        assertEquals(3f, twice.scale)
        val after = onScreen(twice, pagePoint)
        assertEquals(elsewhere.x, after.x, 0.01f)
        assertEquals(elsewhere.y, after.y, 0.01f)
    }

    @Test
    fun scaleStaysBetweenOneAndTheMaximum() {
        assertEquals(Zoom(), Zoom().by(0.5f, Offset.Zero, Offset(500f, 1000f), screen))
        assertEquals(Zoom.MAX, Zoom().by(20f, Offset.Zero, Offset(500f, 1000f), screen).scale)
    }

    @Test
    fun thePageAlwaysCoversTheScreen() {
        val zoomed = Zoom().by(2f, Offset.Zero, Offset(500f, 1000f), screen) // pan (-500, -1000)
        assertEquals(Offset(-500f, -1000f), zoomed.pan)
        assertEquals(Offset(0f, 0f), zoomed.by(1f, Offset(900f, 3000f), Offset.Zero, screen).pan)
        assertEquals(Offset(-1000f, -2000f), zoomed.by(1f, Offset(-900f, -3000f), Offset.Zero, screen).pan)
    }

    @Test
    fun lettingGoJustAboveOneSnapsBack() {
        val barely = Zoom().by(1.03f, Offset.Zero, Offset(500f, 1000f), screen)
        assertTrue(barely.zoomed)
        assertFalse(barely.settled().zoomed)
        assertEquals(Zoom(), barely.settled())
        val zoomed = Zoom().by(2f, Offset.Zero, Offset(500f, 1000f), screen)
        assertEquals(zoomed, zoomed.settled())
        assertEquals(Zoom(), zoomed.towardsNone(1f))
    }
}
