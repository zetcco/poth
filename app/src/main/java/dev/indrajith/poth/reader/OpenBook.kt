package dev.indrajith.poth.reader

import android.graphics.BitmapFactory
import android.util.Log
import dev.indrajith.poth.data.Position
import dev.indrajith.poth.epub.Chapter
import dev.indrajith.poth.epub.ChapterParser
import dev.indrajith.poth.epub.Css
import dev.indrajith.poth.epub.CssRule
import dev.indrajith.poth.epub.EpubBook
import dev.indrajith.poth.epub.EpubException
import java.io.Closeable
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A book open for reading: its chapters, stylesheets and images, read from the EPUB as needed. */
class OpenBook(private val epub: EpubBook) : Closeable {
    /** The spine items read in order; non-linear ones (notes and the like) are left out. */
    val readingOrder: List<Int> = epub.spine.indices.filter { epub.spine[it].linear }

    /** For hyphenation. A bare "en" would find no patterns, so it is taken as US English. */
    val locale: Locale = epub.metadata.language?.let(Locale::forLanguageTag)
        ?.takeIf { it.language.isNotEmpty() }
        ?.let { if (it.language == "en" && it.country.isEmpty()) Locale.US else it }
        ?: Locale.getDefault()

    // The screen may close the book while a chapter is still being laid out in the background.
    private var closed = false
    private val stylesheets = HashMap<String, List<CssRule>>()
    private val parser = ChapterParser { path ->
        stylesheets.getOrPut(path) {
            try {
                Css.parse(epub.read(path).decodeToString())
            } catch (e: EpubException) {
                emptyList()
            }
        }
    }

    /** One spine item, parsed. A chapter that cannot be read comes back empty and is skipped. */
    @Synchronized
    fun chapter(spine: Int): Chapter {
        if (closed) return Chapter(emptyList())
        val path = epub.spine[spine].item.path
        return try {
            parser.parse(epub.html(path), path)
        } catch (e: EpubException) {
            Log.w("Poth", "Skipping $path", e)
            Chapter(emptyList())
        } catch (e: IOException) {
            Log.w("Poth", "Skipping $path", e)
            Chapter(emptyList())
        }
    }

    /** An image, decoded no larger than needed to fill [maxWidth] × [maxHeight]. */
    @Synchronized
    fun image(path: String, maxWidth: Int, maxHeight: Int): DecodedImage? {
        if (closed) return null
        val bytes = try {
            epub.read(path)
        } catch (e: EpubException) {
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return DecodedImage(bitmap, bounds.outWidth, bounds.outHeight)
    }

    @Synchronized
    override fun close() {
        closed = true
        epub.close()
    }
}

/**
 * The book laid out for one [PageSpec]. Chapters are laid out when first needed, off the main
 * thread, and the last few are kept.
 */
class Pages(private val book: OpenBook, spec: PageSpec) {
    private val paginator = Paginator(spec, book.locale, book::image)
    private val mutex = Mutex()
    private val laidOut = LinkedHashMap<Int, List<Page>>(8, 0.75f, true) // least recently used first

    suspend fun of(spine: Int): List<Page> = mutex.withLock {
        laidOut[spine] ?: withContext(Dispatchers.Default) {
            paginator.paginate(spine, book.chapter(spine))
        }.also {
            laidOut[spine] = it
            while (laidOut.size > KEPT) laidOut.remove(laidOut.keys.first())
        }
    }

    /** The page showing [position], or the book's first page if the position is not in it. */
    suspend fun at(position: Position): Page? {
        if (position.spine in book.readingOrder) {
            val pages = of(position.spine)
            pages.lastOrNull { it.start <= position.char }?.let { return it }
        }
        return firstFrom(0)
    }

    suspend fun next(page: Page): Page? {
        val pages = of(page.spine)
        if (page.index + 1 < pages.size) return pages[page.index + 1]
        return firstFrom(book.readingOrder.indexOf(page.spine) + 1)
    }

    suspend fun previous(page: Page): Page? {
        if (page.index > 0) return of(page.spine)[page.index - 1]
        var order = book.readingOrder.indexOf(page.spine) - 1
        while (order >= 0) {
            of(book.readingOrder[order]).lastOrNull()?.let { return it }
            order--
        }
        return null
    }

    /** Lays out the chapters either side of [spine], so turning into them does not wait. */
    suspend fun prepareAround(spine: Int) {
        val order = book.readingOrder.indexOf(spine)
        book.readingOrder.getOrNull(order + 1)?.let { of(it) }
        book.readingOrder.getOrNull(order - 1)?.let { of(it) }
    }

    private suspend fun firstFrom(order: Int): Page? {
        for (i in order until book.readingOrder.size) of(book.readingOrder[i]).firstOrNull()?.let { return it }
        return null
    }

    private companion object {
        const val KEPT = 5
    }
}
