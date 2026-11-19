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
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** A line of the contents, placed in the book: the spine item and the id it points to, if any. */
data class Section(val title: String, val depth: Int, val spine: Int, val fragment: String?)

/** A book open for reading: its chapters, stylesheets and images, read from the EPUB as needed. */
class OpenBook(private val epub: EpubBook) : Closeable {
    val title: String = epub.metadata.title
    val author: String = epub.metadata.authors.joinToString(", ")
    val series: String? = epub.metadata.series

    /** The spine items read in order; non-linear ones (notes and the like) are left out. */
    val readingOrder: List<Int> = epub.spine.indices.filter { epub.spine[it].linear }

    /** For hyphenation. A bare "en" would find no patterns, so it is taken as US English. */
    val locale: Locale = epub.metadata.language?.let(Locale::forLanguageTag)
        ?.takeIf { it.language.isNotEmpty() }
        ?.let { if (it.language == "en" && it.country.isEmpty()) Locale.US else it }
        ?: Locale.getDefault()

    /** The table of contents, without entries that point outside the reading order. */
    val contents: List<Section> = run {
        val spineByPath = epub.spine.withIndex().associate { (index, item) -> item.item.path to index }
        epub.toc.mapNotNull { entry ->
            spineByPath[entry.path]?.takeIf { it in readingOrder }?.let { Section(entry.title, entry.depth, it, entry.fragment) }
        }
    }

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
        val bytes = imageBytes(path) ?: return null
        val size = size(bytes) ?: return null
        var sample = 1
        while (size.width / (sample * 2) >= maxWidth && size.height / (sample * 2) >= maxHeight) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return DecodedImage(bitmap, size.width, size.height)
    }

    /** An image's file, as it is in the book. */
    @Synchronized
    fun imageFile(path: String): ByteArray? = imageBytes(path)

    /** Only an image's size, which is all that counting pages needs. */
    @Synchronized
    fun imageSize(path: String): DecodedImage? = imageBytes(path)?.let(::size)

    private fun imageBytes(path: String): ByteArray? {
        if (closed) return null
        return try {
            epub.read(path)
        } catch (e: EpubException) {
            null
        }
    }

    private fun size(bytes: ByteArray): DecodedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) DecodedImage(null, bounds.outWidth, bounds.outHeight) else null
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
    /** Saved page counts are stored under this: they hold only for the same layout. */
    val layoutKey = "v${Paginator.VERSION} ${spec.width}x${spec.height} ${spec.fontPx} ${spec.lineHeight}"

    private class LaidOut(val pages: List<Page>, val anchors: Map<String, Int>)

    private val paginator = Paginator(spec, book.locale, book::image)
    private val counter = Paginator(spec, book.locale) { path, _, _ -> book.imageSize(path) }
    private val mutex = Mutex()
    private val laidOut = LinkedHashMap<Int, LaidOut>(8, 0.75f, true) // least recently used first

    suspend fun of(spine: Int): List<Page> = laidOut(spine).pages

    private suspend fun laidOut(spine: Int): LaidOut = mutex.withLock {
        laidOut[spine] ?: withContext(Dispatchers.Default) {
            val chapter = book.chapter(spine)
            LaidOut(paginator.paginate(spine, chapter), chapter.anchors)
        }.also {
            laidOut[spine] = it
            while (laidOut.size > KEPT) laidOut.remove(laidOut.keys.first())
        }
    }

    /** The page showing [position], or the book's first page if the position is not in it. */
    suspend fun at(position: Position): Page? {
        if (position.spine in book.readingOrder) {
            of(position.spine).lastOrNull { it.start <= position.char }?.let { return it }
        }
        return firstFrom(0)
    }

    /** Where [section] starts: the page with its id on it, or its chapter's first page. */
    suspend fun at(section: Section): Page? {
        val chapter = laidOut(section.spine)
        val char = section.fragment?.let { chapter.anchors[it] } ?: 0
        return chapter.pages.lastOrNull { it.start <= char } ?: firstFrom(book.readingOrder.indexOf(section.spine))
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

    /** The contents entry [page] is in: the last one starting at or before the page does. */
    suspend fun sectionOf(page: Page): Section? {
        val order = book.readingOrder.indexOf(page.spine)
        val anchors = laidOut(page.spine).anchors
        return book.contents.lastOrNull { section ->
            val sectionOrder = book.readingOrder.indexOf(section.spine)
            sectionOrder < order || sectionOrder == order && (section.fragment?.let { anchors[it] } ?: 0) <= page.start
        }
    }

    /**
     * How many pages each chapter has, in reading order. Lays out every chapter not laid out
     * yet, without decoding images: a few seconds for a long book.
     */
    suspend fun count(): IntArray = withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        val counts = IntArray(book.readingOrder.size)
        for ((order, spine) in book.readingOrder.withIndex()) {
            counts[order] = mutex.withLock { laidOut[spine]?.pages?.size }
                ?: counter.paginate(spine, book.chapter(spine)).size
            yield()
        }
        Log.i("Poth", "Counted ${counts.sum()} pages in ${(System.nanoTime() - started) / 1_000_000} ms")
        counts
    }

    /** Page numbers for the whole book, from [count]. */
    fun map(counts: IntArray) = PageMap(book.readingOrder, counts)

    private suspend fun firstFrom(order: Int): Page? {
        for (i in max(order, 0) until book.readingOrder.size) of(book.readingOrder[i]).firstOrNull()?.let { return it }
        return null
    }

    private companion object {
        const val KEPT = 5
    }
}

/** Where each chapter's pages fall in the whole book. Page numbers here start at 0. */
class PageMap(private val readingOrder: List<Int>, private val counts: IntArray) {
    private val starts = IntArray(counts.size).also { starts ->
        var sum = 0
        for (i in counts.indices) {
            starts[i] = sum
            sum += counts[i]
        }
    }
    val total = counts.sum()

    /** The first page of each chapter that has pages: ticks on the scrubber. */
    val chapterStarts: List<Int> = counts.indices.filter { counts[it] > 0 }.map { starts[it] }

    fun number(spine: Int, index: Int): Int {
        val order = readingOrder.indexOf(spine)
        if (order < 0 || total == 0) return 0
        return starts[order] + index.coerceIn(0, max(counts[order] - 1, 0))
    }

    /** The spine item and page index of page [number]. */
    fun locate(number: Int): Pair<Int, Int> {
        val n = number.coerceIn(0, max(total - 1, 0))
        val order = counts.indices.last { counts[it] > 0 && starts[it] <= n || it == 0 }
        return readingOrder[order] to n - starts[order]
    }
}
