package dev.indrajith.poth.ui

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.actions.LookupContext
import dev.indrajith.poth.actions.QuickAction
import dev.indrajith.poth.actions.Templates
import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Origin
import dev.indrajith.poth.data.Position
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.data.Shelf
import dev.indrajith.poth.dictionary.EntryMarker
import dev.indrajith.poth.dictionary.Words
import dev.indrajith.poth.epub.EpubBook
import dev.indrajith.poth.epub.EpubException
import dev.indrajith.poth.reader.Arrival
import dev.indrajith.poth.reader.Marker
import dev.indrajith.poth.reader.OpenBook
import dev.indrajith.poth.reader.PageClock
import dev.indrajith.poth.reader.PageMap
import dev.indrajith.poth.reader.PageSpec
import dev.indrajith.poth.reader.Pages
import dev.indrajith.poth.reader.Section
import dev.indrajith.poth.reader.Zoom
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

/**
 * The reader: one page at a time, turned by swiping. A tap shows the bars (and the system's);
 * another hides them. Where reading stops is remembered.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(graph: Graph, bookId: String, onBack: () -> Unit) {
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

    Box(Modifier.fillMaxSize().background(Page.background), contentAlignment = Alignment.Center) {
        when (val state = opening) {
            Opening.Loading -> LoadingIndicator()
            is Opening.Failed -> Text(
                state.reason,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
            is Opening.Ready -> Reader(graph, bookId, state.book, state.position, onBack)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reader(graph: Graph, bookId: String, book: OpenBook, start: Position, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var position by remember { mutableStateOf(start) }
    var laidOutBefore by remember { mutableStateOf(false) }
    var barsVisible by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var rotationLocked by remember { mutableStateOf(graph.prefs.rotationLocked) }
    val clock = remember(bookId) {
        PageClock(bookId, record = { view -> graph.scope.launch(Dispatchers.IO) { graph.log.add(view) } })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { clock.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { clock.resume() }
    DisposableEffect(clock) { onDispose { clock.stop() } }

    // The dictionary as this book sees it: its own entries, its shelf's, the library's.
    val dictionary = graph.dictionary
    val changes by dictionary.changes.collectAsStateWithLifecycle()
    val entries by produceState(emptyList<Entry>(), changes) { value = withContext(Dispatchers.IO) { dictionary.entriesFor(bookId) } }
    val shelf by produceState<Shelf?>(null, changes) { value = withContext(Dispatchers.IO) { dictionary.shelvesOf(bookId).firstOrNull() } }
    val suggestedShelf = remember(bookId) { Words.suggestShelf(book.title, book.series) }
    val marker = remember(entries) { EntryMarker(entries) }
    var cardIds by remember { mutableStateOf(emptyList<Long>()) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    val haptics = LocalHapticFeedback.current

    // Quick actions hand the word (and the sentence it was read in) to other apps.
    val quickActions = graph.quickActions
    val actions by quickActions.actions.collectAsStateWithLifecycle()
    var configuringActions by remember { mutableStateOf(false) }
    var lastSentence by remember { mutableStateOf("") }
    val context = LocalContext.current

    SystemBars(visible = barsVisible || contentsOpen || editing != null || configuringActions)
    RotationLock(rotationLocked)

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // The system bars are hidden while reading, but the camera cutout is still there. Margins do
    // not follow the bars coming and going, so showing them does not lay the book out again.
    val top = max(WindowInsets.statusBarsIgnoringVisibility.getTop(density), WindowInsets.displayCutout.getTop(density)) +
        with(density) { 6.dp.roundToPx() }
    val bottom = with(density) { 16.dp.roundToPx() }
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
        val turner = remember(pages) { Turner(pages) }
        var jumpedFrom by remember(pages) { mutableStateOf<BookPage?>(null) }

        LaunchedEffect(pages) {
            pages.at(position)?.let { turner.show(it, if (laidOutBefore) Arrival.RELAYOUT else Arrival.OPEN) }
        }
        // "18 of 1789": counted once per layout in the background, then kept.
        val map by produceState<PageMap?>(null, pages) {
            val saved = withContext(Dispatchers.IO) { graph.pageCounts.get(bookId, pages.layoutKey) }
            val counts = saved?.takeIf { it.size == book.readingOrder.size } ?: pages.count().also { counts ->
                graph.scope.launch(Dispatchers.IO) { graph.pageCounts.put(bookId, pages.layoutKey, counts) }
            }
            value = pages.map(counts)
        }
        val current = turner.shown?.page
        val section by produceState<Section?>(null, current) { value = current?.let { pages.sectionOf(it) } }
        val chapterPages by produceState(0, current?.spine) { value = current?.let { pages.of(it.spine).size } ?: 0 }

        fun jump(to: suspend () -> BookPage?) {
            val from = current
            scope.launch {
                val target = to() ?: return@launch
                if (target === from) return@launch
                jumpedFrom = from
                turner.show(target, Arrival.JUMP)
            }
        }

        fun runAction(action: QuickAction, word: String) {
            val lookup = LookupContext(word, lastSentence, book.title, book.author, section?.title.orEmpty())
            if (!quickActions.launch(context, action, Templates.fill(action.template, lookup))) {
                Toast.makeText(context, "${action.label} isn't available", Toast.LENGTH_SHORT).show()
            }
        }

        PageTurner(
            turner = turner,
            marker = marker,
            pageLeft = left.toFloat(),
            pageTop = top.toFloat(),
            onTap = { page, x, y ->
                // An underlined word opens its entries; anywhere else shows or hides the bars.
                val hit = page.wordAt(x, y, marker)
                if (hit != null && hit.marks.isNotEmpty()) {
                    lastSentence = hit.sentence
                    cardIds = hit.marks.flatMap { it.entries }.distinct()
                } else {
                    barsVisible = !barsVisible
                    if (!barsVisible) jumpedFrom = null
                }
            },
            onLongPress = { page, x, y ->
                // Holding a word writes about it: its entry if it has one, a new one if not.
                val hit = page.wordAt(x, y, marker) ?: return@PageTurner
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                lastSentence = hit.sentence
                val existing = entries.firstOrNull { entry -> hit.marks.any { entry.id in it.entries } }
                if (existing != null) {
                    editing = existing
                } else {
                    scope.launch {
                        val lowercaseElsewhere = withContext(Dispatchers.Default) {
                            val word = Regex("\\b${Regex.escape(hit.word.lowercase())}\\b")
                            pages.of(page.spine).any { word.containsMatchIn(it.text()) }
                        }
                        val guess = Words.suggestScope(hit.word, hit.startsSentence, lowercaseElsewhere, shelf != null || suggestedShelf.isNotEmpty())
                        editing = Entry(headword = Words.headword(hit.word, guess), scope = guess)
                    }
                }
            },
            onShow = { page, arrivedBy ->
                laidOutBefore = true
                position = Position(page.spine, page.start)
                clock.show(page.spine, page.start, page.end, page.words, arrivedBy)
                graph.scope.launch(Dispatchers.IO) { graph.books.savePosition(bookId, Position(page.spine, page.start)) }
            },
        )

        val inChapter = current?.let { "page ${it.index + 1} of ${max(chapterPages, it.index + 1)}" }
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = book.title,
                author = book.author,
                section = listOfNotNull(section?.title, inChapter).joinToString(" · ").ifEmpty { null },
                onBack = onBack,
                onContents = { contentsOpen = true },
            )
        }
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val pageMap = map
            val number = current?.let { pageMap?.number(it.spine, it.index) }
            // Ticks for the top level of the contents (the parts), not every file in the book.
            val partStarts = remember(pageMap) {
                val top = book.contents.minOfOrNull { it.depth } ?: 0
                pageMap?.let { map -> book.contents.filter { it.depth == top }.map { map.number(it.spine, 0) }.distinct().sorted() }.orEmpty()
            }
            ReaderBottomBar(
                pageLabel = if (pageMap != null && number != null) "${number + 1} of ${pageMap.total}" else inChapter.orEmpty(),
                rotationLocked = rotationLocked,
                onRotation = {
                    rotationLocked = !rotationLocked
                    graph.prefs.rotationLocked = rotationLocked
                },
                scrubber = if (pageMap != null && number != null) {
                    ScrubberModel(
                        total = pageMap.total,
                        current = number,
                        ticks = partStarts,
                        label = { page ->
                            val (spine, _) = pageMap.locate(page)
                            val title = book.contents.lastOrNull { book.readingOrder.indexOf(it.spine) <= book.readingOrder.indexOf(spine) }?.title
                            listOfNotNull(title, "page ${page + 1}").joinToString(" · ")
                        },
                        onJump = { page ->
                            val (spine, index) = pageMap.locate(page)
                            jump { pages.of(spine).getOrNull(index) }
                        },
                    )
                } else {
                    null
                },
                back = jumpedFrom?.let { from ->
                    val fromNumber = pageMap?.number(from.spine, from.index)
                    if (fromNumber != null) "Back to page ${fromNumber + 1}" else "Back"
                },
                onBackClick = {
                    val target = jumpedFrom ?: return@ReaderBottomBar
                    jumpedFrom = null
                    scope.launch { turner.show(target, Arrival.JUMP) }
                },
            )
        }
        // Built once the first page is up, then kept, so opening it is only its slide.
        if (current != null) {
            val pageMap = map
            val pageOf = remember(pageMap) { { section: Section -> pageMap?.number(section.spine, 0) } }
            ContentsPanel(
                open = contentsOpen,
                contents = book.contents,
                current = section,
                pageOf = pageOf,
                onPick = { picked ->
                    contentsOpen = false
                    jump { pages.at(picked) }
                },
                onClose = { contentsOpen = false },
            )
        }

        EntryCard(
            entries = entries.filter { it.id in cardIds },
            scopeLabel = { entry ->
                when (entry.scope) {
                    Scope.BOOK -> "This book"
                    Scope.SHELF -> shelf?.name ?: "Shelf"
                    Scope.LIBRARY -> "Library"
                }
            },
            actions = actions,
            actionStore = quickActions,
            onAction = { action -> runAction(action, entries.firstOrNull { it.id in cardIds }?.headword.orEmpty()) },
            onConfigureActions = { configuringActions = true },
            onEdit = { entry ->
                cardIds = emptyList()
                editing = entry
            },
            onAddAnother = { entry ->
                cardIds = emptyList()
                val other = if (entry.scope == Scope.LIBRARY) (if (shelf != null || suggestedShelf.isNotEmpty()) Scope.SHELF else Scope.BOOK) else Scope.LIBRARY
                editing = Entry(headword = entry.headword, scope = other)
            },
            onEntryLink = { target ->
                val ids = marker.matcher.lookup(target)
                if (ids.isNotEmpty()) {
                    cardIds = ids
                } else {
                    cardIds = emptyList()
                    val guess = Words.suggestScope(target, startsSentence = false, appearsInLowercase = false, onShelf = shelf != null || suggestedShelf.isNotEmpty())
                    editing = Entry(headword = target, scope = guess)
                }
            },
            onClose = { cardIds = emptyList() },
        )
        EntryEditor(
            entry = editing,
            shelfName = shelf?.name,
            suggestedShelf = suggestedShelf,
            language = book.locale.language,
            actions = actions,
            actionStore = quickActions,
            onAction = ::runAction,
            onConfigureActions = { configuringActions = true },
            onSave = { edited, newShelf ->
                val original = editing
                editing = null
                val origin = Origin(bookId, position)
                graph.scope.launch(Dispatchers.IO) {
                    // A moved entry goes to this book, this book's shelf, or the library.
                    val scopeId = when {
                        original != null && original.id != 0L && edited.scope == original.scope -> original.scopeId
                        edited.scope == Scope.BOOK -> bookId
                        edited.scope == Scope.LIBRARY -> null
                        else -> (dictionary.shelvesOf(bookId).firstOrNull() ?: dictionary.putOnShelf(bookId, newShelf ?: suggestedShelf)).id.toString()
                    }
                    dictionary.save(edited.copy(scopeId = scopeId), origin)
                }
            },
            onDelete = { entry ->
                editing = null
                graph.scope.launch(Dispatchers.IO) { dictionary.delete(entry.id) }
            },
            onClose = { editing = null },
        )
        QuickActionsPanel(open = configuringActions, store = quickActions, onClose = { configuringActions = false })
    }
}

private class Shown(val page: BookPage, val arrivedBy: String)

/** The page on screen, its neighbours, and how far a swipe has moved them. */
@Stable
private class Turner(val pages: Pages) {
    var shown by mutableStateOf<Shown?>(null)
        private set
    var next by mutableStateOf<BookPage?>(null)
    var previous by mutableStateOf<BookPage?>(null)
    val offset = Animatable(0f)

