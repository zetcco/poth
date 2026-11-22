package dev.indrajith.poth.dictionary

import dev.indrajith.poth.data.Entry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The entries of one place as a web: a point for each entry and a line for each [[link]]
 * between two of them, laid out so that linked entries sit together. Plain Kotlin: the layout
 * is worked out once, off the main thread, and the screen only draws it.
 */
object EntryGraph {
    /** Entries [a] and [b] (the smaller ID first) link to each other, one way or both. */
    data class Edge(val a: Long, val b: Long)

    /** Where an entry's point goes. One unit is about a line's length between two linked entries. */
    data class Point(val x: Float, val y: Float)

    class Layout(val points: Map<Long, Point>, val edges: List<Edge>) {
        /** For each entry, the ones it links to or is linked from. */
        val neighbours: Map<Long, Set<Long>> = HashMap<Long, MutableSet<Long>>().also { map ->
            for (edge in edges) {
                map.getOrPut(edge.a) { mutableSetOf() } += edge.b
                map.getOrPut(edge.b) { mutableSetOf() } += edge.a
            }
        }

        fun degree(id: Long) = neighbours[id]?.size ?: 0
    }

    /** The links among [entries], each pair once whichever way it goes; links to other entries are left out. */
    fun edges(entries: List<Entry>): List<Edge> =
        EntryLinks.links(entries)
            .flatMap { (from, to) -> to.map { Edge(min(from, it), max(from, it)) } }
            .distinct()
            .sortedWith(compareBy({ it.a }, { it.b }))

    /**
     * Lays [entries] out: the linked ones by forces, each line pulling its ends together and
     * every point pushing the others away, until they settle; with [unlinked], the entries
     * that link nowhere go in a ring around them, in alphabetical order. The same entries always
     * come out the same.
     */
    fun layout(entries: List<Entry>, unlinked: Boolean): Layout {
        val edges = edges(entries)
        val linked = edges.flatMap { listOf(it.a, it.b) }.toSortedSet().toList()
        val points = settle(linked, edges)
        val rest = if (unlinked) entries.filter { it.id !in points }.sortedBy { it.headword.lowercase() }.map { it.id } else emptyList()
        return Layout(points + ring(rest, points.values), edges)
    }

    private const val STEPS = 300

    // Pulls every point towards the middle, so groups that don't link to the rest stay near it.
    private const val GRAVITY = 1.0

    private val GOLDEN_ANGLE = PI * (3 - sqrt(5.0))

    /** Fruchterman and Reingold's forces, cooling so that points move less each step. */
    private fun settle(ids: List<Long>, edges: List<Edge>): Map<Long, Point> {
        val n = ids.size
        if (n == 0) return emptyMap()
        val index = ids.withIndex().associate { (i, id) -> id to i }
        val links = edges.map { index.getValue(it.a) to index.getValue(it.b) }
        // Starting on a sunflower's spiral: spread out evenly, and the same every time.
        val x = DoubleArray(n) { sqrt(it + 0.5) * cos(it * GOLDEN_ANGLE) }
        val y = DoubleArray(n) { sqrt(it + 0.5) * sin(it * GOLDEN_ANGLE) }
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        val hottest = sqrt(n.toDouble())
        for (step in 0 until STEPS) {
            dx.fill(0.0)
            dy.fill(0.0)
            // Apart: 1/d for every pair.
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var ex = x[i] - x[j]
                    var ey = y[i] - y[j]
                    var d2 = ex * ex + ey * ey
                    if (d2 < 1e-4) {
                        ex = 0.01
                        ey = 0.01 * (j - i)
                        d2 = ex * ex + ey * ey
                    }
                    dx[i] += ex / d2
                    dy[i] += ey / d2
                    dx[j] -= ex / d2
                    dy[j] -= ey / d2
                }
            }
            // Together: d² along each line, so linked points settle about one unit apart.
            for ((i, j) in links) {
                val ex = x[i] - x[j]
                val ey = y[i] - y[j]
                val d = hypot(ex, ey)
                dx[i] -= ex * d
                dy[i] -= ey * d
                dx[j] += ex * d
                dy[j] += ey * d
            }
            val heat = hottest * (1 - step.toDouble() / STEPS)
            for (i in 0 until n) {
                dx[i] -= GRAVITY * x[i]
                dy[i] -= GRAVITY * y[i]
                val d = hypot(dx[i], dy[i])
                if (d > 0) {
                    val move = min(d, heat)
                    x[i] += dx[i] / d * move
                    y[i] += dy[i] / d * move
                }
            }
        }
        return ids.withIndex().associate { (i, id) -> id to Point(x[i].toFloat(), y[i].toFloat()) }
    }

    private const val RING_GAP = 1.2f

    /** [ids] in rings around [inside], from the top clockwise, an outer ring once one is full. */
    private fun ring(ids: List<Long>, inside: Collection<Point>): Map<Long, Point> {
        val points = HashMap<Long, Point>()
        var radius = (inside.maxOfOrNull { hypot(it.x, it.y) } ?: 0f) + 1.5f
        var placed = 0
        while (placed < ids.size) {
            val fits = max(1, (2 * PI * radius / RING_GAP).toInt())
            val these = ids.subList(placed, min(ids.size, placed + fits))
            for ((i, id) in these.withIndex()) {
                val angle = 2 * PI * i / these.size - PI / 2
                points[id] = Point((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
            }
            placed += these.size
            radius += RING_GAP
        }
        return points
    }
}
