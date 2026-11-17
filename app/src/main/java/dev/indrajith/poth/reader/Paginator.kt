package dev.indrajith.poth.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import dev.indrajith.poth.dictionary.Words
import dev.indrajith.poth.epub.Align
import dev.indrajith.poth.epub.Chapter
import dev.indrajith.poth.epub.ImageBlock
import dev.indrajith.poth.epub.RuleBlock
import dev.indrajith.poth.epub.Shift
import dev.indrajith.poth.epub.TextBlock
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The text area of a page and the reader's type, in pixels. */
data class PageSpec(
    val width: Int,
    val height: Int,
    val fontPx: Float,           // one base em
    val lineHeight: Float,       // when the book does not say, a multiple of the font size
    val textColor: Int,
    val imageScale: Float,       // screen pixels per image pixel: images are sized like CSS pixels
    val typeface: Typeface = Typeface.SERIF,
)

/**
 * An image as decoded for the page, with the size of the original file. Counting pages needs
 * only the size, so [bitmap] is null then.
 */
class DecodedImage(val bitmap: Bitmap?, val width: Int, val height: Int)

/** A word to underline: [start] until [end] in a paragraph's text. [faint] for general words. */
data class Mark(val start: Int, val end: Int, val faint: Boolean, val entries: List<Long>)

/** Says which words of a paragraph's text, from [from] until [to], to underline. */
fun interface Marker {
    fun marks(text: CharSequence, from: Int, to: Int): List<Mark>
}

/**
 * The word under a point on a page: [start] until [end] are offsets in the chapter, and [marks]
 * the underlines on it. A term of several words ("Jasnah Kholin") is hit as a whole.
 */
class WordHit(
    val word: String,
    val start: Int,
    val end: Int,
    val startsSentence: Boolean,
    val sentence: String,
    val marks: List<Mark>,
)

/** A word, or a run of words: [start] until [end] in the chapter. */
data class WordRange(val start: Int, val end: Int)

/**
 * Some of a page's text, [start] until [end] in the chapter: what it says (as a term is
 * written), the sentence it begins in, and where it is drawn on the page, a rectangle a line.
 */
class Passage(
    val start: Int,
    val end: Int,
    val text: String,
    val sentence: String,
    val startsSentence: Boolean,
    val rects: List<RectF>,
)

/** Text highlighted on a page: [start] until [end] in the chapter, filled with [color]. */
class Highlight(val start: Int, val end: Int, color: Int) {
    val paint = Paint().apply { this.color = color }
}

/** One page of a chapter: [start] until [end] are character offsets in the chapter. */
class Page(
    val spine: Int,
    val index: Int,
    val start: Int,
    val end: Int,
    val words: Int,
    private val items: List<PageItem>,
) {
    private val slices get() = items.filterIsInstance<TextSlice>()

    /**
     * Draws the page with its top left corner at the canvas's origin, underlining what [marker]
     * says and filling in behind [highlight].
     */
    fun draw(canvas: Canvas, marker: Marker? = null, highlight: Highlight? = null) = items.forEach { it.draw(canvas, marker, highlight) }

    /** The word at [x], [y] on the page, or null if there is no text there. */
    fun wordAt(x: Float, y: Float, marker: Marker?): WordHit? =
        items.firstNotNullOfOrNull { (it as? TextSlice)?.wordAt(x, y, marker) }

    /**
     * The word nearest [x], [y]: the one there, or on the line nearest the point, the closest
     * word on it. Null on a page without text.
     */
    fun wordNear(x: Float, y: Float): WordRange? = slices.minByOrNull { it.distanceTo(y) }?.wordNear(x, y)

    /** The word that chapter offset [offset] is in, if it is on this page. */
    fun wordAround(offset: Int): WordRange? = slices.firstNotNullOfOrNull { it.wordAround(offset) }

    /** The text from [start] until [end] in the chapter, or null if none of it is on this page. */
    fun passage(start: Int, end: Int): Passage? {
        val parts = slices.mapNotNull { it.part(start, end) }
        if (parts.isEmpty()) return null
        val first = slices.firstOrNull { it.covers(start) } ?: slices.first { it.part(start, end) != null }
        return Passage(
            start = start,
            end = end,
            text = Words.clean(parts.joinToString(" ") { it.first }),
            sentence = first.sentence(start, end),
            startsSentence = first.startsSentence(start),
            rects = parts.flatMap { it.second },
        )
    }

    /** The page's text, a paragraph (or part of one) a line. */
    fun text(): String = items.filterIsInstance<TextSlice>().joinToString("\n") { it.text() }
}

