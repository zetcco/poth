package dev.indrajith.poth.data

/** One page that was on screen. Times are milliseconds since the epoch. */
data class PageView(
    val bookId: String,
    val shownAt: Long,
    val hiddenAt: Long,
    val spine: Int,
    val startChar: Int,
    val endChar: Int,
    val words: Int,
    val arrivedBy: String,
)

/** The page_views table: what was on screen, and for how long. Call off the main thread. */
class ReadingLog(private val db: PothDatabase) {
    fun add(view: PageView) {
        db.writableDatabase.execSQL(
            """
            INSERT INTO page_views (book_id, shown_at, hidden_at, spine_index, start_char, end_char, words, arrived_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            arrayOf<Any?>(view.bookId, view.shownAt, view.hiddenAt, view.spine, view.startChar, view.endChar, view.words, view.arrivedBy),
        )
    }
}
