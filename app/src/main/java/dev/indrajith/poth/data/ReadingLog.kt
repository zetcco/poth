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
    /** Every page view since [since] (milliseconds since the epoch), oldest first. */
    fun since(since: Long = 0): List<PageView> = db.readableDatabase.rawQuery(
        """
        SELECT book_id, shown_at, hidden_at, spine_index, start_char, end_char, words, arrived_by
        FROM page_views WHERE shown_at >= ? ORDER BY shown_at
        """,
        arrayOf(since.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PageView(
                        cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3),
                        cursor.getInt(4), cursor.getInt(5), cursor.getInt(6), cursor.getString(7),
                    ),
                )
            }
        }
    }

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
