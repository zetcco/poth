package dev.indrajith.poth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.indrajith.poth.Graph
import dev.indrajith.poth.epub.EpubBook
import dev.indrajith.poth.epub.EpubException
import dev.indrajith.poth.epub.PlainText
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ready(val title: String, val chapters: List<List<String>>) : Loaded
    data class Failed(val reason: String) : Loaded
}

private val pageText = TextStyle(
    color = Page.text,
    fontFamily = FontFamily.Serif,
    fontSize = 20.sp,
    lineHeight = 1.45.em,
    textAlign = TextAlign.Justify,
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph,
)

/**
 * Iteration 0's reader: the whole book as plain paragraphs in one scrolling column, to check that
 * books are read correctly. Iteration 1 replaces it with real pages.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(graph: Graph, bookId: String) {
    val state by produceState<Loaded>(Loaded.Loading, bookId) {
        value = withContext(Dispatchers.IO) {
            try {
                graph.books.markOpened(bookId, System.currentTimeMillis())
                EpubBook.open(graph.files.of(bookId)).use { epub ->
                    Loaded.Ready(
                        title = epub.metadata.title,
                        chapters = epub.spine.filter { it.linear }.map { PlainText.paragraphs(epub.html(it.item.path)) },
                    )
                }
            } catch (e: EpubException) {
                Loaded.Failed("Can't read this book: ${e.message}")
            } catch (e: IOException) {
                Loaded.Failed("Can't open this book: ${e.message}")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Page.background), contentAlignment = Alignment.Center) {
        when (val loaded = state) {
            Loaded.Loading -> LoadingIndicator()
            is Loaded.Failed -> Text(
                loaded.reason,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
            is Loaded.Ready -> PlainBook(loaded)
        }
    }
}

@Composable
private fun PlainBook(book: Loaded.Ready) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = insets.calculateTopPadding() + 24.dp,
            bottom = insets.calculateBottomPadding() + 48.dp,
        ),
    ) {
        item {
            Text(
                book.title,
                style = pageText.copy(fontSize = 26.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            )
        }
        book.chapters.forEachIndexed { index, paragraphs ->
            if (index > 0) item { ChapterBreak() }
            items(paragraphs) { Text(it, style = pageText, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun ChapterBreak() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("§", style = pageText.copy(textAlign = TextAlign.Center))
    }
}
