package dev.indrajith.poth.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * poth's SQLite database. The schema is plain SQL: each entry in [MIGRATIONS] takes it one version
 * up, and a new install runs them all, so there is one description of the schema, not two.
 */
class PothDatabase(context: Context, name: String? = "poth.db") :
    SQLiteOpenHelper(context, name, null, MIGRATIONS.size) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) = migrate(db, 0, MIGRATIONS.size)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = migrate(db, oldVersion, newVersion)

    private fun migrate(db: SQLiteDatabase, from: Int, to: Int) {
        for (version in from until to) MIGRATIONS[version].forEach(db::execSQL)
    }

    private companion object {
        val MIGRATIONS: List<List<String>> = listOf(
            // 1: books, shelves, and the raw reading log.
            listOf(
                // A book is its file's content: the ID is the SHA-256 of the EPUB, so moving or
                // renaming the file, or picking it twice, is still the same book.
                """
                CREATE TABLE books (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL DEFAULT '',
                    series TEXT,
                    series_index REAL,
                    language TEXT,
                    file_name TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    added_at INTEGER NOT NULL,
                    opened_at INTEGER,
                    -- Where reading stopped: the spine item and a character offset in its text.
                    position_spine INTEGER NOT NULL DEFAULT 0,
                    position_char INTEGER NOT NULL DEFAULT 0
                )
                """,
                // Shelves group books (a series, usually); dictionary entries can belong to one.
                """
                CREATE TABLE shelves (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    created_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE book_shelves (
                    book_id TEXT NOT NULL REFERENCES books (id) ON DELETE CASCADE,
                    shelf_id INTEGER NOT NULL REFERENCES shelves (id) ON DELETE CASCADE,
                    PRIMARY KEY (book_id, shelf_id)
                )
                """,
                // One row per page that was on screen, written when it leaves the screen. These
                // are raw facts only: whether a page or a session counts as reading is decided
                // when the stats are worked out, so those rules can change and still apply to
                // everything read so far. No foreign key: the history outlives a removed book.
                """
                CREATE TABLE page_views (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id TEXT NOT NULL,
                    shown_at INTEGER NOT NULL,
                    hidden_at INTEGER NOT NULL,
                    spine_index INTEGER NOT NULL,
                    start_char INTEGER NOT NULL,
                    end_char INTEGER NOT NULL,
                    words INTEGER NOT NULL,
                    -- How the page was reached: 'open', 'next', 'previous' or 'jump'.
                    arrived_by TEXT NOT NULL
                )
                """,
                "CREATE INDEX page_views_by_time ON page_views (shown_at)",
                "CREATE INDEX page_views_by_book ON page_views (book_id, shown_at)",
            ),
        )
    }
}
