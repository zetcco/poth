package dev.indrajith.poth.epub

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

/**
 * A chapter's text as paragraphs, without any styling: what iteration 0's reader shows to check
 * that a book is read correctly. The real reader lays out styled text instead.
 */
object PlainText {
    // Elements that start and end a paragraph. A <br> also ends one.
    private val blocks = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "section",
        "article", "header", "footer", "aside", "nav", "figure", "figcaption", "table", "tr", "td",
        "th", "dt", "dd", "ul", "ol", "dl", "hr", "body",
    )
    private val spaces = Regex("[ \t\n\r\u000C]+")

    fun paragraphs(chapter: Document): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val text = current.toString().replace(spaces, " ").trim()
            if (text.isNotEmpty()) out += text
            current.setLength(0)
        }
        chapter.body().traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when {
                    node is TextNode -> current.append(node.wholeText)
                    node is Element && (node.normalName() in blocks || node.normalName() == "br") -> flush()
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() in blocks) flush()
            }
        })
        flush()
        return out
    }
}
