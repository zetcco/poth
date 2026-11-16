package dev.indrajith.poth.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.indrajith.poth.Graph
import dev.indrajith.poth.R
import dev.indrajith.poth.data.Book
import dev.indrajith.poth.epub.EpubException
import java.io.IOException
import kotlinx.coroutines.launch

private const val EPUB_MIME = "application/epub+zip"

/** The books, and "Add book", which copies an EPUB in from the file picker and opens it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BooksScreen(graph: Graph, onOpen: (String) -> Unit) {
    val books by graph.books.all.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Poth") },
                subtitle = { Text(if (books.size == 1) "1 book" else "${books.size} books") },
                scrollBehavior = scrollBehavior,
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
        } else {
            BookList(books, importing, padding, onOpen)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookList(books: List<Book>, importing: Boolean, padding: PaddingValues, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = padding) {
        if (importing) {
            item { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        }
        items(books, key = { it.id }) { book ->
            ListItem(
                onClick = { onOpen(book.id) },
                supportingContent = book.author.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                overlineContent = book.series?.let { series -> { Text(seriesLabel(series, book.seriesIndex)) } },
            ) {
                Text(book.title)
            }
        }
    }
}

/** "The Stormlight Archive 2", or "… 2.5" for an in-between book. */
private fun seriesLabel(series: String, index: Double?): String = when {
    index == null -> series
    index % 1.0 == 0.0 -> "$series ${index.toLong()}"
    else -> "$series $index"
}
