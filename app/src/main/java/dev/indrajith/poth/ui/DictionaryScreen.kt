package dev.indrajith.poth.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.R
import dev.indrajith.poth.data.Book
import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.data.Shelf
import dev.indrajith.poth.dictionary.Browse
import dev.indrajith.poth.dictionary.Matcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The entries that show in one place: the library, a shelf or a book. */
private class Group(val place: String, val entries: List<Entry>)

/**
 * Every entry, and the start of each one's notes for the list: read off the main thread, so a
 * row scrolling in has nothing left to parse.
 */
private class Listing(val entries: List<Entry>) {
    val previews: Map<Long, String> = entries.associate { it.id to Browse.preview(it.body) }
}

/**
 * The whole dictionary: every entry, by where it shows (the library, then each shelf, then
 * each book), and a search through words and notes. Tapping an entry shows its card; links in
 * it lead on to other entries, and it can be edited from there. A new entry can be written here
 * too, for the library, a shelf or a book: the word searched for, if nothing matched it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun DictionaryScreen(graph: Graph, onBack: () -> Unit) {
    val dictionary = graph.dictionary
    val changes by dictionary.changes.collectAsStateWithLifecycle()
    val shelfChanges by graph.shelves.changes.collectAsStateWithLifecycle()
    val books by graph.books.all.collectAsStateWithLifecycle()
    val loaded by produceState<Listing?>(null, changes) { value = withContext(Dispatchers.IO) { Listing(dictionary.all()) } }
    val shelves by produceState(emptyList<Shelf>(), shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.all() } }
    val shelfOf by produceState(emptyMap<String, Shelf>(), shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.byBook() } }
    val entries = loaded?.entries.orEmpty()
    val previews = loaded?.previews.orEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    val trail = remember { CardTrail() }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var choosingPlace by remember { mutableStateOf(false) }
    var showingGraph by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // The keyboard goes before a card or the editor comes up, which it would cover.
    val focus = LocalFocusManager.current

    fun place(entry: Entry): String = when (entry.scope) {
        Scope.LIBRARY -> "Library"
        Scope.SHELF -> shelves.firstOrNull { it.id.toString() == entry.scopeId }?.name ?: "A shelf"
        Scope.BOOK -> books.firstOrNull { it.id == entry.scopeId }?.title ?: "A book"
    }
    val groups = remember(entries, query, shelves, books) {
        Browse.search(entries, query)
            .groupBy { it.scope to it.scopeId }
            .values
            .map { Group(place(it.first()), it) }
            .sortedWith(compareBy({ -it.entries.first().scope.ordinal }, { it.place.lowercase() }))
    }
    // General words make no web worth drawing: the graph is of each shelf, its books' entries with it.
    val graphPlaces = remember(entries, shelves, books, shelfOf) {
        entries
            .filter { it.scope != Scope.LIBRARY }
            .groupBy { entry ->
                if (entry.scope == Scope.SHELF) "shelf:${entry.scopeId}" else shelfOf[entry.scopeId]?.let { "shelf:${it.id}" } ?: "book:${entry.scopeId}"
            }
            .map { (key, placed) ->
                val (kind, id) = key.split(':', limit = 2)
                val name = if (kind == "shelf") shelves.firstOrNull { it.id.toString() == id }?.name ?: "A shelf" else books.firstOrNull { it.id == id }?.title ?: "A book"
                GraphPlace(key, name, placed)
            }
            .sortedBy { it.name.lowercase() }
    }
    BackHandler(enabled = showingGraph && trail.ids.isEmpty() && editing == null) { showingGraph = false }
    val matcher = remember(entries) { Matcher(entries.flatMap { entry -> entry.terms.map { it to entry.id } }) }
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.canScrollBackward } }
    val colors = MaterialTheme.colorScheme
    val barColor by animateColorAsState(if (scrolled) colors.surfaceContainer else colors.surface, label = "bar")

    Box(Modifier.fillMaxSize()) {
        if (showingGraph) {
            GraphScreen(
                places = graphPlaces,
                focused = trail.ids.toSet(),
                onOpen = { trail.open(listOf(it)) },
                onClear = { trail.close() },
                onBack = {
                    trail.close()
                    showingGraph = false
                },
            )
        } else Scaffold(
            floatingActionButton = {
                if (loaded != null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            focus.clearFocus()
                            choosingPlace = true
                        },
                        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        text = { Text("New entry") },
                    )
                }
            },
            topBar = {
                // One solid bar for the title and the search, tinted once the list is scrolled.
                Column(Modifier.background(barColor)) {
                    TopAppBar(
                        title = { Text("Dictionary") },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                        },
                        actions = {
                            if (graphPlaces.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        focus.clearFocus()
                                        showingGraph = true
                                    },
                                ) { Icon(painterResource(R.drawable.ic_graph), contentDescription = "Graph of links") }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                    OutlinedTextField(
                        query,
                        { query = it },
                        placeholder = { Text(if (entries.isEmpty()) "Search" else "Search ${entries.size} entries") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") } }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    )
                }
            },
        ) { padding ->
            when {
                loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
                entries.isEmpty() -> Message("No entries yet. Hold a word while reading to write about it.", Modifier.padding(padding))
                groups.isEmpty() -> Message("Nothing matches “${query.trim()}”.", Modifier.padding(padding)) {
                    Button(
                        onClick = {
                            focus.clearFocus()
                            choosingPlace = true
                        },
                    ) { Text("Add “${query.trim()}”") }
                }
                // The list stops under the bar rather than scrolling behind it, so the headers stick below it.
                else -> LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                ) {
                    for (group in groups) {
                        stickyHeader(key = "place:${group.place}", contentType = "place") { PlaceHeader(group.place, group.entries.size) }
                        items(group.entries, key = { it.id }, contentType = { "entry" }) { entry ->
                            EntryRow(entry, previews[entry.id].orEmpty()) {
                                focus.clearFocus()
                                trail.open(listOf(entry.id))
                            }
                        }
                    }
                }
            }
        }

        EntryCard(
            trail = trail,
            entries = entries,
            scopeLabel = ::place,
            actions = emptyList(),
            actionStore = graph.quickActions,
            onAction = {},
            onConfigureActions = {},
            onEdit = { entry ->
                trail.close()
                editing = entry
            },
            onAddAnother = null,
            onEntryLink = { target ->
                val ids = matcher.lookup(target)
                if (ids.isNotEmpty()) {
                    trail.follow(ids)
                } else {
                    Toast.makeText(context, "No entry for “$target” yet. Hold the word in a book to write one.", Toast.LENGTH_LONG).show()
                }
            },
            // Over the graph, another entry can be tapped while one is open.
            modal = !showingGraph,
        )
        if (choosingPlace) {
            NewEntrySheet(
                word = query.trim(),
                shelves = shelves,
                books = books,
                onPick = { scope, scopeId ->
                    choosingPlace = false
                    editing = Entry(headword = query.trim(), scope = scope, scopeId = scopeId)
                },
                onDismiss = { choosingPlace = false },
            )
        }
        // Kept while the editor slides away, so it doesn't change on the way out.
        var editingPlace by remember { mutableStateOf("") }
        val shownEditing = editing
        if (shownEditing != null) editingPlace = place(shownEditing)
        EntryEditor(
            entry = editing,
            shelfName = null,
            suggestedShelf = "",
            language = shownEditing?.takeIf { it.scope == Scope.BOOK }?.let { entry -> books.firstOrNull { it.id == entry.scopeId }?.language } ?: "en",
            actions = emptyList(),
            actionStore = graph.quickActions,
            onAction = { _, _ -> },
            onConfigureActions = {},
            onSave = { edited, _ ->
                val original = editing ?: return@EntryEditor
                editing = null
                // Edited here, it stays where it shows; the reader is where entries move.
                graph.scope.launch(Dispatchers.IO) { dictionary.save(edited.copy(scope = original.scope, scopeId = original.scopeId), origin = null) }
            },
            onDelete = { entry ->
                editing = null
                graph.scope.launch(Dispatchers.IO) { dictionary.delete(entry.id) }
            },
            onClose = { editing = null },
            linkable = entries,
            place = editingPlace,
        )
    }
}

@Composable
private fun PlaceHeader(place: String, count: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(place, style = MaterialTheme.typography.titleSmall, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (count == 1) "1 entry" else "$count entries", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, preview: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(entry.headword, style = MaterialTheme.typography.titleMedium)
        if (entry.aliases.isNotEmpty()) {
            Text("also ${entry.aliases.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            preview.ifEmpty { "No notes yet." },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun Message(text: String, modifier: Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Where a new entry ([word], if one was searched for) will show: the library, a shelf, or one book. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntrySheet(word: String, shelves: List<Shelf>, books: List<Book>, onPick: (Scope, String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                if (word.isEmpty()) "New entry, for" else "“$word”, for",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            PlaceOption(R.drawable.ic_library, "Library", "Every book: for general words") { onPick(Scope.LIBRARY, null) }
            for (shelf in shelves) {
                PlaceOption(R.drawable.ic_shelf, shelf.name, "Every book on the shelf") { onPick(Scope.SHELF, shelf.id.toString()) }
            }
            for (book in books) {
                PlaceOption(R.drawable.ic_book, book.title, "Only this book") { onPick(Scope.BOOK, book.id) }
            }
        }
    }
}

@Composable
private fun PlaceOption(@DrawableRes icon: Int, name: String, meaning: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(meaning) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
