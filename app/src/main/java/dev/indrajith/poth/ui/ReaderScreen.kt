package dev.indrajith.poth.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.indrajith.poth.Graph
import dev.indrajith.poth.data.Position
import dev.indrajith.poth.epub.EpubBook
import dev.indrajith.poth.epub.EpubException
import dev.indrajith.poth.reader.Arrival
import dev.indrajith.poth.reader.OpenBook
import dev.indrajith.poth.reader.PageClock
import dev.indrajith.poth.reader.PageSpec
import dev.indrajith.poth.reader.Pages
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.indrajith.poth.reader.Page as BookPage

private sealed interface Opening {
    data object Loading : Opening
    class Ready(val book: OpenBook, val position: Position) : Opening
    data class Failed(val reason: String) : Opening
}

/** The reader: one page at a time, turned by swiping. Where reading stops is remembered. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(graph: Graph, bookId: String) {
    val opening by produceState<Opening>(Opening.Loading, bookId) {
        val result = withContext(Dispatchers.IO) {
            try {
                val book = OpenBook(EpubBook.open(graph.files.of(bookId)))
                graph.books.markOpened(bookId, System.currentTimeMillis())
                Opening.Ready(book, graph.books.position(bookId))
            } catch (e: EpubException) {
                Opening.Failed("Can't read this book: ${e.message}")
            } catch (e: IOException) {
                Opening.Failed("Can't open this book: ${e.message}")
            }
        }
        value = result
        awaitDispose { (result as? Opening.Ready)?.book?.close() }
    }

    HideStatusBar()
    Box(Modifier.fillMaxSize().background(Page.background), contentAlignment = Alignment.Center) {
        when (val state = opening) {
            Opening.Loading -> LoadingIndicator()
            is Opening.Failed -> Text(
                state.reason,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
            is Opening.Ready -> Reader(graph, bookId, state.book, state.position)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reader(graph: Graph, bookId: String, book: OpenBook, start: Position) {
    var position by remember { mutableStateOf(start) }
    var laidOutBefore by remember { mutableStateOf(false) }
    val clock = remember(bookId) {
        PageClock(bookId, record = { view -> graph.scope.launch(Dispatchers.IO) { graph.log.add(view) } })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { clock.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { clock.resume() }
    DisposableEffect(clock) { onDispose { clock.stop() } }

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // The status bar is hidden while reading, but the camera cutout is still there.
    val top = max(WindowInsets.statusBarsIgnoringVisibility.getTop(density), WindowInsets.displayCutout.getTop(density)) +
        with(density) { 6.dp.roundToPx() }
    val bottom = WindowInsets.navigationBars.getBottom(density) + with(density) { 10.dp.roundToPx() }
    val side = with(density) { 28.dp.roundToPx() }
    val left = side + WindowInsets.displayCutout.getLeft(density, direction)
    val right = side + WindowInsets.displayCutout.getRight(density, direction)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth - left - right
        val height = constraints.maxHeight - top - bottom
        val spec = remember(width, height, density) {
            PageSpec(
                width = width,
                height = height,
                fontPx = with(density) { 20.sp.toPx() },
                lineHeight = 1.2f,
                textColor = Page.text.toArgb(),
                imageScale = density.density,
            )
        }
        val pages = remember(book, spec) { Pages(book, spec) }
        PageTurner(
            pages = pages,
            from = position,
            arrivedBy = if (laidOutBefore) Arrival.RELAYOUT else Arrival.OPEN,
            pageLeft = left.toFloat(),
            pageTop = top.toFloat(),
            onShow = { page, arrivedBy ->
                laidOutBefore = true
                position = Position(page.spine, page.start)
                clock.show(page.spine, page.start, page.end, page.words, arrivedBy)
                graph.scope.launch(Dispatchers.IO) { graph.books.savePosition(bookId, Position(page.spine, page.start)) }
            },
        )
    }
}

private class Shown(val page: BookPage, val arrivedBy: String)

/**
 * Shows [pages] from [from] on, a page at a time. A swipe slides the next or previous page in;
 * a short or slow swipe springs back. [onShow] hears about every page that settles on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageTurner(
    pages: Pages,
    from: Position,
    arrivedBy: String,
    pageLeft: Float,
    pageTop: Float,
    onShow: (BookPage, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var shown by remember(pages) { mutableStateOf<Shown?>(null) }
    var next by remember(pages) { mutableStateOf<BookPage?>(null) }
    var previous by remember(pages) { mutableStateOf<BookPage?>(null) }
    val offset = remember(pages) { Animatable(0f) }
    val flingSpeed = with(LocalDensity.current) { 400.dp.toPx() } // per second

    LaunchedEffect(pages) {
        pages.at(from)?.let { shown = Shown(it, arrivedBy) }
    }
    LaunchedEffect(shown) {
        val current = shown ?: return@LaunchedEffect
        onShow(current.page, current.arrivedBy)
        next = null
        previous = null
        next = pages.next(current.page)
        previous = pages.previous(current.page)
        pages.prepareAround(current.page.spine)
    }

    if (shown == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()

        suspend fun settle(forward: Boolean?) {
            val target = when (forward) {
                true -> next
                false -> previous
                null -> null
            }
            if (target == null) {
                offset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                return
            }
            offset.animateTo(if (forward == true) -width else width, tween(220, easing = FastOutSlowInEasing))
            shown = Shown(target, if (forward == true) Arrival.NEXT else Arrival.PREVIOUS)
            offset.snapTo(0f)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(pages) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val forward = when {
                                velocity < -flingSpeed || (velocity <= 0 && offset.value < -width / 4) -> true
                                velocity > flingSpeed || (velocity >= 0 && offset.value > width / 4) -> false
                                else -> null
                            }
                            scope.launch { settle(forward) }
                        },
                        onDragCancel = { scope.launch { settle(null) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Past the first or last page it only gives a little.
                            val min = if (next != null) -width else -width / 10
                            val max = if (previous != null) width else width / 10
                            scope.launch { offset.snapTo((offset.value + dragAmount).coerceIn(min, max)) }
                        },
                    )
                },
        ) {
            val dx = offset.value
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                fun draw(page: BookPage?, shift: Float) {
                    if (page == null) return
                    native.save()
                    native.translate(pageLeft + shift, pageTop)
                    page.draw(native)
                    native.restore()
                }
                draw(shown?.page, dx)
                if (dx < 0) draw(next, dx + width) else if (dx > 0) draw(previous, dx - width)
            }
        }
    }
}

/** While reading, the status bar is hidden; a swipe down from the top shows it for a moment. */
@Composable
private fun HideStatusBar() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}
