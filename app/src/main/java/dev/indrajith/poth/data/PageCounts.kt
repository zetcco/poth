package dev.indrajith.poth.data

/**
 * Pages per chapter of a book for one layout, so "18 of 1789" is there as soon as the book opens
 * again. Call off the main thread.
 */
class PageCounts(private val db: PothDatabase) {
    fun get(bookId: String, layout: String): IntArray? =
        db.readableDatabase.rawQuery("SELECT counts FROM page_counts WHERE book_id = ? AND layout = ?", arrayOf(bookId, layout))
            .use { cursor -> if (cursor.moveToFirst()) parse(cursor.getString(0)) else null }

    fun put(bookId: String, layout: String, counts: IntArray) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO page_counts (book_id, layout, counts) VALUES (?, ?, ?)",
            arrayOf<Any?>(bookId, layout, counts.joinToString(",")),
        )
    }

    private fun parse(text: String): IntArray? =
        if (text.isEmpty()) IntArray(0) else text.split(',').map { it.toIntOrNull() ?: return null }.toIntArray()
}
