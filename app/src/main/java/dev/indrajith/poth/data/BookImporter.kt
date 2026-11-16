package dev.indrajith.poth.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.indrajith.poth.epub.EpubBook
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The imported EPUBs, each named by its SHA-256, which is also the book's ID. */
class BookFiles(val dir: File) {
    fun of(id: String) = File(dir, "$id.epub")
}

/** Copies a picked EPUB into the app's own storage and adds it to [books]. */
class BookImporter(
    private val resolver: ContentResolver,
    private val books: Books,
    private val files: BookFiles,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Picking the same file twice gives the same book, stored once, with its reading position
     * kept. Throws [dev.indrajith.poth.epub.EpubException] if the file is not a readable EPUB
     * (nothing is kept then) and [IOException] if it cannot be copied.
     */
    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        files.dir.mkdirs()
        val temp = File.createTempFile("import-", ".part", files.dir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = resolver.openInputStream(uri) ?: throw IOException("the file could not be opened")
            DigestInputStream(input, digest).use { hashing -> temp.outputStream().use { hashing.copyTo(it) } }
            val id = digest.digest().joinToString("") { "%02x".format(it) }

            val metadata = EpubBook.open(temp).use { it.metadata }
            val target = files.of(id)
            if (!target.exists() && !temp.renameTo(target)) throw IOException("the book could not be stored")

            books.put(
                Book(
                    id = id,
                    title = metadata.title,
                    author = metadata.authors.joinToString(", "),
                    series = metadata.series,
                    seriesIndex = metadata.seriesIndex,
                    language = metadata.language,
                    fileName = displayName(uri) ?: target.name,
                    fileSize = target.length(),
                    addedAt = now(),
                    openedAt = null,
                ),
            )
            books.get(id) ?: throw IOException("the book was not saved")
        } finally {
            temp.delete() // already gone if it became the book's file
        }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
}
