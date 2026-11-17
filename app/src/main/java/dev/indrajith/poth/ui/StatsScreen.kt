package dev.indrajith.poth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.R
import dev.indrajith.poth.stats.ReadingRules
import dev.indrajith.poth.stats.ReadingStats
import dev.indrajith.poth.stats.ReadingSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The heatmap's colours: [empty] for days not read, then one hue, brighter for more reading. */
class HeatColors(val empty: Color, val levels: List<Color>)

/**
 * On the dark surface: the wallpaper's accent from tone 40 up to tone 95. Tone 30 was tried first
 * and came out at 1.86:1 against the card, too close to an empty day to see.
 */
@Composable
fun heatColors() = HeatColors(
    empty = MaterialTheme.colorScheme.surfaceContainerHighest,
    levels = listOf(
        colorResource(android.R.color.system_accent1_600),
        colorResource(android.R.color.system_accent1_400),
        colorResource(android.R.color.system_accent1_200),
        colorResource(android.R.color.system_accent1_50),
    ),
)

/**
 * How much has been read: today, this week, the streak and speed; a GitHub-style calendar of the
 * last months; and each book. Only reading counts, as [ReadingStats] decides it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(graph: Graph, onBack: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var speedSince by remember { mutableLongStateOf(graph.prefs.speedSince) }
    val summary by produceState<ReadingSummary?>(null, speedSince) {
        value = withContext(Dispatchers.IO) { graph.readingSummary(zone) }
    }
    var measuringAgain by remember { mutableStateOf(false) }
    val trackingPaused by graph.prefs.trackingPaused.collectAsStateWithLifecycle()
    var confirmingTracking by remember { mutableStateOf(false) }
    val books by graph.books.all.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } },
                actions = { TrackingButton(trackingPaused, onClick = { confirmingTracking = true }) },
            )
        },
    ) { padding ->
        val stats = summary
        if (stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        var selected by remember { mutableStateOf(today) }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (trackingPaused) TrackingPaused(onResume = { confirmingTracking = true })
            val week = (0L..6L).sumOf { stats.minutesOn(today.minusDays(it)) }
            val streak = stats.streak(today)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Today", duration(stats.minutesOn(today)), Modifier.weight(1f))
                Tile("Last 7 days", duration(week), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Streak", if (streak.current == 1) "1 day" else "${streak.current} days", Modifier.weight(1f), "Best: ${streak.longest}")
                // Tapped, it offers to measure the speed afresh, as when pages were skimmed.
                Tile(
                    "Speed",
                    if (stats.speedMeasured) "${stats.wordsPerMinute.roundToInt()} wpm" else "Measuring",
                    Modifier.weight(1f),
                    if (stats.speedMeasured) "From ${stats.speedPages} pages" else "${stats.speedPages} of ${ReadingRules().speedPages} pages",
                    onClick = { measuringAgain = true },
                )
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reading days", style = MaterialTheme.typography.titleMedium)
                    val colors = heatColors()
                    Heatmap(stats, today, colors, selected, onSelect = { selected = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val day = stats.days[selected]
                        Text(
                            buildString {
                                append(if (selected == today) "Today" else selected.format(DateTimeFormatter.ofPattern("EEE d MMM")))
                                append(" · ")
                                append(if (day == null) "nothing read" else "${duration(day.millis / 60_000)}, ${day.pages} pages")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Legend(colors)
                    }
                }
            }

            if (stats.books.isNotEmpty()) {
                Text("Books", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                for (book in stats.books.values.sortedByDescending { it.lastReadAt }) {
                    val title = books.firstOrNull { it.id == book.bookId }?.title ?: "A removed book"
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${duration(book.millis / 60_000)} · ${book.pages} pages · ${"%,d".format(book.words)} words",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Counted: pages on screen long enough to read, in sittings of a minute or more. Glances, and time " +
                    "beyond three times a page's reading time, are left out. Speed is measured on pages read straight " +
                    "through: not ones flipped to, peeked at, or left half read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    if (confirmingTracking) {
        ConfirmTracking(
            pause = !trackingPaused,
            onConfirm = {
                graph.prefs.pauseTracking(!trackingPaused)
                confirmingTracking = false
            },
            onDismiss = { confirmingTracking = false },
        )
    }
    if (measuringAgain) {
        AlertDialog(
            onDismissRequest = { measuringAgain = false },
            title = { Text("Measure speed again?") },
            text = { Text("The pages read so far are set aside, and your speed is measured again over the next ${ReadingRules().speedPages} pages you read.") },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    graph.prefs.speedSince = now
                    speedSince = now
                    measuringAgain = false
                }) { Text("Measure again") }
            },
            dismissButton = { TextButton(onClick = { measuringAgain = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null, onClick: (() -> Unit)? = null) {
    val content = @Composable {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainer
    val shape = RoundedCornerShape(24.dp)
    if (onClick == null) {
        Surface(color = color, shape = shape, modifier = modifier) { content() }
    } else {
        Surface(onClick = onClick, color = color, shape = shape, modifier = modifier) { content() }
    }
}

/**
 * A week a column, Monday at the top, the current week on the right: as many weeks as fit. A
 * tap picks a day for the line below.
 */
