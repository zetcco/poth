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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.dictionary.Markdown

/** An entry's notes, from Markdown. A [[link]] to another entry goes to [onEntryLink]. */
@Composable
fun MarkdownText(source: String, onEntryLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(source) { Markdown.parse(source) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            val text = annotated(block, colors, onEntryLink)
            when (block.kind) {
                Markdown.Kind.HEADING -> Text(
                    text,
                    style = when (block.level) {
                        1 -> type.titleLarge
                        2 -> type.titleMedium
                        else -> type.titleSmall
                    },
                )
                Markdown.Kind.PARAGRAPH -> Text(text, style = type.bodyLarge)
                Markdown.Kind.BULLET, Markdown.Kind.NUMBERED -> Row(Modifier.padding(start = (block.level * 16).dp)) {
                    Text(if (block.kind == Markdown.Kind.BULLET) "•" else block.marker, style = type.bodyLarge, modifier = Modifier.width(24.dp))
                    Text(text, style = type.bodyLarge)
                }
                Markdown.Kind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.outlineVariant))
                    Text(text, style = type.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                }
                Markdown.Kind.RULE -> HorizontalDivider()
            }
        }
    }
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
