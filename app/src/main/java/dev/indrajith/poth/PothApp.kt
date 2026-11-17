package dev.indrajith.poth

import android.app.Application
import android.content.Context
import dev.indrajith.poth.actions.QuickActions
import dev.indrajith.poth.data.BookFiles
import dev.indrajith.poth.data.BookImporter
import dev.indrajith.poth.data.Covers
import dev.indrajith.poth.data.Dictionary
import dev.indrajith.poth.data.PageCounts
import dev.indrajith.poth.data.Books
import dev.indrajith.poth.data.PothDatabase
import dev.indrajith.poth.data.Prefs
import dev.indrajith.poth.data.ReadingLog
import dev.indrajith.poth.data.Shelves
import dev.indrajith.poth.stats.ReadingRules
import dev.indrajith.poth.stats.ReadingStats
import dev.indrajith.poth.stats.ReadingSummary
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The app's long-lived objects. Screens reach them through [graph]. */
class Graph(
    val scope: CoroutineScope,
    val db: PothDatabase,
    val books: Books,
    val files: BookFiles,
    val importer: BookImporter,
    val log: ReadingLog,
    val pageCounts: PageCounts,
    val prefs: Prefs,
    val dictionary: Dictionary,
    val quickActions: QuickActions,
    val shelves: Shelves,
    val covers: Covers,
) {
    /** Reading as the rules count it, from page views since [since]. Call off the main thread. */
    fun readingSummary(zone: ZoneId, since: Long = 0): ReadingSummary =
        ReadingStats.summarize(log.since(since), zone, ReadingRules(speedSince = prefs.speedSince))
}

interface GraphOwner {
    val graph: Graph
}

fun Context.graph(): Graph = (applicationContext as GraphOwner).graph

class PothApp : Application(), GraphOwner {
    override lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val db = PothDatabase(this)
        val books = Books(db)
        val files = BookFiles(File(filesDir, "books"))
        graph = Graph(
            scope = scope,
            db = db,
            books = books,
            files = files,
            importer = BookImporter(contentResolver, books, files),
            log = ReadingLog(db),
            pageCounts = PageCounts(db),
            prefs = Prefs(this),
            dictionary = Dictionary(db),
            quickActions = QuickActions(this),
            shelves = Shelves(db),
            covers = Covers(File(filesDir, "covers")),
        )
        scope.launch(Dispatchers.IO) { books.load() }
    }
}
