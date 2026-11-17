package dev.indrajith.poth.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import dev.indrajith.poth.R
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
import dev.indrajith.poth.reader.Highlight
import dev.indrajith.poth.reader.Marker
import dev.indrajith.poth.reader.OpenBook
import dev.indrajith.poth.reader.PageClock
import dev.indrajith.poth.reader.PageMap
import dev.indrajith.poth.reader.PageSpec
import dev.indrajith.poth.reader.Pages
import dev.indrajith.poth.reader.Section
import dev.indrajith.poth.reader.Swipe
import dev.indrajith.poth.reader.WordRange
import dev.indrajith.poth.reader.Zoom
import dev.indrajith.poth.widget.ReadingWidget
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
fun ReaderScreen(graph: Graph, bookId: String, onBack: () -> Unit, onDictionary: () -> Unit) {
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
            is Opening.Ready -> Reader(graph, bookId, state.book, state.position, onBack, onDictionary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reader(graph: Graph, bookId: String, book: OpenBook, start: Position, onBack: () -> Unit, onDictionary: () -> Unit) {
    val scope = rememberCoroutineScope()
    var position by remember { mutableStateOf(start) }
    var laidOutBefore by remember { mutableStateOf(false) }
    var barsVisible by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var rotationLocked by remember { mutableStateOf(graph.prefs.rotationLocked) }
    // While tracking is paused, page views are dropped rather than stored.
    val trackingPaused by graph.prefs.trackingPaused.collectAsStateWithLifecycle()
    val clock = remember(bookId) {
        PageClock(bookId, record = { view ->
            if (!graph.prefs.trackingPaused.value) graph.scope.launch(Dispatchers.IO) { graph.log.add(view) }
        })
    }
    // The page view so far ends as it was (kept or not), and the rest of it goes the new way.
    fun pauseTracking(paused: Boolean) {
        clock.pause()
        graph.prefs.pauseTracking(paused)
        clock.resume()
    }
    var confirmingTracking by remember { mutableStateOf(false) }
    // When reading stops, the widget catches up (once the last page view is written).
    val appContext = LocalContext.current.applicationContext
    fun refreshWidget() = graph.scope.launch {
        delay(1_000)
        ReadingWidget.updateAll(appContext)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        clock.pause()
        refreshWidget()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { clock.resume() }
    DisposableEffect(clock) {
        onDispose {
            clock.stop()
            refreshWidget()
        }
    }

    // The dictionary as this book sees it: its own entries, its shelf's, the library's.
    val dictionary = graph.dictionary
    val changes by dictionary.changes.collectAsStateWithLifecycle()
    val shelfChanges by graph.shelves.changes.collectAsStateWithLifecycle()
    val entries by produceState(emptyList<Entry>(), changes, shelfChanges) {
        value = withContext(Dispatchers.IO) { dictionary.entriesFor(bookId) }
    }
    val shelf by produceState<Shelf?>(null, shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.of(bookId) } }
    val suggestedShelf = remember(bookId) { Words.suggestShelf(book.title, book.series) }
    val marker = remember(entries) { EntryMarker(entries) }
    val trail = remember { CardTrail() }
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
        // How far through the book, for the book list: once the pages are counted.
        LaunchedEffect(map, current) {
            val pageMap = map ?: return@LaunchedEffect
            val page = current ?: return@LaunchedEffect
            if (pageMap.total == 0) return@LaunchedEffect
            val progress = (pageMap.number(page.spine, page.index) + 1).toDouble() / pageMap.total
            graph.scope.launch(Dispatchers.IO) { graph.books.saveProgress(bookId, progress) }
        }
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
                // A tap drops a highlight first. Then an underlined word opens its entries;
                // anywhere else shows or hides the bars.
                if (turner.selection != null) {
                    turner.selection = null
                    return@PageTurner
                }
                val hit = page.wordAt(x, y, marker)
                if (hit != null && hit.marks.isNotEmpty()) {
                    lastSentence = hit.sentence
                    trail.open(hit.marks.flatMap { it.entries }.distinct())
                } else {
                    barsVisible = !barsVisible
                    if (!barsVisible) jumpedFrom = null
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
            enter = quickSlideIn { -it },
            exit = quickSlideOut { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = book.title,
                author = book.author,
                section = listOfNotNull(section?.title, inChapter).joinToString(" · ").ifEmpty { null },
                onBack = onBack,
                onDictionary = onDictionary,
                onContents = { contentsOpen = true },
            )
        }
        AnimatedVisibility(
            visible = barsVisible,
            enter = quickSlideIn { it },
            exit = quickSlideOut { it },
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
                trackingPaused = trackingPaused,
                onTracking = { confirmingTracking = true },
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
        // Highlighted words: write about them, look them up, or copy them.
        val selection = turner.selection
        val selected = remember(current, selection) { selection?.let { current?.passage(it.start, it.end) } }
        if (selected != null && selected.rects.isNotEmpty() && !turner.selecting && turner.offset.value == 0f) {
            val existing = remember(selected.text, entries) {
                val ids = marker.matcher.lookup(selected.text)
                entries.firstOrNull { it.id in ids }
            }
            val zoom = turner.zoom
            val origin = Offset(left.toFloat(), top.toFloat())
            val rects = selected.rects
            SelectionMenu(
                anchor = Rect(
                    zoom.toScreen(Offset(rects.minOf { it.left }, rects.first().top), origin),
                    zoom.toScreen(Offset(rects.maxOf { it.right }, rects.last().bottom), origin),
                ),
                addLabel = if (existing != null) "Edit “${existing.headword}”" else "Add to dictionary",
                addIcon = if (existing != null) R.drawable.ic_edit else R.drawable.ic_add,
                actions = actions,
                actionStore = quickActions,
                onAdd = {
                    turner.selection = null
                    lastSentence = selected.sentence
                    if (existing != null) {
                        editing = existing
                    } else {
                        val text = selected.text
                        val page = current
                        scope.launch {
                            // Capitalised only for starting a sentence, if it is in lowercase elsewhere.
                            val lowercaseElsewhere = page != null && withContext(Dispatchers.Default) {
                                val words = Regex("\\b${Regex.escape(text.lowercase())}\\b")
                                pages.of(page.spine).any { words.containsMatchIn(it.text()) }
                            }
                            val guess = Words.suggestScope(text, selected.startsSentence, lowercaseElsewhere, shelf != null || suggestedShelf.isNotEmpty())
                            editing = Entry(headword = Words.headword(text, guess), scope = guess)
                        }
                    }
                },
                onAction = { action ->
                    turner.selection = null
                    lastSentence = selected.sentence
                    runAction(action, selected.text)
                },
                onCopy = {
                    turner.selection = null
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(book.title, selected.text))
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
            trail = trail,
            entries = entries,
            scopeLabel = { entry ->
                when (entry.scope) {
                    Scope.BOOK -> "This book"
                    Scope.SHELF -> shelf?.name ?: "Shelf"
                    Scope.LIBRARY -> "Library"
                }
            },
            actions = actions,
            actionStore = quickActions,
            onAction = { action -> runAction(action, entries.firstOrNull { it.id in trail.ids }?.headword.orEmpty()) },
            onConfigureActions = { configuringActions = true },
            onEdit = { entry ->
                trail.close()
                editing = entry
            },
            onAddAnother = { entry ->
                trail.close()
                val other = if (entry.scope == Scope.LIBRARY) (if (shelf != null || suggestedShelf.isNotEmpty()) Scope.SHELF else Scope.BOOK) else Scope.LIBRARY
                editing = Entry(headword = entry.headword, scope = other)
            },
            onEntryLink = { target ->
                val ids = marker.matcher.lookup(target)
                if (ids.isNotEmpty()) {
                    trail.follow(ids)
                } else {
                    trail.close()
                    val guess = Words.suggestScope(target, startsSentence = false, appearsInLowercase = false, onShelf = shelf != null || suggestedShelf.isNotEmpty())
                    editing = Entry(headword = target, scope = guess)
                }
            },
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
                        else -> (graph.shelves.of(bookId) ?: graph.shelves.put(bookId, newShelf ?: suggestedShelf)).id.toString()
                    }
                    dictionary.save(edited.copy(scopeId = scopeId), origin)
                }
            },
            onDelete = { entry ->
                editing = null
                graph.scope.launch(Dispatchers.IO) { dictionary.delete(entry.id) }
            },
            onClose = { editing = null },
            linkable = entries,
        )
        QuickActionsPanel(open = configuringActions, store = quickActions, onClose = { configuringActions = false })
        if (confirmingTracking) {
            ConfirmTracking(
                pause = !trackingPaused,
                onConfirm = {
                    pauseTracking(!trackingPaused)
                    confirmingTracking = false
                },
                onDismiss = { confirmingTracking = false },
            )
        }
    }
}

