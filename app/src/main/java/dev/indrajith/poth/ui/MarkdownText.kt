package dev.indrajith.poth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.dictionary.Markdown
import kotlin.math.max
import kotlin.math.min

/**
 * An entry's notes, from Markdown. A [[link]] to another entry has the dotted underline that
 * dictionary words have on the page, and goes to [onEntryLink].
 */
@Composable
fun MarkdownText(source: String, onEntryLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(source) { Markdown.parse(source) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            val text = annotated(block, colors, onEntryLink)
            val links = block.spans.filter { it.style == Markdown.Style.ENTRY }
            when (block.kind) {
                Markdown.Kind.HEADING -> BlockText(
                    text,
                    links,
                    style = when (block.level) {
                        1 -> type.titleLarge
                        2 -> type.titleMedium
                        else -> type.titleSmall
                    },
                )
                Markdown.Kind.PARAGRAPH -> BlockText(text, links, style = type.bodyLarge)
                Markdown.Kind.BULLET, Markdown.Kind.NUMBERED -> Row(Modifier.padding(start = (block.level * 16).dp)) {
                    Text(if (block.kind == Markdown.Kind.BULLET) "•" else block.marker, style = type.bodyLarge, modifier = Modifier.width(24.dp))
                    BlockText(text, links, style = type.bodyLarge)
                }
                Markdown.Kind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.outlineVariant))
                    BlockText(text, links, style = type.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                }
                Markdown.Kind.RULE -> HorizontalDivider()
                Markdown.Kind.IMAGE -> if (block.target.startsWith("img:")) {
                    NoteImage(block.target.removePrefix("img:"), block.text)
                } else {
                    // Only pictures kept with the notes are shown; one elsewhere is named.
                    Text("Picture: ${block.text.ifEmpty { block.target }}", style = type.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * A block's text, with a dotted line under each of [links], as dictionary words are underlined
 * on the page: dashes a fifth of the type size long, a little below the baseline.
 */
@Composable
private fun BlockText(
    text: AnnotatedString,
    links: List<Markdown.Span>,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Text(
        text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            if (links.isEmpty()) return@drawBehind
            val fontPx = style.fontSize.toPx()
            val dashes = PathEffect.dashPathEffect(floatArrayOf(fontPx * 0.2f, fontPx * 0.12f))
            val width = max(1f, fontPx / 16)
            val length = result.layoutInput.text.length
            for (link in links) {
                val end = min(link.end, length)
                if (link.start >= end) continue
                for (line in result.getLineForOffset(link.start)..result.getLineForOffset(end - 1)) {
                    val from = max(link.start, result.getLineStart(line))
                    val to = min(end, result.getLineEnd(line, visibleEnd = true))
                    if (from >= to) continue
                    val y = result.getLineBaseline(line) + fontPx * 0.2f
                    drawLine(lineColor, Offset(result.getBoundingBox(from).left, y), Offset(result.getBoundingBox(to - 1).right, y), width, pathEffect = dashes)
                }
            }
        },
    )
}

private fun annotated(block: Markdown.Block, colors: ColorScheme, onEntryLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    append(block.text)
    for (span in block.spans) {
        when (span.style) {
            Markdown.Style.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
            Markdown.Style.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
            Markdown.Style.CODE -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest), span.start, span.end)
            Markdown.Style.LINK -> addLink(
                LinkAnnotation.Url(span.target.orEmpty(), TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))),
                span.start,
                span.end,
            )
            Markdown.Style.ENTRY -> {
                val target = span.target.orEmpty()
                addLink(
                    LinkAnnotation.Clickable(target, TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium))) { onEntryLink(target) },
                    span.start,
                    span.end,
                )
            }
        }
    }
}
