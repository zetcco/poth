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
                    -- How the page was reached: see reader.Arrival.
                    arrived_by TEXT NOT NULL
                )
                """,
                "CREATE INDEX page_views_by_time ON page_views (shown_at)",
                "CREATE INDEX page_views_by_book ON page_views (book_id, shown_at)",
            ),
            // 2: pages per chapter, per layout (screen size and type), as comma-separated counts.
            listOf(
                """
                CREATE TABLE page_counts (
                    book_id TEXT NOT NULL REFERENCES books (id) ON DELETE CASCADE,
                    layout TEXT NOT NULL,
                    counts TEXT NOT NULL,
                    PRIMARY KEY (book_id, layout)
                )
                """,
            ),
            // 3: the dictionary. An entry is a headword, other ways of writing it (one per line)
            // and notes in Markdown, seen by one book, one shelf's books, or all books (the
            // library, scope_id NULL). Every save is also kept as a revision, with the book and
            // place being read at the time, for showing later where each part was written.
            listOf(
                """
                CREATE TABLE entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    headword TEXT NOT NULL,
                    aliases TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    scope TEXT NOT NULL,
                    scope_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """,
                "CREATE INDEX entries_by_scope ON entries (scope, scope_id)",
                """
                CREATE TABLE entry_revisions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_id INTEGER NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
                    headword TEXT NOT NULL,
                    aliases TEXT NOT NULL,
                    body TEXT NOT NULL,
                    scope TEXT NOT NULL,
                    scope_id TEXT,
                    saved_at INTEGER NOT NULL,
                    book_id TEXT,
                    spine_index INTEGER,
                    position_char INTEGER
                )
                """,
                "CREATE INDEX entry_revisions_by_entry ON entry_revisions (entry_id, saved_at)",
            ),
            // 4: how far through each book reading is, 0 to 1, for the book list. NULL until known.
            listOf("ALTER TABLE books ADD COLUMN progress REAL"),
        )
    }
}
