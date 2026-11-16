package dev.indrajith.poth.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Shelf(val id: Long, val name: String)

/**
 * Shelves group books (a series, usually); entries for a shelf show in all its books. For now a
 * book is on at most one shelf. Call off the main thread.
 */
class Shelves(private val db: PothDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val _changes = MutableStateFlow(0)

    /** Goes up whenever a shelf, or which books are on it, changes. */
    val changes: StateFlow<Int> = _changes

    fun all(): List<Shelf> =
        db.readableDatabase.rawQuery("SELECT id, name FROM shelves ORDER BY name COLLATE NOCASE", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(Shelf(cursor.getLong(0), cursor.getString(1))) } }

    fun of(bookId: String): Shelf? = db.readableDatabase.rawQuery(
        "SELECT s.id, s.name FROM shelves s JOIN book_shelves b ON b.shelf_id = s.id WHERE b.book_id = ? ORDER BY s.name COLLATE NOCASE",
        arrayOf(bookId),
    ).use { cursor -> if (cursor.moveToFirst()) Shelf(cursor.getLong(0), cursor.getString(1)) else null }

    /** Which shelf each book is on; books on none are not in it. */
    fun byBook(): Map<String, Shelf> = db.readableDatabase.rawQuery(
        "SELECT b.book_id, s.id, s.name FROM book_shelves b JOIN shelves s ON s.id = b.shelf_id",
        null,
    ).use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), Shelf(cursor.getLong(1), cursor.getString(2))) } }

    /**
     * Puts the book on the shelf called [name], making the shelf if there is none by that name
     * (ignoring case), and takes it off any other.
     */
    fun put(bookId: String, name: String): Shelf {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.execSQL("INSERT OR IGNORE INTO shelves (name, created_at) VALUES (?, ?)", arrayOf<Any?>(name.trim(), now()))
            val shelf = database.rawQuery("SELECT id, name FROM shelves WHERE name = ? COLLATE NOCASE", arrayOf(name.trim()))
                .use { it.moveToFirst(); Shelf(it.getLong(0), it.getString(1)) }
            database.execSQL("DELETE FROM book_shelves WHERE book_id = ?", arrayOf<Any?>(bookId))
            database.execSQL("INSERT INTO book_shelves (book_id, shelf_id) VALUES (?, ?)", arrayOf<Any?>(bookId, shelf.id))
            database.setTransactionSuccessful()
            _changes.value++
            return shelf
        } finally {
            database.endTransaction()
        }
    }

    /** Takes the book off its shelf. The shelf stays, with its entries, even if it is now empty. */
    fun takeOff(bookId: String) {
        db.writableDatabase.execSQL("DELETE FROM book_shelves WHERE book_id = ?", arrayOf<Any?>(bookId))
        _changes.value++
    }

    /** Renames a shelf. False if another shelf already has that name. */
    fun rename(id: Long, name: String): Boolean {
        val taken = db.readableDatabase.rawQuery("SELECT 1 FROM shelves WHERE name = ? COLLATE NOCASE AND id != ?", arrayOf(name.trim(), id.toString()))
            .use { it.moveToFirst() }
        if (taken) return false
        db.writableDatabase.execSQL("UPDATE shelves SET name = ? WHERE id = ?", arrayOf<Any?>(name.trim(), id))
        _changes.value++
        return true
    }
}
