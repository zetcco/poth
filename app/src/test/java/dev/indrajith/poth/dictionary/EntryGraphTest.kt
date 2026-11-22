package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Scope
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryGraphTest {
    private val henry = Entry(1, "Sir Henry Baskerville", aliases = listOf("Sir Henry"), body = "Heir to the Hall.", scope = Scope.BOOK)
    private val mortimer = Entry(2, "Mortimer", body = "Looks after [[Sir Henry]], and [[Mortimer]] again.", scope = Scope.BOOK)
    private val holmes = Entry(3, "Holmes", body = "Sends Watson with [[Sir Henry Baskerville|him]]; knows [[Mortimer]].", scope = Scope.BOOK)
    private val watson = Entry(4, "Watson", body = "Writes to [[Holmes]] about [[Lestrade]].", scope = Scope.BOOK)
    private val barrymore = Entry(5, "Barrymore", body = "The butler.", scope = Scope.BOOK)
    private val selden = Entry(6, "Selden", body = "The convict.", scope = Scope.BOOK)

    @Test
    fun aLineForEachPairThatLinks() {
        val both = holmes.copy(body = holmes.body + " Trusts [[Watson]].")
        assertEquals(
            listOf(EntryGraph.Edge(1, 2), EntryGraph.Edge(1, 3), EntryGraph.Edge(2, 3), EntryGraph.Edge(3, 4)),
            EntryGraph.edges(listOf(henry, mortimer, both, watson)),
        )
        // A link to an entry that isn't drawn (Holmes) draws nothing.
        assertEquals(emptyList<EntryGraph.Edge>(), EntryGraph.edges(listOf(watson, barrymore)))
    }

    @Test
    fun linkedEntriesSettleCloserThanOthers() {
        val layout = EntryGraph.layout(listOf(henry, mortimer, holmes, watson, barrymore, selden), unlinked = true)
        fun distance(a: Long, b: Long): Float {
            val p = layout.points.getValue(a)
            val q = layout.points.getValue(b)
            return hypot(p.x - q.x, p.y - q.y)
        }
        assertEquals(6, layout.points.size)
        assertTrue(distance(3, 4) < distance(1, 4)) // Holmes is next to Watson; Sir Henry two links away
        layout.points.values.forEach { assertFalse(it.x.isNaN() || it.y.isNaN()) }
        // The two that link nowhere are around the rest.
        val farthestLinked = (1L..4L).maxOf { layout.points.getValue(it).let { p -> hypot(p.x, p.y) } }
        for (id in listOf(5L, 6L)) assertTrue(layout.points.getValue(id).let { hypot(it.x, it.y) } > farthestLinked)
        assertEquals(setOf(1L, 2L, 4L), layout.neighbours[3L])
        assertEquals(0, layout.degree(5))
    }

    @Test
    fun theSameEntriesComeOutTheSame() {
        val entries = listOf(henry, mortimer, holmes, watson, barrymore)
        val first = EntryGraph.layout(entries, unlinked = true).points
        assertEquals(first, EntryGraph.layout(entries.reversed(), unlinked = true).points)
        assertEquals(setOf(1L, 2L, 3L, 4L), EntryGraph.layout(entries, unlinked = false).points.keys)
    }
}