@Composable
private fun Heatmap(stats: ReadingSummary, today: LocalDate, colors: HeatColors, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val cell = 12.dp
    val gap = 3.dp
    val labels = 30.dp
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val weeks = ((maxWidth - labels + gap) / (cell + gap)).toInt().coerceIn(4, 53)
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = lastMonday.minusWeeks(weeks - 1L)
        fun dateAt(week: Int, day: Int): LocalDate = firstMonday.plusWeeks(week.toLong()).plusDays(day.toLong())
        val readDays = stats.days.keys.count { !it.isBefore(firstMonday) }

        Column {
            // Month names over the week each month starts in.
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                var lastLabelEnd = -1
                for (week in 0 until weeks) {
                    val monday = dateAt(week, 0)
                    val first = (0..6).map { dateAt(week, it) }.firstOrNull { it.dayOfMonth == 1 } ?: continue
                    if (week == 0 && first != monday) continue
                    if (week <= lastLabelEnd) continue
                    Text(
                        first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.offset(x = labels + (cell + gap) * week),
                    )
                    lastLabelEnd = week + 2
                }
            }
            Row {
                Column(Modifier.width(labels)) {
                    for (day in 0..6) {
                        Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                            if (day % 2 == 0) {
                                Text(
                                    DayOfWeek.of(day + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
                Canvas(
                    Modifier
                        .size(width = (cell + gap) * weeks - gap, height = (cell + gap) * 7 - gap)
                        .semantics { contentDescription = "Reading calendar: $readDays days read in the last $weeks weeks" }
                        .pointerInput(firstMonday, weeks) {
                            detectTapGestures { at ->
                                val step = (cell + gap).toPx()
                                val week = (at.x / step).toInt().coerceIn(0, weeks - 1)
                                val day = (at.y / step).toInt().coerceIn(0, 6)
                                val date = dateAt(week, day)
                                if (!date.isAfter(today)) onSelect(date)
                            }
                        },
                ) {
                    val size = cell.toPx()
                    val step = (cell + gap).toPx()
                    val radius = CornerRadius(3.dp.toPx())
                    for (week in 0 until weeks) {
                        for (day in 0..6) {
                            val date = dateAt(week, day)
                            if (date.isAfter(today)) continue
                            val level = ReadingStats.level(stats.minutesOn(date))
                            val color = if (level == 0) colors.empty else colors.levels[level - 1]
                            val topLeft = Offset(week * step, day * step)
                            drawRoundRect(color, topLeft, Size(size, size), radius)
                            if (date == selected) {
                                drawRoundRect(outline, topLeft, Size(size, size), radius, style = Stroke(1.5.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(colors: HeatColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val label = MaterialTheme.typography.labelSmall
        Text("Less", style = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(2.dp))
        for (color in listOf(colors.empty) + colors.levels) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(2.dp))
        Text("More", style = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "42 min", "3 h 5 min". */
fun duration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
