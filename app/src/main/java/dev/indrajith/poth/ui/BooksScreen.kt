package dev.indrajith.poth.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.R
import dev.indrajith.poth.data.Book
import dev.indrajith.poth.data.Shelf
import dev.indrajith.poth.dictionary.Words
import dev.indrajith.poth.epub.EpubException
import dev.indrajith.poth.stats.ReadingStats
import dev.indrajith.poth.stats.ReadingSummary
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EPUB_MIME = "application/epub+zip"

// Covers already read from disk, so scrolling back does not load them again.
private val coverCache = HashMap<String, ImageBitmap>()

/**
 * The library: where reading is (the last book, today, the streak), then the books by shelf as
 * covers with how far through each is. A book held down can be put on a shelf; "Add book" copies
 * an EPUB in from the file picker and opens it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BooksScreen(graph: Graph, onOpen: (String) -> Unit, onStats: () -> Unit) {
    val books by graph.books.all.collectAsStateWithLifecycle()
    val shelfChanges by graph.shelves.changes.collectAsStateWithLifecycle()
    val shelfOf by produceState(emptyMap<String, Shelf>(), shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.byBook() } }
    val shelves by produceState(emptyList<Shelf>(), shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.all() } }
    val zone = remember { ZoneId.systemDefault() }
    val summary by produceState<ReadingSummary?>(null, books) {
        value = withContext(Dispatchers.IO) { ReadingStats.summarize(graph.log.since(), zone) }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var shelving by remember { mutableStateOf<Book?>(null) }
    var renaming by remember { mutableStateOf<Shelf?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            var problem: String? = null
            try {
                onOpen(graph.importer.import(uri).id)
            } catch (e: EpubException) {
                problem = "Can't read this book: ${e.message}"
            } catch (e: IOException) {
                problem = "Can't copy this file: ${e.message}"
            } finally {
                importing = false
            }
            problem?.let { snackbar.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poth") },
                actions = {
                    IconButton(onClick = onStats) { Icon(painterResource(R.drawable.ic_insights), contentDescription = "Reading stats") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!importing) pick.launch(arrayOf(EPUB_MIME)) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add book") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (books.isEmpty() && !importing) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No books yet. Add an EPUB from your phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        // Shelves in name order, then the books on none; in a shelf, by series number, then title.
        val sections = remember(books, shelfOf) {
            val order = compareBy<Book>({ it.seriesIndex ?: Double.MAX_VALUE }, { it.title.lowercase() })
            val onShelves = books.filter { it.id in shelfOf }.groupBy { shelfOf.getValue(it.id) }
                .toSortedMap(compareBy { it.name.lowercase() })
                .map { (shelf, list) -> shelf to list.sortedWith(order) }
            val loose = books.filter { it.id !in shelfOf }.sortedWith(order)
            onShelves + if (loose.isEmpty()) emptyList() else listOf(null to loose)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (importing) {
                item(span = { GridItemSpan(maxLineSpan) }) { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            }
            val last = books.firstOrNull { it.openedAt != null }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryCard(graph, last, summary, onOpen = onOpen, onStats = onStats)
            }
            for ((shelf, list) in sections) {
                // With no shelves at all, the books need no heading.
                if (shelf != null || sections.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "shelf-${shelf?.id}") {
                        ShelfHeader(shelf?.name ?: "Not on a shelf", list.size, onRename = shelf?.let { { renaming = it } })
                    }
                }
                items(list, key = { it.id }) { book ->
                    BookTile(
                        graph = graph,
                        book = book,
                        shelf = shelfOf[book.id],
                        onOpen = { onOpen(book.id) },
                        onShelve = { shelving = book },
                        onTakeOff = { graph.scope.launch(Dispatchers.IO) { graph.shelves.takeOff(book.id) } },
                    )
                }
            }
        }
    }

    shelving?.let { book ->
        ShelfDialog(
            shelves = shelves,
            current = shelfOf[book.id],
            suggestion = Words.suggestShelf(book.title, book.series),
            onPick = { name ->
                shelving = null
                graph.scope.launch(Dispatchers.IO) { graph.shelves.put(book.id, name) }
            },
            onDismiss = { shelving = null },
        )
    }
    renaming?.let { shelf ->
        NameDialog(
            title = "Rename shelf",
            initial = shelf.name,
            confirm = "Rename",
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    val renamed = withContext(Dispatchers.IO) { graph.shelves.rename(shelf.id, name) }
                    if (!renamed) snackbar.showSnackbar("There is already a shelf called “$name”")
                }
            },
            onDismiss = { renaming = null },
        )
    }
}

/** The book last read, to carry on with, and today's reading, the streak and the last two weeks. */
@Composable
private fun SummaryCard(graph: Graph, last: Book?, summary: ReadingSummary?, onOpen: (String) -> Unit, onStats: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (last != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(last.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(graph, last, Modifier.width(64.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Continue reading", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text(last.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (last.author.isNotEmpty()) {
                            Text(last.author, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.height(8.dp))
                        Progress(last)
                    }
                }
            }
            val today = remember { LocalDate.now() }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onStats).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.padding(end = 20.dp)) {
                    Text("Today", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(duration(summary?.minutesOn(today) ?: 0), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.padding(end = 16.dp)) {
                    Text("Streak", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    val streak = summary?.streak(today)?.current ?: 0
                    Text(if (streak == 1) "1 day" else "$streak days", style = MaterialTheme.typography.titleMedium)
                }
                if (summary != null) RecentDays(summary, today, Modifier.weight(1f).height(18.dp))
            }
        }
    }
}

