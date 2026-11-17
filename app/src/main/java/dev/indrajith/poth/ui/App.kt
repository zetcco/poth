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
 * The book list, the book that is open, the reading stats, or the whole dictionary. Back returns
 * to the list (from the stats or the dictionary, to the book if one was open). [openStats] is
 * the widget asking for the stats.
 */
@Composable
fun PothRoot(openStats: MutableStateFlow<Boolean>) {
    val graph = LocalContext.current.graph()
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var dictionaryOpen by rememberSaveable { mutableStateOf(false) }

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
        dictionaryOpen -> {
            BackHandler { dictionaryOpen = false }
            DictionaryScreen(graph, onBack = { dictionaryOpen = false })
        }
        bookId != null -> {
            BackHandler { openBookId = null }
            ReaderScreen(graph, bookId, onBack = { openBookId = null }, onDictionary = { dictionaryOpen = true })
        }
        else -> BooksScreen(graph, onOpen = { openBookId = it }, onStats = { statsOpen = true }, onDictionary = { dictionaryOpen = true })
    }
}