sealed interface PageItem {
    fun draw(canvas: Canvas, marker: Marker?, highlight: Highlight?)
}

/** The dashed underlines for dictionary words, in the text's colour. */
private class MarkPaints(color: Int, fontPx: Float) {
    val strong = paint(color, 0xC0, fontPx)
    val faint = paint(color, 0x60, fontPx)

    private fun paint(color: Int, alpha: Int, fontPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = alpha
        style = Paint.Style.STROKE
        strokeWidth = max(1f, fontPx / 16)
        pathEffect = DashPathEffect(floatArrayOf(fontPx * 0.2f, fontPx * 0.12f), 0f)
    }
}

/**
 * Lines [firstLine] until [endLine] of a paragraph, placed at [x], [y] on the page. The
 * paragraph starts at [blockStart] in the chapter.
 */
private class TextSlice(
    val layout: Layout,
    val firstLine: Int,
    val endLine: Int,
    val x: Float,
    val y: Float,
    val pageWidth: Float,
    val blockStart: Int,
    val markPaints: MarkPaints,
) : PageItem {
    private val from get() = layout.getLineStart(firstLine)
    private val to get() = layout.getLineEnd(endLine - 1)

    // The marks are worked out the first time the slice is drawn with a marker, then kept.
    private var marksOf: Marker? = null
    private var marks: List<Mark> = emptyList()

    private fun marks(marker: Marker?): List<Mark> {
        if (marker == null) return emptyList()
        if (marker !== marksOf) {
            marks = marker.marks(layout.text, from, to)
            marksOf = marker
        }
        return marks
    }

    override fun draw(canvas: Canvas, marker: Marker?, highlight: Highlight?) {
        val top = layout.getLineTop(firstLine).toFloat()
        val bottom = layout.getLineBottom(endLine - 1).toFloat()
        canvas.save()
        canvas.translate(x, y - top)
        // Layout.draw draws the lines the clip touches, so the clip picks this page's lines.
        canvas.clipRect(-x, top, pageWidth - x, bottom)
        if (highlight != null) {
            for (rect in rects(highlight.start - blockStart, highlight.end - blockStart)) canvas.drawRect(rect, highlight.paint)
        }
        layout.draw(canvas)
        for (mark in marks(marker)) underline(canvas, mark)
        canvas.restore()
    }

    private fun underline(canvas: Canvas, mark: Mark) {
        val paint = if (mark.faint) markPaints.faint else markPaints.strong
        val below = layout.paint.textSize * 0.2f
        for (line in firstLine until endLine) {
            val lineEnd = layout.getLineEnd(line)
            val start = max(mark.start, layout.getLineStart(line))
            val end = min(mark.end, lineEnd)
            if (start >= end) continue
            val x0 = horizontal(line, start)
            val x1 = horizontal(line, end)
            val baseline = layout.getLineBaseline(line) + below
            canvas.drawLine(x0, baseline, x1, baseline, paint)
        }
    }

    fun wordAt(px: Float, py: Float, marker: Marker?): WordHit? {
        val top = layout.getLineTop(firstLine)
        if (py < y || py > y + layout.getLineBottom(endLine - 1) - top) return null
        val line = layout.getLineForVertical((py - y + top).toInt()).coerceIn(firstLine, endLine - 1)
        val lx = px - x
        if (lx < horizontal(line, layout.getLineStart(line)) || lx > horizontal(line, visibleEnd(line))) return null
        val text = layout.text
        val token = Words.at(text, offsetAt(line, lx)) ?: return null
        val marks = marks(marker).filter { it.start < token.end && it.end > token.start }
        val start = marks.minOfOrNull { it.start } ?: token.start
        val end = marks.maxOfOrNull { it.end } ?: token.end
        return WordHit(
            word = text.subSequence(start, end).toString(),
            start = blockStart + start,
            end = blockStart + end,
            startsSentence = Words.startsSentence(text, start),
            sentence = Words.sentenceAround(text, start, end),
            marks = marks,
        )
    }

    fun text(): String = layout.text.subSequence(from, to).toString()

    /** Whether chapter offset [offset] is in this slice's text. */
    fun covers(offset: Int): Boolean = offset - blockStart in from until to

    /** How far [py] on the page is above or below this slice; 0 if it is beside it. */
    fun distanceTo(py: Float): Float {
        val bottom = y + layout.getLineBottom(endLine - 1) - layout.getLineTop(firstLine)
        return when {
            py < y -> y - py
            py > bottom -> py - bottom
            else -> 0f
        }
    }

    /** The word at [px], [py], or on the line nearest it, the word nearest along the line. */
    fun wordNear(px: Float, py: Float): WordRange? {
        val top = layout.getLineTop(firstLine)
        val ly = (py - y + top).toInt().coerceIn(top, layout.getLineBottom(endLine - 1) - 1)
        val line = layout.getLineForVertical(ly).coerceIn(firstLine, endLine - 1)
        val start = layout.getLineStart(line)
        val end = visibleEnd(line)
        if (start >= end) return null
        val lx = (px - x).coerceIn(horizontal(line, start), horizontal(line, end))
        val text = layout.text
        val token = Words.at(text, offsetAt(line, lx))?.takeIf { it.start < end && it.end > start }
            ?: Words.tokens(text, start, end).minByOrNull { abs((horizontal(line, it.start) + horizontal(line, it.end)) / 2 - lx) }
            ?: return null
        return WordRange(blockStart + token.start, blockStart + token.end)
    }

    /** The word chapter offset [offset] is in, if this slice has it. */
    fun wordAround(offset: Int): WordRange? {
        if (!covers(offset)) return null
        val token = Words.at(layout.text, offset - blockStart) ?: return null
        return WordRange(blockStart + token.start, blockStart + token.end)
    }

    /** The part of chapter offsets [start] until [end] in this slice: its text, and where it is drawn on the page. */
    fun part(start: Int, end: Int): Pair<String, List<RectF>>? {
        val from = max(start - blockStart, this.from)
        val until = min(end - blockStart, to)
        if (from >= until) return null
        val top = layout.getLineTop(firstLine)
        val rects = rects(from, until).map { RectF(it.left + x, it.top - top + y, it.right + x, it.bottom - top + y) }
        return layout.text.subSequence(from, until).toString() to rects
    }

    /** The sentence chapter offsets [start] until [end] begin in. */
    fun sentence(start: Int, end: Int): String {
        val length = layout.text.length
        return Words.sentenceAround(layout.text, (start - blockStart).coerceIn(0, length), (end - blockStart).coerceIn(0, length))
    }

    fun startsSentence(offset: Int): Boolean = Words.startsSentence(layout.text, (offset - blockStart).coerceIn(0, layout.text.length))

    /** Where the paragraph's [start] until [end] is drawn on this slice's lines, in the layout's coordinates: a rectangle a line. */
    private fun rects(start: Int, end: Int): List<RectF> = buildList {
        for (line in firstLine until endLine) {
            val from = max(start, layout.getLineStart(line))
            val until = min(end, visibleEnd(line))
            if (from >= until) continue
            add(RectF(horizontal(line, from), layout.getLineTop(line).toFloat(), horizontal(line, until), layout.getLineBottom(line).toFloat()))
        }
    }

    // Android draws a justified line with its spaces widened, but getPrimaryHorizontal and
    // getOffsetForHorizontal measure it as if they were not, so anything placed by them drifts
    // left along the line. On justified lines, a position here is getPrimaryHorizontal's plus the
    // extra each space before it was given to fill the line. Should a later Android measure the
    // widened spaces itself, the extra comes out as nothing.

    private class Justified(val start: Int, val end: Int, val extraPerSpace: Float)

    private val justified = HashMap<Int, Justified?>()

    private fun justified(line: Int): Justified? = justified.getOrPut(line) {
        val text = layout.text
        val lineEnd = layout.getLineEnd(line)
        val isJustified = layout.justificationMode == LineBreaker.JUSTIFICATION_MODE_INTER_WORD &&
            lineEnd < text.length && text[lineEnd - 1] != '\n' && // a paragraph's last line is not justified
            layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
        if (!isJustified) return@getOrPut null
        val start = layout.getLineStart(line)
        val end = visibleEnd(line)
        val spaces = (start until end).count { text[it] == ' ' }
        if (spaces == 0) return@getOrPut null
        // A word broken across lines (letters on both sides of the break) has a hyphen drawn after it.
        val broken = end == lineEnd && end > start && text[end - 1].isLetter() && text[lineEnd].isLetter()
        val hyphen = if (broken) layout.paint.measureText("-") else 0f
        val shortBy = layout.getParagraphRight(line) - (measured(line, end) + hyphen)
        Justified(start, end, (shortBy / spaces).coerceAtLeast(0f))
    }

    /** Where [offset] is drawn on [line]. */
    private fun horizontal(line: Int, offset: Int): Float {
        val at = offset.coerceIn(layout.getLineStart(line), visibleEnd(line))
        val justified = justified(line) ?: return measured(line, at)
        val spaces = (justified.start until at).count { layout.text[it] == ' ' }
        return measured(line, at) + justified.extraPerSpace * spaces
    }

    /** getPrimaryHorizontal, except at a line's very end, which it would take for the next line's start. */
    private fun measured(line: Int, offset: Int): Float =
        if (offset < layout.getLineEnd(line) || offset == layout.getLineStart(line)) {
            layout.getPrimaryHorizontal(offset)
        } else {
            layout.getPrimaryHorizontal(offset - 1) + layout.paint.measureText(layout.text, offset - 1, offset)
        }

    /** The offset on [line] drawn nearest to [x]. */
    private fun offsetAt(line: Int, x: Float): Int {
        val justified = justified(line) ?: return layout.getOffsetForHorizontal(line, x)
        var low = justified.start
        var high = justified.end
        while (low < high) { // the last offset drawn at or left of x
            val middle = (low + high + 1) / 2
            if (horizontal(line, middle) <= x) low = middle else high = middle - 1
        }
        return if (low < justified.end && x - horizontal(line, low) > horizontal(line, low + 1) - x) low + 1 else low
    }

    /** The end of [line]'s text, without the spaces it ends with. */
    private fun visibleEnd(line: Int): Int {
        val start = layout.getLineStart(line)
        var end = layout.getLineEnd(line)
        while (end > start && layout.text[end - 1].isWhitespace()) end--
        return end
    }
}

