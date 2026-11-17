package dev.indrajith.poth.data

import android.database.Cursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which books see an entry: one book, every book on a shelf, or every book. */
enum class Scope(val key: String) {
    BOOK("book"),
    SHELF("shelf"),
    LIBRARY("library");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: LIBRARY
    }
}

/** A dictionary entry: a word (and other ways of writing it) and free notes in Markdown. */
data class Entry(
    val id: Long = 0,               // 0 until saved
    val headword: String,
    val aliases: List<String> = emptyList(),
    val body: String = "",
    val scope: Scope,
    val scopeId: String? = null,    // the book's ID, the shelf's ID, or null for the library
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Everything this entry is matched by in the text. */
    val terms get() = listOf(headword) + aliases
}

/** Where an entry was saved from: the book being read and the place in it. */
data class Origin(val bookId: String, val position: Position)

/**
 * The dictionary. Every save also keeps the entry as it was, with where it was saved from, so its
 * history can be shown later. Which shelf's entries a book sees is [Shelves]' business. Call off
 * the main thread.
 */
class Dictionary(private val db: PothDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val _changes = MutableStateFlow(0)

    /** Goes up whenever an entry changes. */
    val changes: StateFlow<Int> = _changes

    /** The entries a book sees: its own, its shelves', the library's. The most specific first. */
    fun entriesFor(bookId: String): List<Entry> = db.readableDatabase.rawQuery(
        """
        SELECT $COLUMNS FROM entries
        WHERE (scope = 'book' AND scope_id = ?)
           OR (scope = 'shelf' AND scope_id IN (SELECT CAST(shelf_id AS TEXT) FROM book_shelves WHERE book_id = ?))
           OR scope = 'library'
        ORDER BY CASE scope WHEN 'book' THEN 0 WHEN 'shelf' THEN 1 ELSE 2 END, headword COLLATE NOCASE
        """,
        arrayOf(bookId, bookId),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toEntry()) } }

    /** Every entry, wherever it shows, by word. */
    fun all(): List<Entry> = db.readableDatabase.rawQuery(
        "SELECT $COLUMNS FROM entries ORDER BY headword COLLATE NOCASE, id",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toEntry()) } }

    /** Adds or updates [entry] and keeps this version of it. Returns it as saved. */
    fun save(entry: Entry, origin: Origin?): Entry {
        val at = now()
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val aliases = entry.aliases.map(String::trim).filter(String::isNotEmpty).joinToString("\n")
            val id = if (entry.id == 0L) {
                database.execSQL(
                    "INSERT INTO entries (headword, aliases, body, scope, scope_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(entry.headword.trim(), aliases, entry.body, entry.scope.key, entry.scopeId, at, at),
                )
                database.rawQuery("SELECT last_insert_rowid()", null).use { it.moveToFirst(); it.getLong(0) }
            } else {
                database.execSQL(
                    "UPDATE entries SET headword = ?, aliases = ?, body = ?, scope = ?, scope_id = ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any?>(entry.headword.trim(), aliases, entry.body, entry.scope.key, entry.scopeId, at, entry.id),
                )
                entry.id
            }
            database.execSQL(
                """
                INSERT INTO entry_revisions (entry_id, headword, aliases, body, scope, scope_id, saved_at, book_id, spine_index, position_char)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                arrayOf<Any?>(
                    id, entry.headword.trim(), aliases, entry.body, entry.scope.key, entry.scopeId, at,
                    origin?.bookId, origin?.position?.spine, origin?.position?.char,
                ),
            )
            database.setTransactionSuccessful()
            _changes.value++
            return entry.copy(id = id, createdAt = if (entry.id == 0L) at else entry.createdAt, updatedAt = at)
        } finally {
            database.endTransaction()
        }
    }

    fun delete(id: Long) {
        db.writableDatabase.execSQL("DELETE FROM entries WHERE id = ?", arrayOf<Any?>(id))
        _changes.value++
    }

    private companion object {
        const val COLUMNS = "id, headword, aliases, body, scope, scope_id, created_at, updated_at"

        fun Cursor.toEntry() = Entry(
            id = getLong(0),
            headword = getString(1),
            aliases = getString(2).split('\n').filter(String::isNotEmpty),
            body = getString(3),
            scope = Scope.of(getString(4)),
            scopeId = if (isNull(5)) null else getString(5),
            createdAt = getLong(6),
            updatedAt = getLong(7),
        )
    }
}