private class Shown(val page: BookPage, val arrivedBy: String)

// A swipe that starts this close to a side edge goes back instead of turning the page: the blank
// margin beside the text. It is wider than Android's own back strip when Back sensitivity is
// low (18dp at the lowest setting), so a swipe from the edge goes back either way.
private val EdgeWidth = 32.dp

// How far in from the edge a swipe has to come before it goes back.
private val BackDistance = 40.dp

/** A swipe from a side edge on its way to going back, [progress] 1 or more once it will. */
private class EdgePull(val left: Boolean, val y: Float, val progress: Float)

/** The page on screen, its neighbours, and how far a swipe has moved them. */
@Stable
private class Turner(val pages: Pages) {
    var shown by mutableStateOf<Shown?>(null)
        private set
    var next by mutableStateOf<BookPage?>(null)
    var previous by mutableStateOf<BookPage?>(null)
    val offset = Animatable(0f)
    var pull by mutableStateOf<EdgePull?>(null)

    var zoom by mutableStateOf(Zoom())

    /** Words highlighted on the page shown, and whether a finger is still moving the highlight. */
    var selection by mutableStateOf<WordRange?>(null)
    var selecting by mutableStateOf(false)

    suspend fun show(page: BookPage, arrivedBy: String) {
        offset.snapTo(0f)
        zoom = Zoom()
        selection = null
        selecting = false
        shown = Shown(page, arrivedBy)
    }

