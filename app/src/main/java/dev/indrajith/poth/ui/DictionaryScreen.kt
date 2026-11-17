package dev.indrajith.poth.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.R
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
 * The whole dictionary: every entry, by where it shows (the library, then each shelf, then
 * each book), and a search through words and notes. Tapping an entry shows its card; links in
 * it lead on to other entries, and it can be edited from there.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun DictionaryScreen(graph: Graph, onBack: () -> Unit) {
    val dictionary = graph.dictionary
    val changes by dictionary.changes.collectAsStateWithLifecycle()
    val shelfChanges by graph.shelves.changes.collectAsStateWithLifecycle()
    val books by graph.books.all.collectAsStateWithLifecycle()
    val loaded by produceState<List<Entry>?>(null, changes) { value = withContext(Dispatchers.IO) { dictionary.all() } }
    val shelves by produceState(emptyList<Shelf>(), shelfChanges) { value = withContext(Dispatchers.IO) { graph.shelves.all() } }
    val entries = loaded.orEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    var cardIds by remember { mutableStateOf(emptyList<Long>()) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    val context = LocalContext.current

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
    val matcher = remember(entries) { Matcher(entries.flatMap { entry -> entry.terms.map { it to entry.id } }) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Dictionary") },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                        },
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            },
        ) { padding ->
            when {
                loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
                entries.isEmpty() -> Message("No entries yet. Hold a word while reading to write about it.", Modifier.padding(padding))
                groups.isEmpty() -> Message("Nothing matches “${query.trim()}”.", Modifier.padding(padding))
                else -> LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                    for (group in groups) {
                        stickyHeader(key = "place:${group.place}") { PlaceHeader(group.place, group.entries.size) }
                        items(group.entries, key = { it.id }) { entry -> EntryRow(entry) { cardIds = listOf(entry.id) } }
                    }
                }
            }
        }

        EntryCard(
            entries = entries.filter { it.id in cardIds },
            scopeLabel = ::place,
            actions = emptyList(),
            actionStore = graph.quickActions,
            onAction = {},
            onConfigureActions = {},
            onEdit = { entry ->
                cardIds = emptyList()
                editing = entry
            },
            onAddAnother = null,
            onEntryLink = { target ->
                val ids = matcher.lookup(target)
                if (ids.isNotEmpty()) {
                    cardIds = ids
                } else {
                    Toast.makeText(context, "No entry for “$target” yet. Hold the word in a book to write one.", Toast.LENGTH_LONG).show()
                }
            },
            onClose = { cardIds = emptyList() },
        )
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
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val preview = remember(entry.body) { Browse.preview(entry.body) }
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
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
