package dev.indrajith.poth.epub

import java.util.IdentityHashMap
import kotlin.math.max
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

// Sizes and lengths below are in "base em": multiples of the reader's font size, so a book's
// 0.77419em inside a 1.29167em body is 1.0, the reader's size.

enum class Align { START, CENTER, END, JUSTIFY }

enum class Shift { NONE, SUPER, SUB }

/** How a run of text looks. */
data class InlineStyle(
    val size: Float = 1f,
    val italic: Boolean = false,
    val bold: Boolean = false,
    val shift: Shift = Shift.NONE,
    val underline: Boolean = false,
    val strike: Boolean = false,
)

/** [style] applies to characters [start] until [end] of a [TextBlock]'s text. */
data class Run(val start: Int, val end: Int, val style: InlineStyle)

/** Spacing and alignment of a block. */
data class BlockStyle(
    val size: Float = 1f,
    val align: Align = Align.JUSTIFY,
    val lineHeight: Float? = null,  // a multiple of [size]; null leaves it to the reader
    val indent: Float = 0f,         // of the first line; negative hangs it out to the left
    val marginTop: Float = 0f,
    val marginBottom: Float = 0f,
    val marginLeft: Float = 0f,
    val marginRight: Float = 0f,
    val pageBreakBefore: Boolean = false,
    val keepWithNext: Boolean = false, // a heading: not left alone at the bottom of a page
)

/**
 * A paragraph, an image or a rule. [start] is the block's character offset in its chapter: text
 * blocks count their characters, the others count one. A reading position is a spine index and
 * such an offset, so it does not depend on the font size or the screen.
 */
sealed interface Block {
    val start: Int
    val length: Int
    val style: BlockStyle
}

data class TextBlock(override val start: Int, val text: String, val runs: List<Run>, override val style: BlockStyle) : Block {
    override val length get() = text.length
}

data class ImageBlock(override val start: Int, val path: String, val alt: String, override val style: BlockStyle) : Block {
    override val length get() = 1
}

data class RuleBlock(override val start: Int, override val style: BlockStyle) : Block {
    override val length get() = 1
}

/** One spine item, ready to be laid out. */
class Chapter(val blocks: List<Block>) {
    val length: Int = blocks.lastOrNull()?.let { it.start + it.length } ?: 0
}

/**
 * Turns a chapter's XHTML and its stylesheets into a [Chapter]. [stylesheet] gives the rules of a
 * CSS file by its path inside the zip (empty if there is no such file).
 *
 * Supported: the box and text properties books use for body text (font size, style, weight and
 * small caps, alignment, indent, line height, margins and padding, display: none, page breaks),
 * with the usual inheritance. Colours and fonts are left to the reader.
 */
class ChapterParser(private val stylesheet: (path: String) -> List<CssRule>) {

    fun parse(doc: Document, path: String): Chapter {
        val builder = Builder(cascade(doc, path), path.substringBeforeLast('/', ""))
        builder.walk(doc.body().parent() ?: doc.body(), Computed())
        return builder.finish()
    }

    /** The author's declarations for each element, the more specific rules winning. */
    private fun cascade(doc: Document, path: String): Map<Element, Map<String, String>> {
        val base = path.substringBeforeLast('/', "")
        val rules = mutableListOf<CssRule>()
        for (element in doc.select("link, style")) {
            if (element.normalName() == "style") {
                rules += Css.parse(element.data())
            } else if (element.attr("rel").contains("stylesheet", ignoreCase = true) &&
                element.attr("type").let { it.isEmpty() || it.equals("text/css", ignoreCase = true) }
            ) {
                rules += stylesheet(resolvePath(base, element.attr("href")))
            }
        }

        val declared = IdentityHashMap<Element, MutableMap<String, String>>()
        val ordered = rules.withIndex().sortedWith(compareBy({ it.value.specificity }, { it.index }))
        for ((_, rule) in ordered) {
            val matches = try {
                doc.select(rule.selector)
            } catch (e: RuntimeException) {
                continue // a selector Jsoup does not support: ::first-letter, :hover and the like
            }
            for (element in matches) declared.getOrPut(element) { HashMap() }.putAll(rule.declarations)
        }
        for (element in doc.select("[style]")) {
            declared.getOrPut(element) { HashMap() }.putAll(Css.declarations(element.attr("style")))
        }
        return declared
    }

    /** An element's style after inheritance. Margins are its own; the rest is passed down. */
    private data class Computed(
        val inline: InlineStyle = InlineStyle(),
        val align: Align = Align.JUSTIFY,
        val indent: Float = 0f,
        val lineHeight: Float? = null,
        val smallCaps: Boolean = false,
        val transform: String? = null,
        val left: Float = 0f,
        val right: Float = 0f,
        val marginTop: Float = 0f,
        val marginBottom: Float = 0f,
        val keepWithNext: Boolean = false,
    )