    suspend fun unzoom() {
        val from = zoom
        animate(0f, 1f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { t, _ -> zoom = from.towardsNone(t) }
    }

    /** Ends a swipe: on to the next or previous page, or back to where it was ([forward] null). */
    suspend fun settle(forward: Boolean?, width: Float) {
        val target = when (forward) {
            true -> next
            false -> previous
            null -> null
        }
        if (target == null) {
            offset.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
            return
        }
        offset.animateTo(if (forward == true) -width else width, tween(160, easing = FastOutSlowInEasing))
        show(target, if (forward == true) Arrival.NEXT else Arrival.PREVIOUS)
    }
}

/** Where [at] on the screen is on a page drawn at [origin], through this zoom. */
private fun Zoom.toPage(at: Offset, origin: Offset) = Offset((at.x - pan.x) / scale - origin.x, (at.y - pan.y) / scale - origin.y)

/** Where [at] on a page drawn at [origin] is on the screen, through this zoom. */
private fun Zoom.toScreen(at: Offset, origin: Offset) = Offset(pan.x + scale * (origin.x + at.x), pan.y + scale * (origin.y + at.y))

// The selection handles: a drop hanging from each end of the highlight.
private val HandleRadius = 11.dp

// How dark a page is while another fully covers it.
private const val CoveredDim = 0.5f

/**
 * Draws [turner]'s page and turns it by swiping sideways: the page slides off to uncover the next
 * one lying beneath it, or the previous page slides back over it. A short or slow swipe springs
 * back. A swipe up or down turns nothing, and a swipe in from
 * a side edge goes back. Two fingers zoom the page; while it is zoomed, one finger moves it
 * instead of turning, and a double tap or Back zooms out.
 * Holding a word highlights it, and moving on before letting go highlights the words on to the
 * finger; the handles at its ends move them afterwards. A swipe, or Back, drops the highlight.
 * Taps are handed on with the point on the page, zoom taken into account.
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
    onShow: (BookPage, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flingSpeed = with(LocalDensity.current) { 400.dp.toPx() } // per second
    val shown = turner.shown
    // The gesture handlers below are set up once; these keep them calling the latest callbacks,
    // which see the latest dictionary.
    val tap by rememberUpdatedState(onTap)
    val currentMarker by rememberUpdatedState(marker)

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
    BackHandler(enabled = turner.selection != null) { turner.selection = null }
    val origin = Offset(pageLeft, pageTop)
    val selection = turner.selection
    val passage = remember(shown.page, selection) { selection?.let { shown.page.passage(it.start, it.end) } }
    val selected by rememberUpdatedState(passage)

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val gestures = WindowInsets.systemGestures
    val minEdge = with(density) { EdgeWidth.toPx() }
    val edgeLeft by rememberUpdatedState(max(gestures.getLeft(density, direction).toFloat(), minEdge))
    val edgeRight by rememberUpdatedState(max(gestures.getRight(density, direction).toFloat(), minEdge))
    val backDistance = with(density) { BackDistance.toPx() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val goBack by rememberUpdatedState { backDispatcher?.onBackPressed() }
    val haptics = LocalHapticFeedback.current
    val pullFill = MaterialTheme.colorScheme.secondaryContainer
    val pullArrow = MaterialTheme.colorScheme.onSecondaryContainer
    val handleColor = MaterialTheme.colorScheme.primary
    val highlightColor = handleColor.copy(alpha = 0.35f).toArgb()
    val handleRadius = with(density) { HandleRadius.toPx() }
    val handleReach = handleRadius + with(density) { 16.dp.toPx() }
    val coverFill = remember { Paint().apply { color = Page.background.toArgb() } }
    val coverDim = remember { Paint().apply { color = Page.background.toArgb() } }
    val coverEdge = remember { Paint().apply { color = Page.text.copy(alpha = 0.3f).toArgb() } }

    /** Which handle is at [at] on the screen: the start's (true), the end's (false), or neither. */
    fun handleAt(at: Offset): Boolean? {
        val rects = selected?.rects?.takeIf { it.isNotEmpty() } ?: return null
        val zoom = turner.zoom
        val start = zoom.toScreen(Offset(rects.first().left, rects.first().bottom), origin) + Offset(-handleRadius, handleRadius)
        val end = zoom.toScreen(Offset(rects.last().right, rects.last().bottom), origin) + Offset(handleRadius, handleRadius)
        val toStart = (at - start).getDistance()
        val toEnd = (at - end).getDistance()
        return when {
            toStart <= handleReach && toStart <= toEnd -> true
            toEnd <= handleReach -> false
            else -> null
        }
    }

