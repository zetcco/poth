package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.indrajith.poth.graph

/** The book list, or the book that is open. Back from a book returns to the list. */
@Composable
fun PothRoot() {
    val graph = LocalContext.current.graph()
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val bookId = openBookId
    if (bookId == null) {
        BooksScreen(graph, onOpen = { openBookId = it })
    } else {
        BackHandler { openBookId = null }
        ReaderScreen(graph, bookId)
    }
}
