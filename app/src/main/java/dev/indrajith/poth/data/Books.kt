package dev.indrajith.poth.data

import android.database.Cursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where reading is: a spine index and a character offset in that chapter. */
data class Position(val spine: Int, val char: Int)

data class Book(
    val id: String,             // SHA-256 of the EPUB file
    val title: String,
    val author: String,         // "" when the book names none
    val series: String?,
    val seriesIndex: Double?,
    val language: String?,
    val fileName: String,       // as it was called when it was picked
    val fileSize: Long,
    val addedAt: Long,
    val openedAt: Long?,
    val progress: Double? = null, // 0 to 1; null until the book has been read with its pages counted
)

/** The books table. Every call reads or writes the disk: call them off the main thread. */
class Books(private val db: PothDatabase) {
    private val _all = MutableStateFlow<List<Book>>(emptyList())

    /** Every book, the most recently opened (or added) first. Empty until [load] has run. */
    val all: StateFlow<List<Book>> = _all

    fun load() {
        _all.value = db.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM books ORDER BY COALESCE(opened_at, added_at) DESC",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toBook()) } }
    }

    fun get(id: String): Book? =
        db.readableDatabase.rawQuery("SELECT $COLUMNS FROM books WHERE id = ?", arrayOf(id))
            .use { cursor -> if (cursor.moveToFirst()) cursor.toBook() else null }

    /**
     * Adds [book]. If it is already there, only what comes from the file is updated: when it was
     * added, when it was last opened and where reading stopped are kept.
     */
    fun put(book: Book) {
        db.writableDatabase.execSQL(
            """
            INSERT INTO books (id, title, author, series, series_index, language, file_name, file_size, added_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                title = excluded.title, author = excluded.author, series = excluded.series,
                series_index = excluded.series_index, language = excluded.language,
                file_name = excluded.file_name, file_size = excluded.file_size
            """,
            arrayOf<Any?>(
                book.id, book.title, book.author, book.series, book.seriesIndex, book.language,
                book.fileName, book.fileSize, book.addedAt,
            ),
        )
        load()
    }

    /** Where reading stopped; the start of the book if it has not been opened. */
    fun position(id: String): Position =
        db.readableDatabase.rawQuery("SELECT position_spine, position_char FROM books WHERE id = ?", arrayOf(id))
            .use { cursor -> if (cursor.moveToFirst()) Position(cursor.getInt(0), cursor.getInt(1)) else Position(0, 0) }

    fun savePosition(id: String, position: Position) {
        db.writableDatabase.execSQL(
            "UPDATE books SET position_spine = ?, position_char = ? WHERE id = ?",
            arrayOf<Any?>(position.spine, position.char, id),
        )
    }

    fun saveProgress(id: String, progress: Double) {
        db.writableDatabase.execSQL("UPDATE books SET progress = ? WHERE id = ?", arrayOf<Any?>(progress.coerceIn(0.0, 1.0), id))
        load()
    }

    fun markOpened(id: String, at: Long) {
        db.writableDatabase.execSQL("UPDATE books SET opened_at = ? WHERE id = ?", arrayOf<Any?>(at, id))
        load()
    }

    private companion object {
        const val COLUMNS =
            "id, title, author, series, series_index, language, file_name, file_size, added_at, opened_at, progress"

        fun Cursor.toBook() = Book(
            id = getString(0),
            title = getString(1),
            author = getString(2),
            series = if (isNull(3)) null else getString(3),
            seriesIndex = if (isNull(4)) null else getDouble(4),
            language = if (isNull(5)) null else getString(5),
            fileName = getString(6),
            fileSize = getLong(7),
            addedAt = getLong(8),
            openedAt = if (isNull(9)) null else getLong(9),
            progress = if (isNull(10)) null else getDouble(10),
        )
    }
}