    /**
     * Highlights from [anchor] to the word under finger [id] as it moves, until it lifts. [grab]
     * is how far the finger is from the point it moves: a handle's, below the text.
     */
    suspend fun AwaitPointerEventScope.extend(page: BookPage, id: PointerId, anchor: WordRange, grab: Offset) {
        turner.selecting = true
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: break
            // A second finger is zooming, which keeps the highlight as it is.
            if (event.changes.count { it.pressed } > 1) break
            if (!change.pressed) {
                change.consume()
                break
            }
            if (change.positionChanged()) {
                val at = turner.zoom.toPage(change.position - grab, origin)
                page.wordNear(at.x, at.y)?.let { word ->
                    val next = WordRange(min(anchor.start, word.start), max(anchor.end, word.end))
                    if (next != turner.selection) {
                        turner.selection = next
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
            change.consume()
        }
        turner.selecting = false
    }
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
                    onTap = { onPage(it, tap) },
                )
            }
            .pointerInput(turner) {
                val tracker = VelocityTracker()
                val width = size.width.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val page = turner.shown?.page ?: return@awaitEachGesture
                    // A handle moves its end of the highlight; the word at the other end stays.
                    val handle = handleAt(down.position)
                    val rects = selected?.rects
                    val current = turner.selection
                    if (handle != null && rects != null && current != null) {
                        down.consume()
                        val anchor = if (handle) {
                            page.wordAround(current.end - 1) ?: WordRange(current.end, current.end)
                        } else {
                            page.wordAround(current.start) ?: WordRange(current.start, current.start)
                        }
                        val textPoint = if (handle) Offset(rects.first().left, rects.first().centerY()) else Offset(rects.last().right, rects.last().centerY())
                        extend(page, down.id, anchor, grab = down.position - turner.zoom.toScreen(textPoint, origin))
                        return@awaitEachGesture
                    }
                    // Held still, it highlights the word; moved, it is a swipe, read once past the touch slop.
                    var slop: Offset? = null
                    val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        slop = awaitSlop(down.id)
                        true
                    }
                    if (decided == null) {
                        val at = turner.zoom.toPage(down.position, origin)
                        val hit = page.wordAt(at.x, at.y, currentMarker)
                        if (hit == null) {
                            // No word here; and a long press is not a tap.
                            follow(down.id) {}
                            currentEvent.changes.forEach { it.consume() }
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val word = WordRange(hit.start, hit.end)
                        turner.selection = word
                        extend(page, down.id, word, grab = Offset.Zero)
                        return@awaitEachGesture
                    }
                    val moved = slop ?: return@awaitEachGesture
                    turner.selection = null
                    when {
                        // Up or down: not a page turn, and not a tap either.
                        !Swipe.sideways(moved.x, moved.y) -> follow(down.id) {}
                        down.position.x < edgeLeft || down.position.x > width - edgeRight -> {
                            // In from a side edge: back, with an arrow at the edge until it lets go.
                            val left = down.position.x < edgeLeft
                            var inward = if (left) moved.x else -moved.x
                            var drift = moved.y
                            var armed = false
                            fun pull(y: Float) {
                                val progress = inward / backDistance
                                turner.pull = EdgePull(left, y, progress)
                                val ready = Swipe.back(inward, drift, backDistance)
                                if (ready && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                armed = ready
                            }
                            pull(down.position.y)
                            val lifted = follow(down.id) { change ->
                                val delta = change.positionChange()
                                inward += if (left) delta.x else -delta.x
                                drift += delta.y
                                pull(change.position.y)
                            }
                            turner.pull = null
                            if (lifted && Swipe.back(inward, drift, backDistance)) goBack()
                        }
                        else -> {
                            tracker.resetTracking()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var total = moved
                            var shift = turner.offset.value
                            val lifted = follow(down.id) { change ->
                                val delta = change.positionChange()
                                total += delta
                                tracker.addPosition(change.uptimeMillis, change.position)
                                // Past the first or last page it only gives a little.
                                val min = if (turner.next != null) -width else -width / 10
                                val max = if (turner.previous != null) width else width / 10
                                shift = (shift + delta.x).coerceIn(min, max)
                                val to = shift
                                scope.launch { turner.offset.snapTo(to) }
                            }
                            val forward = if (lifted) {
                                val velocity = tracker.calculateVelocity()
                                Swipe.turn(total.x, total.y, velocity.x, velocity.y, shift, width, flingSpeed)
                            } else {
                                null
                            }
                            scope.launch { turner.settle(forward, width) }
                        }
                    }
                }
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
            fun draw(page: BookPage?, shift: Float, highlight: Highlight? = null) {
                if (page == null) return
                native.save()
                native.translate(pageLeft + shift, pageTop)
                page.draw(native, marker, highlight)
                native.restore()
            }
            // A page beneath one sliding over it is dimmed by how much of it is covered; on black
            // that does what a shadow would.
            fun beneath(page: BookPage?, covered: Float, highlight: Highlight? = null) {
                draw(page, 0f, highlight)
                coverDim.alpha = (covered.coerceIn(0f, 1f) * CoveredDim * 255).toInt()
                native.drawRect(0f, 0f, size.width, size.height, coverDim)
            }
            // A sliding page hides what is under it, and a faint line marks its edge.
            fun over(page: BookPage?, shift: Float, highlight: Highlight? = null) {
                if (page == null) return
                native.drawRect(shift, 0f, shift + size.width, size.height, coverFill)
                draw(page, shift, highlight)
                val edge = shift + size.width
                native.drawRect(edge - 1.dp.toPx(), 0f, edge, size.height, coverEdge)
            }
            val page = turner.shown?.page
            val highlight = turner.selection?.let { Highlight(it.start, it.end, highlightColor) }
            when {
                // On: this page slides off to the left, uncovering the next one where it lies.
                dx < 0 -> {
                    beneath(turner.next, covered = 1 + dx / size.width)
                    over(page, dx, highlight)
                }
                // Back: the previous page slides in over this one. On the first page there is
                // none, and this page gives a little instead.
                dx > 0 && turner.previous != null -> {
                    beneath(page, covered = dx / size.width, highlight)
                    over(turner.previous, dx - size.width)
                }
                else -> draw(page, dx, highlight)
            }
            native.restore()
        }
        val rects = passage?.rects
        if (!rects.isNullOrEmpty() && dx == 0f) {
            val zoom = turner.zoom
            drawHandle(zoom.toScreen(Offset(rects.first().left, rects.first().bottom), origin), start = true, handleColor, handleRadius)
            drawHandle(zoom.toScreen(Offset(rects.last().right, rects.last().bottom), origin), start = false, handleColor, handleRadius)
        }
        turner.pull?.let { drawPull(it, pullFill, pullArrow) }
    }
}