private class ImageItem(val bitmap: Bitmap?, val rect: RectF) : PageItem {
    override fun draw(canvas: Canvas, marker: Marker?, highlight: Highlight?) {
        if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, imagePaint)
    }
}

private class RuleItem(val x0: Float, val x1: Float, val y: Float, val paint: Paint) : PageItem {
    override fun draw(canvas: Canvas, marker: Marker?, highlight: Highlight?) = canvas.drawLine(x0, y, x1, y, paint)
}

private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/**
 * Lays a chapter out in pages: each paragraph with Android's own line breaker (justified, with
 * hyphenation for the book's language), then its lines split across pages.
 */
class Paginator(
    private val spec: PageSpec,
    private val locale: Locale,
    private val image: (path: String, maxWidth: Int, maxHeight: Int) -> DecodedImage?,
) {
    private val markPaints = MarkPaints(spec.textColor, spec.fontPx)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = spec.textColor
        alpha = 0x70
        strokeWidth = max(1f, spec.fontPx / 16)
    }

    companion object {
        /**
         * Part of the key saved page counts are stored under. Raise it whenever a change to the
         * parser or the paginator can move a page break, so old counts are not used.
         */
        const val VERSION = 1
    }

    fun paginate(spine: Int, chapter: Chapter): List<Page> {
        val sink = Sink(spine)
        var previousBottom = 0f
        for (block in chapter.blocks) {
            if (block.style.pageBreakBefore) sink.breakPage(block.start)
            // Spacing above a block is dropped at the top of a page, except on the chapter's first.
            val gap = { if (sink.isEmpty) (if (sink.pages.isEmpty()) em(block.style.marginTop) else 0f) else max(previousBottom, em(block.style.marginTop)) }
            when (block) {
                is TextBlock -> placeText(block, sink, gap)
                is ImageBlock -> placeImage(block, sink, gap)
                is RuleBlock -> placeRule(block, sink, gap)
            }
            previousBottom = em(block.style.marginBottom)
        }
        sink.breakPage(chapter.length)
        return sink.pages
    }

    private fun em(value: Float) = value * spec.fontPx

    private fun placeText(block: TextBlock, sink: Sink, gap: () -> Float) {
        // Rest lines start at the left margin, the first line at the margin plus the indent.
        val left = em(block.style.marginLeft)
        val firstLeft = left + em(block.style.indent)
        val x = min(left, firstLeft).coerceAtLeast(0f)
        val width = (spec.width - x - em(block.style.marginRight)).roundToInt().coerceAtLeast(spec.width / 2)
        val layout = layout(block, width, (firstLeft - x).coerceAtLeast(0f).roundToInt(), (left - x).roundToInt())

        if (block.style.keepWithNext && !sink.isEmpty) {
            val needed = gap() + layout.height + 2 * spec.fontPx * spec.lineHeight
            if (sink.y + needed > spec.height) sink.breakPage(block.start)
        }
        var line = 0
        while (line < layout.lineCount) {
            val top = sink.y + (if (line == 0) gap() else 0f)
            val sliceTop = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && top + layout.getLineBottom(end) - sliceTop <= spec.height) end++
            if (end == line) {
                if (!sink.isEmpty) {
                    sink.breakPage(block.start + layout.getLineStart(line))
                    continue
                }
                end = line + 1 // a line taller than a page gets a page of its own
            }
            val words = countWords(layout.text, layout.getLineStart(line), layout.getLineEnd(end - 1))
            sink.add(TextSlice(layout, line, end, x, top, spec.width.toFloat(), block.start, markPaints), top + layout.getLineBottom(end - 1) - sliceTop, words)
            line = end
            if (line < layout.lineCount) sink.breakPage(block.start + layout.getLineStart(line))
        }
    }

    private fun placeImage(block: ImageBlock, sink: Sink, gap: () -> Float) {
        val decoded = image(block.path, spec.width, spec.height) ?: return
        var width = decoded.width * spec.imageScale
        var height = decoded.height * spec.imageScale
        if (width > spec.width) {
            height *= spec.width / width
            width = spec.width.toFloat()
        }
        if (height > spec.height) {
            width *= spec.height / height
            height = spec.height.toFloat()
        }
        if (!sink.isEmpty && sink.y + gap() + height > spec.height) sink.breakPage(block.start)
        val top = sink.y + min(gap(), spec.height - height)
        val left = (spec.width - width) / 2 // images are centred
        sink.add(ImageItem(decoded.bitmap, RectF(left, top, left + width, top + height)), top + height, 0)
    }

    private fun placeRule(block: RuleBlock, sink: Sink, gap: () -> Float) {
        val height = spec.fontPx
        if (!sink.isEmpty && sink.y + gap() + height > spec.height) sink.breakPage(block.start)
        val middle = sink.y + gap() + height / 2
        sink.add(RuleItem(spec.width * 0.35f, spec.width * 0.65f, middle, rulePaint), middle + height / 2, 0)
    }

    private fun layout(block: TextBlock, width: Int, firstIndent: Int, restIndent: Int): StaticLayout {
        val sizePx = em(block.style.size)
        val text = SpannableString(block.text)
        for (run in block.runs) {
            val style = run.style
            fun span(what: Any) = text.setSpan(what, run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val typefaceStyle = (if (style.bold) Typeface.BOLD else 0) or (if (style.italic) Typeface.ITALIC else 0)
            if (typefaceStyle != 0) span(StyleSpan(typefaceStyle))
            if (style.size != block.style.size) span(AbsoluteSizeSpan(em(style.size).roundToInt()))
            when (style.shift) {
                Shift.SUPER -> span(SuperscriptSpan())
                Shift.SUB -> span(SubscriptSpan())
                Shift.NONE -> {}
            }
            if (style.underline) span(UnderlineSpan())
            if (style.strike) span(StrikethroughSpan())
        }
        val lineHeight = (sizePx * (block.style.lineHeight ?: spec.lineHeight)).roundToInt()
        text.setSpan(LineHeightSpan.Standard(lineHeight), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        if (firstIndent > 0 || restIndent > 0) {
            text.setSpan(LeadingMarginSpan.Standard(firstIndent, restIndent), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = spec.typeface
            textSize = sizePx
            color = spec.textColor
            textLocale = locale
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(
                when (block.style.align) {
                    Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                    Align.END -> Layout.Alignment.ALIGN_OPPOSITE
                    Align.START, Align.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
                },
            )
            .setJustificationMode(
                if (block.style.align == Align.JUSTIFY) LineBreaker.JUSTIFICATION_MODE_INTER_WORD else LineBreaker.JUSTIFICATION_MODE_NONE,
            )
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            .setIncludePad(false)
            .build()
    }

    /** The pages of one chapter as they fill up. */
    private class Sink(val spine: Int) {
        val pages = mutableListOf<Page>()
        private var items = mutableListOf<PageItem>()
        private var start = 0
        private var words = 0
        var y = 0f
            private set
        val isEmpty get() = items.isEmpty()

        fun add(item: PageItem, bottom: Float, words: Int) {
            items += item
            y = bottom
            this.words += words
        }

        /** Ends the page being filled, if it has anything; the next one starts at [next]. */
        fun breakPage(next: Int) {
            if (items.isNotEmpty()) {
                pages += Page(spine, pages.size, start, next, words, items)
                items = mutableListOf()
                y = 0f
                words = 0
            }
            start = next
        }
    }
}

/** Words in [text] from [start] until [end]: runs of characters between whitespace. */
internal fun countWords(text: CharSequence, start: Int, end: Int): Int {
    var words = 0
    var inWord = false
    for (i in start until end) {
        val space = text[i].isWhitespace()
        if (!space && !inWord) words++
        inWord = !space
    }
    return words
}