/** The last days as a row of squares, today on the right, shaded like the stats calendar. */
@Composable
private fun RecentDays(summary: ReadingSummary, today: LocalDate, modifier: Modifier) {
    val heat = heatColors()
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val cell = size.height
        val days = ((size.width + gap) / (cell + gap)).toInt().coerceIn(1, 21)
        val left = size.width - (days * (cell + gap) - gap)
        for (i in 0 until days) {
            val level = ReadingStats.level(summary.minutesOn(today.minusDays((days - 1 - i).toLong())))
            drawRoundRect(
                if (level == 0) heat.empty else heat.levels[level - 1],
                Offset(left + i * (cell + gap), 0f),
                Size(cell, cell),
                CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ShelfHeader(name: String, count: Int, onRename: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(if (count == 1) "1 book" else "$count books", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRename != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), contentDescription = "Shelf options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename shelf") }, onClick = { menu = false; onRename() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(graph: Graph, book: Book, shelf: Shelf?, onOpen: () -> Unit, onShelve: () -> Unit, onTakeOff: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
        Box {
            Cover(graph, book, Modifier.fillMaxWidth())
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (shelf == null) "Put on a shelf…" else "Move to another shelf…") }, onClick = { menu = false; onShelve() })
                if (shelf != null) DropdownMenuItem(text = { Text("Take off ${shelf.name}") }, onClick = { menu = false; onTakeOff() })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Progress(book)
    }
}

/** How far through the book: a thin bar and the percentage, or "Not started". */
@Composable
private fun Progress(book: Book) {
    val progress = book.progress
    if (progress == null) {
        Text(
            if (book.openedAt == null) "Not started" else "Reading", // shown properly once opened again
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.weight(1f))
        Text(
            "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The book's cover at 2:3, made from the EPUB the first time; the title stands in without one. */
@Composable
private fun Cover(graph: Graph, book: Book, modifier: Modifier) {
    val cover by produceState(coverCache[book.id], book.id) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            graph.covers.of(book.id, graph.files.of(book.id))?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }?.also { coverCache[book.id] = it }
    }
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                book.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Choosing a book's shelf: one of the shelves there are, or a new one typed in. */
@Composable
private fun ShelfDialog(shelves: List<Shelf>, current: Shelf?, suggestion: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(current?.name ?: shelves.firstOrNull()?.name) }
    var typed by remember { mutableStateOf(if (shelves.isEmpty()) suggestion else "") }
    val name = typed.trim().ifEmpty { chosen.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Put on a shelf") },
        text = {
            Column {
                for (shelf in shelves) {
                    val pick = {
                        chosen = shelf.name
                        typed = ""
                    }
                    Row(Modifier.fillMaxWidth().clickable(onClick = pick), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = typed.isBlank() && chosen == shelf.name, onClick = pick)
                        Text(shelf.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    typed,
                    { typed = it },
                    label = { Text(if (shelves.isEmpty()) "Shelf" else "Or a new shelf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(name) }, enabled = name.isNotBlank()) { Text("Put on shelf") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