    var zoom by mutableStateOf(Zoom())

    suspend fun show(page: BookPage, arrivedBy: String) {
        offset.snapTo(0f)
        zoom = Zoom()
        shown = Shown(page, arrivedBy)
    }

    suspend fun unzoom() {
        val from = zoom
        animate(0f, 1f, animationSpec = tween(250, easing = FastOutSlowInEasing)) { t, _ -> zoom = from.towardsNone(t) }
    }

    /** Ends a swipe: on to the next or previous page, or back to where it was ([forward] null). */
    suspend fun settle(forward: Boolean?, width: Float) {
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
        show(target, if (forward == true) Arrival.NEXT else Arrival.PREVIOUS)
    }
}

/**
 * Draws [turner]'s page and turns it by swiping: the next or previous page slides in, and a
 * short or slow swipe springs back. Two fingers zoom the page; while it is zoomed, one finger
 * moves it instead of turning, and a double tap or Back zooms out. Taps and long presses are
 * handed on with the point on the page, zoom taken into account.
 * [onShow] hears about every page that settles on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageTurner(
    turner: Turner,
    marker: Marker,
    pageLeft: Float,
    pageTop: Float,
    onTap: (page: BookPage, x: Float, y: Float) -> Unit,
    onLongPress: (page: BookPage, x: Float, y: Float) -> Unit,
    onShow: (BookPage, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flingSpeed = with(LocalDensity.current) { 400.dp.toPx() } // per second
    val shown = turner.shown
    // The gesture handlers below are set up once; these keep them calling the latest callbacks,
    // which see the latest dictionary.
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)

    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        onShow(shown.page, shown.arrivedBy)
        turner.next = null
        turner.previous = null
        turner.next = turner.pages.next(shown.page)
        turner.previous = turner.pages.previous(shown.page)
        turner.pages.prepareAround(shown.page.spine)
    }

    if (shown == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    BackHandler(enabled = turner.zoom.zoomed) { scope.launch { turner.unzoom() } }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(turner, turner.zoom.zoomed) {
                // Waiting for a second tap delays the first, so double tap is only on while zoomed.
                fun onPage(at: Offset, handle: (BookPage, Float, Float) -> Unit) {
                    val page = turner.shown?.page ?: return
                    val zoom = turner.zoom
                    handle(page, (at.x - zoom.pan.x) / zoom.scale - pageLeft, (at.y - zoom.pan.y) / zoom.scale - pageTop)
                }
                detectTapGestures(
                    onDoubleTap = if (turner.zoom.zoomed) { _ -> scope.launch { turner.unzoom() } } else null,
                    onLongPress = { onPage(it, longPress) },
                    onTap = { onPage(it, tap) },
                )
            }
            .pointerInput(turner) {
                val tracker = VelocityTracker()
                val width = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        val offset = turner.offset.value
                        val forward = when {
                            velocity < -flingSpeed || (velocity <= 0 && offset < -width / 4) -> true
                            velocity > flingSpeed || (velocity >= 0 && offset > width / 4) -> false
                            else -> null
                        }
                        scope.launch { turner.settle(forward, width) }
                    },
                    onDragCancel = { scope.launch { turner.settle(null, width) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // Past the first or last page it only gives a little.
                        val min = if (turner.next != null) -width else -width / 10
                        val max = if (turner.previous != null) width else width / 10
                        scope.launch { turner.offset.snapTo((turner.offset.value + dragAmount).coerceIn(min, max)) }
                    },
                )
            }
            // Last in the chain, so it sees moves first: what it takes for zooming and panning
            // never reaches the swipe above.
            .pointerInput(turner) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || turner.zoom.zoomed && fingers == 1) {
                            val factor = event.calculateZoom()
                            val move = event.calculatePan()
                            if (factor != 1f || move != Offset.Zero) {
                                turner.zoom = turner.zoom.by(factor, move, event.calculateCentroid(useCurrent = true), size.toSize())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    turner.zoom = turner.zoom.settled()
                }
            },
    ) {
        val dx = turner.offset.value
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // Drawn through the zoom rather than scaled afterwards, so text stays sharp.
            native.save()
            val zoom = turner.zoom
            native.translate(zoom.pan.x, zoom.pan.y)
            native.scale(zoom.scale, zoom.scale)
            fun draw(page: BookPage?, shift: Float) {
                if (page == null) return
                native.save()
                native.translate(pageLeft + shift, pageTop)
                page.draw(native, marker)
                native.restore()
            }
            draw(turner.shown?.page, dx)
            if (dx < 0) draw(turner.next, dx + size.width) else if (dx > 0) draw(turner.previous, dx - size.width)
            native.restore()
        }
    }
}

/**
 * The status bar and the navigation pill are hidden while reading and come back with the
 * reader's bars. A swipe from an edge still shows them for a moment.
 */
@Composable
private fun SystemBars(visible: Boolean) {
    val activity = LocalActivity.current ?: return
    val controller = remember(activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    SideEffect {
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(controller) { onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) } }
}

/** Keeps the screen in its current orientation while [locked]; the system decides otherwise. */
@Composable
private fun RotationLock(locked: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, locked) {
        activity.requestedOrientation = if (locked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
