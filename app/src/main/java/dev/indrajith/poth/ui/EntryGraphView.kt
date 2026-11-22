package dev.indrajith.poth.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import dev.indrajith.poth.R
import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.dictionary.EntryGraph
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A place whose entries make one graph: a shelf, with its books' entries too, or a book on no shelf. */
class GraphPlace(val key: String, val name: String, val entries: List<Entry>)

/**
 * The dictionary's links as a graph, one place at a time (chosen by the chips at the top when
 * there is more than one), with or without the entries that link nowhere. Tapping an entry opens
 * its card ([onOpen]), which leaves the graph usable above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(places: List<GraphPlace>, focused: Set<Long>, onOpen: (Long) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    var placeKey by rememberSaveable { mutableStateOf<String?>(null) }
    // The fullest place to start with.
    val place = places.firstOrNull { it.key == placeKey } ?: places.maxByOrNull { it.entries.size }
    var unlinked by rememberSaveable { mutableStateOf(true) }
    val camera = remember(place?.key) { GraphCamera() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (places.size == 1) place!!.name else "Graph") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                },
                actions = {
                    if (camera.moved) TextButton(onClick = { scope.launch { camera.showAll() } }) { Text("Show all") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (places.size > 1) {
                    for (each in places) {
                        FilterChip(
                            selected = each == place,
                            onClick = {
                                placeKey = each.key
                                onClear()
                            },
                            label = { Text(each.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                FilterChip(
                    selected = unlinked,
                    onClick = { unlinked = !unlinked },
                    label = { Text("Unlinked") },
                    leadingIcon = if (unlinked) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
            if (place != null) {
                key(place.key) {
                    EntryGraphView(place.entries, unlinked, camera, focused, onOpen, onClear, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** How the graph is zoomed and moved: zoom 1 and no pan show all of it. */
@Stable
class GraphCamera {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    val moved get() = zoom != 1f || pan != Offset.Zero

    suspend fun showAll() {
        val fromZoom = zoom
        val fromPan = pan
        animate(0f, 1f, animationSpec = tween(250)) { t, _ ->
            zoom = lerp(fromZoom, 1f, t)
            pan = lerp(fromPan, Offset.Zero, t)
        }
    }
}

/** Where the layout's points land on a canvas of [size]: all of it fitted inside [padding], then [camera]'s zoom and pan. */
private class Projection(bounds: Rect, private val size: Size, padding: Float, camera: GraphCamera) {
    private val middle = bounds.center
    private val pan = camera.pan

    /** Pixels to a unit of the layout. */
    val scale = fitScale(bounds, size, padding) * camera.zoom

    fun at(point: EntryGraph.Point) = Offset(
        size.width / 2 + pan.x + (point.x - middle.x) * scale,
        size.height / 2 + pan.y + (point.y - middle.y) * scale,
    )
}

private fun fitScale(bounds: Rect, size: Size, padding: Float) =
    min((size.width - 2 * padding) / max(bounds.width, 1f), (size.height - 2 * padding) / max(bounds.height, 1f)).coerceAtLeast(1f)

/** Zoomed in this far, a unit of the layout takes this much of the screen; no further. */
private val MostZoomed = 160.dp

/** A unit takes this much of the screen before every entry is named. */
private val NamedZoom = 56.dp

