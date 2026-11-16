package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.indrajith.poth.graph
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The book list, the book that is open, or the reading stats. Back returns to the list (from the
 * stats, to the book if one was open). [openStats] is the widget asking for the stats.
 */
@Composable
fun PothRoot(openStats: MutableStateFlow<Boolean>) {
    val graph = LocalContext.current.graph()
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }

    val statsAsked by openStats.collectAsState()
    LaunchedEffect(statsAsked) {
        if (statsAsked) {
            statsOpen = true
            openStats.value = false
        }
    }

    val bookId = openBookId
    when {
        statsOpen -> {
            BackHandler { statsOpen = false }
            StatsScreen(graph, onBack = { statsOpen = false })
        }
        bookId != null -> {
            BackHandler { openBookId = null }
            ReaderScreen(graph, bookId, onBack = { openBookId = null })
        }
        else -> BooksScreen(graph, onOpen = { openBookId = it }, onStats = { statsOpen = true })
    }
}