    private class Builder(private val declared: Map<Element, Map<String, String>>, private val base: String) {
        private val blocks = mutableListOf<Block>()
        private val paragraph = Paragraph()
        private var offset = 0
        private var pendingTop = 0f      // collapsed top margins waiting for the next block
        private var pendingBreak = false
        private var blockStyle = Computed() // of the innermost block element open

        fun finish(): Chapter {
            flush()
            return Chapter(blocks.toList())
        }

        fun walk(element: Element, parent: Computed) {
            val name = element.normalName()
            if (name in SKIPPED) return
            val decl = (UA[name] ?: emptyMap()) + (declared[element] ?: emptyMap())
            if (decl["display"] == "none") return

            val isBlock = when (decl["display"]) {
                "block", "list-item", "table", "table-row", "table-cell", "flex", "grid" -> true
                "inline", "inline-block" -> false
                else -> name in BLOCKS
            }
            val style = compute(parent, decl, name, isBlock)
            when (name) {
                "img", "image" -> return image(element)
                "br" -> return paragraph.lineBreak(style.inline)
                "hr" -> {
                    flush()
                    return emit(RuleBlock(offset, BlockStyle(marginTop = max(pendingTop, 0.5f), marginBottom = 0.5f)))
                }
            }
            if (!isBlock) return children(element, style)

            flush()
            pendingTop = max(pendingTop, style.marginTop)
            if (decl["page-break-before"] in BREAKS || decl["break-before"] in BREAKS) pendingBreak = true
            val outer = blockStyle
            blockStyle = style
            val emittedBefore = blocks.size
            if (name == "li") bullet(element)?.let { paragraph.append(it, style) }
            children(element, style)
            flush()
            if (blocks.size > emittedBefore) {
                val last = blocks.last()
                blocks[blocks.lastIndex] = last.withMarginBottom(max(last.style.marginBottom, style.marginBottom))
            } else {
                pendingTop = max(pendingTop, style.marginBottom) // an empty block's margins collapse into the next
            }
            blockStyle = outer
        }

        private fun children(element: Element, style: Computed) {
            for (node in element.childNodes()) {
                when (node) {
                    is TextNode -> paragraph.append(node.wholeText, style)
                    is Element -> walk(node, style)
                }
            }
        }

        private fun image(element: Element) {
            val src = element.attr("src").ifEmpty { element.attr("xlink:href") }.ifEmpty { element.attr("href") }
            if (src.isEmpty() || src.startsWith("data:")) return
            flush()
            emit(ImageBlock(offset, resolvePath(base, src), element.attr("alt"), BlockStyle(marginTop = pendingTop, pageBreakBefore = pendingBreak)))
        }

        private fun bullet(item: Element): String? {
            val list = item.parent() ?: return null
            return when (list.normalName()) {
                "ol" -> "${list.children().filter { it.normalName() == "li" }.indexOf(item) + 1}. "
                "ul" -> "• "
                else -> null
            }
        }

        private fun flush() {
            val (text, runs) = paragraph.take() ?: return
            val style = blockStyle
            emit(
                TextBlock(
                    offset, text, runs,
                    BlockStyle(
                        size = style.inline.size,
                        align = style.align,
                        lineHeight = style.lineHeight,
                        indent = style.indent,
                        marginTop = pendingTop,
                        marginLeft = style.left,
                        marginRight = style.right,
                        pageBreakBefore = pendingBreak,
                        keepWithNext = style.keepWithNext,
                    ),
                ),
            )
        }

        private fun emit(block: Block) {
            blocks += block
            offset += block.length
            pendingTop = 0f
            pendingBreak = false
        }
    }

    /** The text of the paragraph being built, with whitespace collapsed as HTML does. */
    private class Paragraph {
        private val text = StringBuilder()
        private val runs = mutableListOf<Run>()

        fun append(raw: String, style: Computed) {
            for (original in raw) {
                var char = original
                if (char == ' ' || char == '\t' || char == '\n' || char == '\r' || char == '\u000C') {
                    if (text.isEmpty() || text.last() == ' ' || text.last() == '\n') continue
                    char = ' '
                }
                var inline = style.inline
                char = when (style.transform) {
                    "uppercase" -> char.uppercaseChar()
                    "lowercase" -> char.lowercaseChar()
                    "capitalize" -> if (text.isEmpty() || text.last().isWhitespace()) char.uppercaseChar() else char
                    else -> char
                }
                if (style.smallCaps && char.isLowerCase()) {
                    char = char.uppercaseChar()
                    inline = inline.copy(size = inline.size * 0.8f)
                }
                // Lines may break before an em dash, which leaves "—skin" at the start of a
                // line. A word joiner keeps the dash with the word before it.
                if (char == '—' && text.isNotEmpty() && !text.last().isWhitespace()) put('⁠', inline)
                put(char, inline)
            }
        }

