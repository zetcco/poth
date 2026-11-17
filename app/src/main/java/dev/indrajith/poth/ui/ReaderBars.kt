package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.R
import dev.indrajith.poth.reader.Section
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Back, the dictionary, the contents, and what is being read: title, author, section and page in it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderTopBar(
    title: String,
    author: String,
    section: String?,
    onBack: () -> Unit,
    onDictionary: () -> Unit,
    onContents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to books") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDictionary) { Icon(painterResource(R.drawable.ic_dictionary), contentDescription = "Dictionary") }
                IconButton(onClick = onContents) { Icon(painterResource(R.drawable.ic_toc), contentDescription = "Contents") }
            }
            val lines = Modifier.padding(horizontal = 16.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = lines)
            val secondary = MaterialTheme.colorScheme.onSurfaceVariant
            if (author.isNotEmpty()) Text(author, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, modifier = lines)
            if (section != null) {
                Text(section, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = lines)
            }
        }
    }
}

/**
 * The page count, the reading tracker's pause, rotation lock and the scrubber. While the scrubber is dragged, a bubble in
 * place of the page count says where letting go will go. [back] offers a way back after a jump.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderBottomBar(
    pageLabel: String,
    rotationLocked: Boolean,
    onRotation: () -> Unit,
    trackingPaused: Boolean,
    onTracking: () -> Unit,
    scrubber: ScrubberModel?,
    back: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (back != null) {
            FilledTonalButton(onClick = onBackClick, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text(back)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            // The scrubber sits above the system gesture area, so a swipe on the pill never reaches it.
            Column(
                Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.mandatorySystemGestures)
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    val preview = dragging
                    if (preview != null && scrubber != null) {
                        val bias = if (scrubber.total > 1) preview.toFloat() / (scrubber.total - 1) * 2 - 1 else 0f
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(BiasAlignment(bias, 0f)),
                        ) {
                            Text(
                                scrubber.label(preview),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        TrackingButton(trackingPaused, onClick = onTracking, modifier = Modifier.align(Alignment.CenterStart))
                        Text(pageLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center))
                        IconButton(onClick = onRotation, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                painterResource(if (rotationLocked) R.drawable.ic_screen_lock_rotation else R.drawable.ic_screen_rotation),
                                contentDescription = if (rotationLocked) "Rotation locked" else "Rotation follows the phone",
                            )
                        }
                    }
                }
                if (scrubber != null) {
                    Scrubber(scrubber, onDragging = { dragging = it })
                } else {
                    // The whole book's pages are still being counted.
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 18.dp))
                }
            }
        }
    }
}

/** What the scrubber shows and does. Page numbers start at 0. */
class ScrubberModel(
    val total: Int,
    val current: Int,
    val ticks: List<Int>,
    val label: (page: Int) -> String,
    val onJump: (page: Int) -> Unit,
)

/**
 * The whole book as a thin track, with a tick at each part. Only the handle can be dragged, and only
 * by a mostly sideways drag: touching the track does nothing, and a vertical swipe (the system's
 * gestures) is left alone. Nothing moves until the thumb is let go.
 */
