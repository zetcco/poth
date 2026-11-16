package dev.indrajith.poth.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.indrajith.poth.epub.EpubBook
import dev.indrajith.poth.epub.EpubException
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/** Small copies of books' covers for the book list, made from the EPUB the first time they are needed. */
class Covers(private val dir: File) {
    private fun file(bookId: String) = File(dir, "$bookId.jpg")

    /** The book's cover thumbnail, made now if need be; null if the book has no cover. Slow the first time. */
    fun of(bookId: String, epub: File): File? {
        val cover = file(bookId)
        if (cover.exists()) return cover
        val bytes = try {
            EpubBook.open(epub).use { book -> book.cover()?.let(book::read) }
        } catch (e: EpubException) {
            null
        } catch (e: IOException) {
            null
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = min(1f, HEIGHT.toFloat() / decoded.height)
        val thumbnail = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        dir.mkdirs()
        val temp = File(dir, "$bookId.part")
        temp.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return if (temp.renameTo(cover)) cover else null
    }

    private companion object {
        const val HEIGHT = 480 // pixels: sharp at the list's size on a phone
    }
}
