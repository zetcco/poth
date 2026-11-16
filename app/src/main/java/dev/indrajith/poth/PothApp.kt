package dev.indrajith.poth

import android.app.Application
import android.content.Context
import dev.indrajith.poth.data.BookFiles
import dev.indrajith.poth.data.BookImporter
import dev.indrajith.poth.data.Books
import dev.indrajith.poth.data.PothDatabase
import dev.indrajith.poth.data.ReadingLog
import java.io.File
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
)

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
        )
        scope.launch(Dispatchers.IO) { books.load() }
    }
}