        fun lineBreak(style: InlineStyle) {
            trimEnd(' ')
            put('\n', style)
        }

        /** The text and its runs, or null if there is nothing but whitespace. Starts a new paragraph. */
        fun take(): Pair<String, List<Run>>? {
            trimEnd(' ', '\n')
            val result = if (text.isEmpty()) null else text.toString() to runs.toList()
            text.setLength(0)
            runs.clear()
            return result
        }

        private fun put(char: Char, style: InlineStyle) {
            val last = runs.lastOrNull()
            if (last != null && last.style == style && last.end == text.length) {
                runs[runs.lastIndex] = last.copy(end = last.end + 1)
            } else {
                runs += Run(text.length, text.length + 1, style)
            }
            text.append(char)
        }

        private fun trimEnd(vararg chars: Char) {
            while (text.isNotEmpty() && text.last() in chars) {
                text.setLength(text.length - 1)
                val last = runs.last()
                if (last.end - 1 <= last.start) runs.removeAt(runs.lastIndex) else runs[runs.lastIndex] = last.copy(end = last.end - 1)
            }
        }
    }

    private companion object {
        val SKIPPED = setOf("head", "script", "style", "title", "meta", "link", "noscript", "template")
        val BLOCKS = setOf(
            "html", "body", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "section",
            "article", "header", "footer", "aside", "nav", "main", "figure", "figcaption", "ul", "ol", "li",
            "dl", "dt", "dd", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "center", "address",
        )
        val BREAKS = setOf("always", "page", "left", "right")

        // What browsers do without a stylesheet, for the elements books use.
        val UA: Map<String, Map<String, String>> = mapOf(
            "h1" to "font-size: 1.6em; font-weight: bold; margin: 0.67em 0",
            "h2" to "font-size: 1.4em; font-weight: bold; margin: 0.75em 0",
            "h3" to "font-size: 1.2em; font-weight: bold; margin: 0.83em 0",
            "h4" to "font-size: 1.1em; font-weight: bold; margin: 1em 0",
            "h5" to "font-weight: bold; margin: 1em 0",
            "h6" to "font-size: 0.9em; font-weight: bold; margin: 1em 0",
            "p" to "margin: 1em 0",
            "blockquote" to "margin: 1em 2em",
            "figure" to "margin: 1em 0",
            "ul" to "margin: 1em 0; padding-left: 1.5em",
            "ol" to "margin: 1em 0; padding-left: 1.5em",
            "dd" to "margin-left: 2em",
            "pre" to "margin: 1em 0; text-align: left",
            "center" to "text-align: center",
            "th" to "font-weight: bold",
            "b" to "font-weight: bold",
            "strong" to "font-weight: bold",
            "i" to "font-style: italic",
            "em" to "font-style: italic",
            "cite" to "font-style: italic",
            "var" to "font-style: italic",
            "dfn" to "font-style: italic",
            "address" to "font-style: italic",
            "u" to "text-decoration: underline",
            "ins" to "text-decoration: underline",
            "s" to "text-decoration: line-through",
            "strike" to "text-decoration: line-through",
            "del" to "text-decoration: line-through",
            "sup" to "vertical-align: super; font-size: smaller",
            "sub" to "vertical-align: sub; font-size: smaller",
            "small" to "font-size: smaller",
            "big" to "font-size: larger",
        ).mapValues { Css.declarations(it.value) }

        val FONT_SIZES = mapOf(
            "xx-small" to 0.6f, "x-small" to 0.75f, "small" to 0.89f, "medium" to 1f,
            "large" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f,
        )
        val NUMBER = Regex("^(-?[0-9]*\\.?[0-9]+)([a-z%]*)$")

        private fun compute(parent: Computed, decl: Map<String, String>, name: String, isBlock: Boolean): Computed {
            val size = fontSize(decl["font-size"], parent.inline.size)
            val p = parent.inline
            val decoration = decl["text-decoration"] ?: decl["text-decoration-line"]
            val inline = InlineStyle(
                size = size,
                italic = decl["font-style"]?.let { it == "italic" || it == "oblique" } ?: p.italic,
                bold = decl["font-weight"]?.let { it == "bold" || it == "bolder" || (it.toIntOrNull() ?: 400) >= 600 } ?: p.bold,
                shift = when (decl["vertical-align"]) {
                    "super" -> Shift.SUPER
                    "sub" -> Shift.SUB
                    "baseline" -> Shift.NONE
                    else -> p.shift
                },
                underline = decoration?.let { "underline" in it } ?: p.underline,
                strike = decoration?.let { "line-through" in it } ?: p.strike,
            )
            val margin = box(decl, "margin", size)
            val padding = box(decl, "padding", size)
            return Computed(
                inline = inline,
                align = when (decl["text-align"]) {
                    "left", "start" -> Align.START
                    "right", "end" -> Align.END
                    "center" -> Align.CENTER
                    "justify" -> Align.JUSTIFY
                    else -> parent.align
                },
                indent = decl["text-indent"]?.let { length(it, size) }?.coerceIn(-10f, 10f) ?: parent.indent,
                lineHeight = if (decl.containsKey("line-height")) lineHeight(decl.getValue("line-height"), size) else parent.lineHeight,
                smallCaps = (decl["font-variant"] ?: decl["font-variant-caps"])?.let { "small-caps" in it } ?: parent.smallCaps,
                transform = decl["text-transform"]?.let { if (it == "none") null else it } ?: parent.transform,
                left = if (isBlock) parent.left + margin[3] + padding[3] else parent.left,
                right = if (isBlock) parent.right + margin[1] + padding[1] else parent.right,
                marginTop = if (isBlock) margin[0] + padding[0] else 0f,
                marginBottom = if (isBlock) margin[2] + padding[2] else 0f,
                keepWithNext = name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' ||
                    decl["page-break-after"] == "avoid" || decl["break-after"] == "avoid",
            )
        }

        /** Top, right, bottom, left of "margin" or "padding", shorthand and longhand. Never negative. */
        private fun box(decl: Map<String, String>, property: String, size: Float): FloatArray {
            val sides = FloatArray(4)
            decl[property]?.split(Regex("\\s+"))?.map { length(it, size) ?: 0f }?.let { values ->
                when (values.size) {
                    1 -> sides.fill(values[0])
                    2 -> { sides[0] = values[0]; sides[2] = values[0]; sides[1] = values[1]; sides[3] = values[1] }
                    3 -> { sides[0] = values[0]; sides[1] = values[1]; sides[3] = values[1]; sides[2] = values[2] }
                    else -> for (i in 0..3) sides[i] = values[i]
                }
            }
            listOf("top", "right", "bottom", "left").forEachIndexed { i, side ->
                decl["$property-$side"]?.let { value -> length(value, size)?.let { sides[i] = it } }
            }
            for (i in 0..3) sides[i] = sides[i].coerceIn(0f, 10f)
            return sides
        }

        private fun fontSize(value: String?, parent: Float): Float {
            if (value == null) return parent
            val size = FONT_SIZES[value] ?: when (value) {
                "smaller" -> parent / 1.2f
                "larger" -> parent * 1.2f
                else -> NUMBER.find(value)?.let { match ->
                    val n = match.groupValues[1].toFloat()
                    when (match.groupValues[2]) {
                        "em" -> parent * n
                        "%" -> parent * n / 100
                        "rem" -> n
                        "ex" -> parent * n / 2
                        "px" -> n / 16
                        "pt" -> n / 12
                        else -> null
                    }
                }
            } ?: parent
            return size.coerceIn(0.4f, 4f)
        }

        /** A line height as a multiple of the font size, or null for "normal". */
        private fun lineHeight(value: String, size: Float): Float? {
            val match = NUMBER.find(value) ?: return null
            val n = match.groupValues[1].toFloat()
            val multiple = when (match.groupValues[2]) {
                "" -> n
                "em" -> n
                "%" -> n / 100
                "px" -> n / 16 / size
                "pt" -> n / 12 / size
                else -> return null
            }
            return multiple.coerceIn(0.8f, 3f)
        }

        /** A length in base em; [size] is the element's own font size, which "em" refers to. */
        private fun length(value: String, size: Float): Float? {
            if (value == "0" || value == "auto") return 0f
            val match = NUMBER.find(value.lowercase()) ?: return null
            val n = match.groupValues[1].toFloat()
            return when (match.groupValues[2]) {
                "em" -> n * size
                "rem" -> n
                "ex" -> n * size / 2
                "px", "" -> n / 16
                "pt" -> n / 12
                "pc" -> n
                "%" -> n * 0.3f // of the line's width, taken as about 30em
                "cm" -> n * 37.8f / 16
                "mm" -> n * 3.78f / 16
                "in" -> n * 6
                else -> null
            }
        }
    }
}

private fun Block.withMarginBottom(margin: Float): Block = when (this) {
    is TextBlock -> copy(style = style.copy(marginBottom = margin))
    is ImageBlock -> copy(style = style.copy(marginBottom = margin))
    is RuleBlock -> copy(style = style.copy(marginBottom = margin))
}