/**
 * Waits for finger [id] to move past the touch slop and returns how far it went, taking that
 * move so it isn't also a tap. Null if it lifted first (a tap or a long press), a second finger
 * came down, or zooming took the move.
 */
private suspend fun AwaitPointerEventScope.awaitSlop(id: PointerId): Offset? {
    var moved = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return null
        if (!change.pressed || change.isConsumed || event.changes.count { it.pressed } > 1) return null
        moved += change.positionChange()
        if (moved.getDistance() >= viewConfiguration.touchSlop) {
            change.consume()
            return moved
        }
    }
}

/**
 * Follows finger [id] until it lifts, handing each move to [onMove] and taking it. Returns true
 * if it lifted, false if the swipe was cut short: a second finger came down, zooming took the
 * move, or the system took the gesture.
 */
private suspend fun AwaitPointerEventScope.follow(id: PointerId, onMove: (PointerInputChange) -> Unit): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        // A cancelled gesture arrives as a lift that is already taken.
        if (change.isConsumed) return false
        if (!change.pressed) return true
        if (event.changes.count { it.pressed } > 1) return false
        onMove(change)
        change.consume()
    }
}

/** A selection handle: a drop hanging from [tip], its point at the start or end of the highlight. */
private fun DrawScope.drawHandle(tip: Offset, start: Boolean, color: Color, radius: Float) {
    drawCircle(color, radius, Offset(if (start) tip.x - radius else tip.x + radius, tip.y + radius))
    drawRect(color, topLeft = Offset(if (start) tip.x - radius else tip.x, tip.y), size = Size(radius, radius))
}

/** The arrow at a side edge while a swipe from it is on its way to going back. */
private fun DrawScope.drawPull(pull: EdgePull, fill: Color, arrow: Color) {
    val radius = 20.dp.toPx()
    val reach = pull.progress.coerceIn(0f, 1f)
    val ready = pull.progress >= 1f
    // Slides out from behind the edge as the swipe comes in.
    val fromEdge = -radius + reach * (radius + 18.dp.toPx())
    val x = if (pull.left) fromEdge else size.width - fromEdge
    val center = Offset(x, pull.y)
    drawCircle(fill.copy(alpha = if (ready) 1f else 0.6f), radius, center)
    // Points back at the edge it came from.
    val point = if (pull.left) -1f else 1f
    val half = 4.dp.toPx()
    val tall = 7.dp.toPx()
    val chevron = Path().apply {
        moveTo(x - point * half, center.y - tall)
        lineTo(x + point * half, center.y)
        lineTo(x - point * half, center.y + tall)
    }
    drawPath(
        chevron,
        arrow.copy(alpha = if (ready) 1f else 0.6f),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
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