@Composable
private fun Scrubber(model: ScrubberModel, onDragging: (Int?) -> Unit) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(model)
    val colors = MaterialTheme.colorScheme
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                val inset = 8.dp.toPx()
                val reach = 24.dp.toPx()
                fun xOf(page: Int, width: Float) =
                    inset + (if (current.total > 1) page.toFloat() / (current.total - 1) else 0f) * (width - 2 * inset)
                fun pageAt(x: Float, width: Float) =
                    (((x - inset) / (width - 2 * inset)).coerceIn(0f, 1f) * (current.total - 1)).roundToInt()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (abs(down.position.x - xOf(current.current, width)) > reach) return@awaitEachGesture // the track
                    // Wait for the finger to travel; it has to go sideways more than up or down.
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        val moved = change.position - down.position
                        if (moved.getDistance() > viewConfiguration.touchSlop) {
                            if (abs(moved.x) < abs(moved.y) * 1.5f) return@awaitEachGesture
                            break
                        }
                    }
                    var page = pageAt(down.position.x, width)
                    dragging = page
                    onDragging(page)
                    val finished = drag(down.id) { change ->
                        change.consume()
                        page = pageAt(change.position.x, width)
                        dragging = page
                        onDragging(page)
                    }
                    dragging = null
                    onDragging(null)
                    if (finished && page != current.current) current.onJump(page)
                }
            },
    ) {
        val inset = 8.dp.toPx()
        val trackHeight = 4.dp.toPx()
        val handleWidth = 4.dp.toPx()
        val handleHeight = 24.dp.toPx()
        val gap = 4.dp.toPx()
        val middle = size.height / 2
        fun xOf(page: Int) = inset + (if (model.total > 1) page.toFloat() / (model.total - 1) else 0f) * (size.width - 2 * inset)
        val x = xOf(dragging ?: model.current)
        val track = CornerRadius(trackHeight / 2)

        if (x - gap > 0) drawRoundRect(colors.primary, Offset(0f, middle - trackHeight / 2), Size(x - gap, trackHeight), track)
        if (x + gap < size.width) {
            drawRoundRect(colors.onSurface.copy(alpha = 0.16f), Offset(x + gap, middle - trackHeight / 2), Size(size.width - x - gap, trackHeight), track)
        }
        // Ticks stand out above and below the track. Ones that would crowd the last are left out.
        val tickWidth = 2.dp.toPx()
        val tickHeight = 10.dp.toPx()
        val spacing = 6.dp.toPx()
        var lastTick = Float.NEGATIVE_INFINITY
        for (tick in model.ticks) {
            val tx = xOf(tick)
            if (tx - lastTick < spacing || abs(tx - x) < gap + handleWidth) continue
            lastTick = tx
            drawRoundRect(
                if (tx < x) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                Offset(tx - tickWidth / 2, middle - tickHeight / 2),
                Size(tickWidth, tickHeight),
                CornerRadius(tickWidth / 2),
            )
        }
        drawRoundRect(colors.primary, Offset(x - handleWidth / 2, middle - handleHeight / 2), Size(handleWidth, handleHeight), CornerRadius(handleWidth / 2))
    }
}

/**
 * The table of contents, as a panel that slides up over the page. It is built once while hidden
 * and stays built, so opening it is only the slide; a bottom sheet makes a window of its own each
 * time, which showed as a hitch. Full height and scrolled to where reading is; parts read as
 * headings, what has been read is dimmed, and each entry shows its page once the book is counted.
 * Closes with Back, a tap above it, or a drag down on its top.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentsPanel(
    open: Boolean,
    contents: List<Section>,
    current: Section?,
    pageOf: (Section) -> Int?,
    onPick: (Section) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shown by animateFloatAsState(if (open) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "contents")
    val scrim by animateFloatAsState(if (open) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "scrim")
    var dragged by remember { mutableFloatStateOf(0f) }
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }
    val currentIndex = contents.indexOf(current)
    val list = rememberLazyListState()
    LaunchedEffect(open) {
        if (open) {
            dragged = 0f
            list.scrollToItem(max(0, currentIndex - 3))
        }
    }
    BackHandler(enabled = open, onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(colors.scrim.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        Surface(
            color = colors.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                .padding(top = 8.dp)
                // Hidden, it waits below the screen, where touches cannot reach it.
                .graphicsLayer { translationY = ((1f - shown) * size.height + dragged).coerceAtLeast(0f) },
        ) {
            Column {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
                            onDragStopped = { velocity ->
                                if (dragged > dismissDistance || velocity > 1500f) onClose() else dragged = 0f
                            },
                        )
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                ) {
                    Box(
                        Modifier
                            .padding(vertical = 16.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(width = 32.dp, height = 4.dp)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )
                    Text("Contents", style = MaterialTheme.typography.headlineSmall)
                    if (current != null) {
                        Text(current.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (contents.isEmpty()) {
                    Text(
                        "This book has no table of contents.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    val topDepth = contents.minOf { it.depth }
                    LazyColumn(
                        state = list,
                        contentPadding = WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    ) {
                        itemsIndexed(contents) { index, section ->
                            ContentsRow(
                                section = section,
                                heading = contents.getOrNull(index + 1)?.let { it.depth > section.depth } == true,
                                indent = section.depth - topDepth,
                                isCurrent = index == currentIndex,
                                read = index < currentIndex,
                                page = pageOf(section),
                                onClick = { onPick(section) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsRow(section: Section, heading: Boolean, indent: Int, isCurrent: Boolean, read: Boolean, page: Int?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = when {
        isCurrent -> colors.onSecondaryContainer
        heading -> colors.primary
        read -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = if (heading) 12.dp else 0.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isCurrent) colors.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp + 20.dp * indent, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.title,
            style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (page != null) {
            Text(
                "${page + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