/**
 * The entries as a graph of their [[links]] ([EntryGraph]): pinch to zoom, drag to look
 * around. Tapping an entry opens it ([onOpen]); the entries [focused] (those on the card) stand
 * out with the ones they link with, while the rest fade back, and the graph moves to keep them
 * above the card. Tapping nothing lets them go ([onClear]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EntryGraphView(
    entries: List<Entry>,
    unlinked: Boolean,
    camera: GraphCamera,
    focused: Set<Long>,
    onOpen: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    // Kept while an edited dictionary is laid out again, so the graph doesn't blink.
    val layout = produceState<EntryGraph.Layout?>(null, entries, unlinked) {
        value = withContext(Dispatchers.Default) { EntryGraph.layout(entries, unlinked) }
    }.value
    if (layout == null) {
        Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    val bounds = remember(layout) {
        val points = layout.points.values
        if (points.isEmpty()) Rect(-1f, -1f, 1f, 1f) else Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
    val padding = with(LocalDensity.current) { 32.dp.toPx() }
    var canvas by remember { mutableStateOf(Size.Zero) }
    val open by rememberUpdatedState(onOpen)
    val clear by rememberUpdatedState(onClear)

    // The entry on the card, moved into view above it if it's out of sight or under the card.
    val target = focused.firstOrNull { it in layout.points }
    LaunchedEffect(target, layout) {
        val point = layout.points[target] ?: return@LaunchedEffect
        if (canvas == Size.Zero) return@LaunchedEffect
        val at = Projection(bounds, canvas, padding, camera).at(point)
        val clearOfCard = Rect(padding, padding, canvas.width - padding, canvas.height * 0.35f)
        if (clearOfCard.contains(at)) return@LaunchedEffect
        val from = camera.pan
        val to = from + Offset(canvas.width / 2, canvas.height * 0.2f) - at
        animate(0f, 1f, animationSpec = tween(300)) { t, _ -> camera.pan = lerp(from, to, t) }
    }

    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val names = remember(entries) { entries.associate { it.id to it.headword } }
    val scopes = remember(entries) { entries.associate { it.id to it.scope } }
    val labels = remember(entries, labelStyle) { HashMap<Long, TextLayoutResult>() }
    // The most linked entries are named even zoomed out.
    val hub = remember(layout) { layout.points.keys.map(layout::degree).sortedDescending().getOrNull(9)?.coerceAtLeast(3) ?: 3 }
    val lit = if (focused.isEmpty()) null else focused + focused.flatMap { layout.neighbours[it].orEmpty() }

    Canvas(
        modifier
            .onSizeChanged { canvas = it.toSize() }
            .pointerInput(layout) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val area = size.toSize()
                    val most = max(1f, MostZoomed.toPx() / fitScale(bounds, area, padding))
                    val zoomed = (camera.zoom * zoom).coerceIn(1f, most)
                    // The point under the fingers stays under them.
                    val middle = Offset(area.width / 2, area.height / 2)
                    camera.pan = (centroid - middle) - (centroid - middle - camera.pan) * (zoomed / camera.zoom) + pan
                    camera.zoom = zoomed
                }
            }
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val projection = Projection(bounds, size.toSize(), padding, camera)
                    val reach = 24.dp.toPx()
                    val hit = layout.points
                        .map { (id, point) -> id to projection.at(point).let { hypot(it.x - tap.x, it.y - tap.y) } }
                        .filter { it.second <= reach }
                        .minByOrNull { it.second }
                    if (hit != null) open(hit.first) else clear()
                }
            }
            .semantics { contentDescription = "Links between ${layout.points.size} entries. Tap an entry to open it." },
    ) {
        val projection = Projection(bounds, size, padding, camera)
        val at = layout.points.mapValues { projection.at(it.value) }
        val line = 1.dp.toPx()
        for (edge in layout.edges) {
            val a = at[edge.a] ?: continue
            val b = at[edge.b] ?: continue
            val color = when {
                lit == null -> colors.outlineVariant
                edge.a in focused || edge.b in focused -> colors.primary.copy(alpha = 0.8f)
                else -> colors.outlineVariant.copy(alpha = 0.3f)
            }
            drawLine(color, a, b, strokeWidth = line)
        }
        val everyName = projection.scale >= NamedZoom.toPx()
        val labelWidth = 120.dp.roundToPx()
        for ((id, point) in at) {
            val degree = layout.degree(id)
            val radius = (3.dp + 1.5.dp * sqrt(degree.toFloat())).toPx()
            val faded = lit != null && id !in lit
            val color = when {
                degree == 0 -> colors.outline
                scopes[id] == Scope.BOOK -> colors.tertiary
                else -> colors.primary
            }
            if (id in focused) drawCircle(colors.primary, radius + 4.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            drawCircle(color, radius, point, alpha = if (faded) 0.25f else 1f)
            val named = if (lit != null) id in lit else everyName || degree >= hub
            if (!named || point.x < -labelWidth || point.x > size.width + labelWidth || point.y < -radius - 40 || point.y > size.height + 40) continue
            val label = labels.getOrPut(id) {
                measurer.measure(
                    names[id].orEmpty(),
                    labelStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = labelWidth),
                )
            }
            drawText(
                label,
                color = if (lit != null) colors.onSurface else colors.onSurfaceVariant,
                topLeft = Offset(point.x - label.size.width / 2f, point.y + radius + 2.dp.toPx()),
            )
        }
    }
}
