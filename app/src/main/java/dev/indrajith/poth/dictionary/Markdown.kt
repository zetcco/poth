package dev.indrajith.poth.dictionary

/**
 * The Markdown that dictionary entries are written in, read into blocks of text with styled
 * ranges. A small subset, enough for notes: headings, paragraphs, lists, quotes, rules, and
 * **bold**, *italic*, `code`, [links](url) and [[links to other entries]]. Single line breaks
 * are kept, as note apps do.
 */
object Markdown {
    enum class Kind { PARAGRAPH, HEADING, BULLET, NUMBERED, QUOTE, RULE }

    enum class Style { BOLD, ITALIC, CODE, LINK, ENTRY }

    /** [style] from [start] until [end]; [target] is a link's address or the entry it names. */
    data class Span(val start: Int, val end: Int, val style: Style, val target: String? = null)

    /** [level] is a heading's level or a list item's indent; [marker] is a numbered item's "3.". */
    data class Block(val kind: Kind, val text: String, val spans: List<Span> = emptyList(), val level: Int = 0, val marker: String = "")

    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val rule = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val quote = Regex("^>\\s?(.*)$")

    fun parse(source: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += block(Kind.PARAGRAPH, paragraph.joinToString("\n"))
            paragraph.clear()
        }
        var quoteSource: String? = null // the Markdown of the quote being read, while lines continue it
        for (raw in source.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            val continuing = quoteSource
            quoteSource = null
            when {
                trimmed.isEmpty() -> flush()
                heading.matches(trimmed) -> {
                    flush()
                    val match = heading.find(trimmed)!!
                    blocks += block(Kind.HEADING, match.groupValues[2], level = match.groupValues[1].length)
                }
                rule.matches(trimmed) -> {
                    flush()
                    blocks += Block(Kind.RULE, "")
                }
                bullet.matches(line) -> {
                    flush()
                    val match = bullet.find(line)!!
                    blocks += block(Kind.BULLET, match.groupValues[2], level = match.groupValues[1].length / 2)
                }
                numbered.matches(line) -> {
                    flush()
                    val match = numbered.find(line)!!
                    blocks += block(Kind.NUMBERED, match.groupValues[3], level = match.groupValues[1].length / 2, marker = "${match.groupValues[2]}.")
                }
                quote.matches(trimmed) -> {
                    flush()
                    val text = quote.find(trimmed)!!.groupValues[1]
                    if (continuing != null) {
                        val joined = continuing + "\n" + text
                        quoteSource = joined
                        blocks[blocks.lastIndex] = block(Kind.QUOTE, joined)
                    } else {
                        quoteSource = text
                        blocks += block(Kind.QUOTE, text)
                    }
                }
                else -> paragraph += trimmed
            }
        }
        flush()
        return blocks
    }

    private fun block(kind: Kind, source: String, level: Int = 0, marker: String = ""): Block {
        val (text, spans) = inline(source)
        return Block(kind, text, spans, level, marker)
    }

    private val inlinePatterns = listOf(
        Regex("`([^`]+)`") to Style.CODE,
        Regex("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?]]") to Style.ENTRY,
        Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)") to Style.LINK,
        Regex("\\*\\*(.+?)\\*\\*|__(.+?)__") to Style.BOLD,
        Regex("\\*(?!\\s)(.+?)(?<!\\s)\\*|(?<![\\p{L}\\p{N}])_(?!\\s)(.+?)(?<!\\s)_(?![\\p{L}\\p{N}])") to Style.ITALIC,
    )

    /** One line or paragraph of inline Markdown, as plain text and the ranges styled. */
    fun inline(source: String): Pair<String, List<Span>> {
        val out = StringBuilder()
        val spans = mutableListOf<Span>()
        inline(source, out, spans)
        return out.toString() to spans.sortedBy { it.start }
    }

    private fun inline(source: String, out: StringBuilder, spans: MutableList<Span>) {
        var i = 0
        while (i < source.length) {
            // The earliest match wins; at the same place, the earlier pattern.
            val (match, style) = inlinePatterns
                .mapNotNull { (pattern, style) -> pattern.find(source, i)?.let { it to style } }
                .minByOrNull { it.first.range.first }
                ?: break
            out.append(source, i, match.range.first)
            val start = out.length
            when (style) {
                Style.CODE -> {
                    out.append(match.groupValues[1])
                    spans += Span(start, out.length, style)
                }
                Style.ENTRY -> {
                    val target = match.groupValues[1].trim()
                    out.append(match.groupValues[2].ifEmpty { target })
                    spans += Span(start, out.length, style, target)
                }
                Style.LINK -> {
                    inline(match.groupValues[1], out, spans)
                    spans += Span(start, out.length, style, match.groupValues[2])
                }
                Style.BOLD, Style.ITALIC -> {
                    inline(match.groupValues[1].ifEmpty { match.groupValues[2] }, out, spans)
                    spans += Span(start, out.length, style)
                }
            }
            i = match.range.last + 1
        }
        if (i < source.length) out.append(source, i, source.length)
    }
}
