package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.R
import dev.indrajith.poth.actions.QuickAction
import dev.indrajith.poth.actions.QuickActions
import dev.indrajith.poth.data.Definition
import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Lookup
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.data.Wiktionary
import kotlinx.coroutines.launch

/**
 * The entries for a tapped word, the most specific first, over the bottom of the page. Shown
 * while [entries] is not empty; Back or a tap above it closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(
    entries: List<Entry>,
    scopeLabel: (Entry) -> String,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAction: (QuickAction) -> Unit,
    onConfigureActions: () -> Unit,
    onEdit: (Entry) -> Unit,
    onAddAnother: (Entry) -> Unit,
    onEntryLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val open = entries.isNotEmpty()
    // Kept while the card slides away, so it does not empty out on the way.
    var shown by remember { mutableStateOf(entries) }
    if (open) shown = entries
    BackHandler(enabled = open, onBack = onClose)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxCardHeight = maxHeight * 0.7f
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxCardHeight),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                        .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    shown.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(Modifier.padding(end = 12.dp))
                        EntryView(entry, scopeLabel(entry), onEdit = { onEdit(entry) }, onEntryLink = onEntryLink)
                    }
                    QuickActionRow(actions, actionStore, onAction, onConfigureActions)
                    shown.firstOrNull()?.let { first ->
                        TextButton(onClick = { onAddAnother(first) }) { Text("Add another meaning") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryView(entry: Entry, scope: String, onEdit: () -> Unit, onEntryLink: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.headword, style = MaterialTheme.typography.headlineSmall)
                val also = entry.aliases.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " · also ")
                Text(scope + also.orEmpty(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit ${entry.headword}") }
        }
        Box(Modifier.padding(end = 12.dp)) {
            if (entry.body.isBlank()) {
                Text("No notes yet.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                MarkdownText(entry.body, onEntryLink)
            }
        }
    }
}

/**
 * Writing an entry: the word, other ways it is written, which books see it, and notes in
 * Markdown. Shown over the whole screen while [entry] is not null. Closing with changes asks first.
 * [onSave] gets the shelf name to put the book on when the entry is for a shelf and the book is
 * on none yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(
    entry: Entry?,
    shelfName: String?,
    suggestedShelf: String,
    language: String,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAction: (QuickAction, word: String) -> Unit,
    onConfigureActions: () -> Unit,
    onSave: (Entry, newShelf: String?) -> Unit,
    onDelete: (Entry) -> Unit,
    onClose: () -> Unit,
) {
    var shown by remember { mutableStateOf(entry) }
    if (entry != null) shown = entry
    AnimatedVisibility(
        visible = entry != null,
        enter = slideInVertically { it / 4 } + fadeIn(),
        exit = slideOutVertically { it / 4 } + fadeOut(),
    ) {
        val editing = shown ?: return@AnimatedVisibility
        EditorContent(editing, shelfName, suggestedShelf, language, actions, actionStore, onAction, onConfigureActions, onSave, onDelete, onClose)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorContent(
    entry: Entry,
    shelfName: String?,
    suggestedShelf: String,
    language: String,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAction: (QuickAction, word: String) -> Unit,
    onConfigureActions: () -> Unit,
    onSave: (Entry, newShelf: String?) -> Unit,
    onDelete: (Entry) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val key = entry.id to entry.headword
    var headword by rememberSaveable(key) { mutableStateOf(entry.headword) }
    var aliases by rememberSaveable(key) { mutableStateOf(entry.aliases.joinToString(", ")) }
    var body by rememberSaveable(key) { mutableStateOf(entry.body) }
    var scope by rememberSaveable(key) { mutableStateOf(entry.scope) }
    var newShelf by rememberSaveable(key) { mutableStateOf(suggestedShelf) }
    var preview by rememberSaveable(key) { mutableStateOf(false) }
    var asking by remember(key) { mutableStateOf<String?>(null) } // "discard" or "delete"
    var lookup by remember(key) { mutableStateOf<Lookup?>(null) }
    var lookingUp by remember(key) { mutableStateOf(false) }
    val coroutines = rememberCoroutineScope()
    fun lookUp() {
        if (lookingUp || headword.isBlank()) return
        lookingUp = true
        coroutines.launch {
            lookup = Wiktionary.define(headword, language)
            lookingUp = false
        }
    }
    // A general word is looked up straight away; names rarely are in a dictionary.
    LaunchedEffect(key) { if (entry.id == 0L && entry.scope == Scope.LIBRARY && entry.body.isBlank()) lookUp() }

    val needsShelf = scope == Scope.SHELF && shelfName == null
    val edited = entry.copy(
        headword = headword.trim(),
        aliases = aliases.split(',').map(String::trim).filter(String::isNotEmpty),
        body = body,
        scope = scope,
    )
    val changed = edited.headword != entry.headword || edited.aliases != entry.aliases || edited.body != entry.body ||
        edited.scope != entry.scope || entry.id == 0L
    val canSave = headword.isNotBlank() && (!needsShelf || newShelf.isNotBlank())
    val close = {
        if (changed && (body.isNotBlank() || entry.id != 0L)) {
            asking = "discard"
        } else {
            onClose()
        }
    }
    BackHandler(onBack = close)

    Surface(color = colors.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.navigationBarsIgnoringVisibility))
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                Text(if (entry.id == 0L) "New entry" else "Edit entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onSave(edited, if (needsShelf) newShelf.trim() else null) },
                    enabled = canSave,
                    modifier = Modifier.padding(end = 12.dp),
                ) { Text("Save") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(headword, { headword = it }, label = { Text("Word") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    aliases,
                    { aliases = it },
                    label = { Text("Also matches") },
                    supportingText = { Text("Other spellings or names, separated by commas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Shows in", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                ScopeButtons(scope) { scope = it }
                Text(
                    when (scope) {
                        Scope.BOOK -> "Only this book."
                        Scope.SHELF -> if (shelfName != null) "Every book on $shelfName." else "Every book on this book's shelf."
                        Scope.LIBRARY -> "Every book: for general words."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (needsShelf) {
                    OutlinedTextField(
                        newShelf,
                        { newShelf = it },
                        label = { Text("Shelf for this book") },
                        supportingText = { Text("Books on the same shelf share their entries") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Look it up", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                QuickActionRow(actions, actionStore, { onAction(it, headword.trim()) }, onConfigureActions)
                Definitions(
                    lookup = lookup,
                    lookingUp = lookingUp,
                    word = headword.trim(),
                    onLookUp = ::lookUp,
                    onPick = { definition ->
                        val line = Wiktionary.markdown(definition)
                        body = if (body.isBlank()) line else body.trimEnd() + "\n" + line
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { preview = !preview }) { Text(if (preview) "Edit" else "Preview") }
                }
                if (preview) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp))
                            .padding(16.dp),
                    ) {
                        if (body.isBlank()) {
                            Text("Nothing written yet.", color = colors.onSurfaceVariant)
                        } else {
                            MarkdownText(body, onEntryLink = {})
                        }
                    }
                } else {
                    OutlinedTextField(
                        body,
                        { body = it },
                        placeholder = { Text("Markdown: **bold**, *italic*, - lists, # headings, [[another entry]]") },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (entry.id != 0L) {
                    TextButton(
                        onClick = { asking = "delete" },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Delete entry")
                    }
                }
            }
        }
    }

    when (asking) {
        "discard" -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Discard changes?") },
            text = { Text("What you changed in this entry will be lost.") },
            confirmButton = { TextButton(onClick = { asking = null; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Keep editing") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Delete “${entry.headword}”?") },
            text = { Text("The entry and its notes will be gone from every book that shows it.") },
            confirmButton = { TextButton(onClick = { asking = null; onDelete(entry) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } },
        )
    }
}

/** Wiktionary's definitions of the word, once looked up; tapping one adds it to the notes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Definitions(lookup: Lookup?, lookingUp: Boolean, word: String, onLookUp: () -> Unit, onPick: (Definition) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wiktionary", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (lookingUp) {
                    LoadingIndicator(Modifier.padding(12.dp).size(24.dp))
                } else {
                    TextButton(onClick = onLookUp, enabled = word.isNotEmpty()) { Text(if (lookup == null) "Look up" else "Again") }
                }
            }
            when (lookup) {
                is Lookup.Found -> {
                    for (definition in lookup.definitions) {
                        Text(
                            buildAnnotatedString {
                                if (definition.partOfSpeech.isNotEmpty()) {
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.primary)) { append(definition.partOfSpeech) }
                                    append("  ")
                                }
                                append(definition.text)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(definition) }.padding(vertical = 6.dp),
                        )
                    }
                    Text("Tap one to add it to the notes. Only the word was sent.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Lookup.NotFound -> Text("Wiktionary has no entry for “$word”.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                is Lookup.Failed -> Text(lookup.reason, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                null -> Text("Definitions of the word, if it is in the dictionary. Only the word is sent.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Book, Shelf, Library as one connected group of toggle buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeButtons(selected: Scope, onSelect: (Scope) -> Unit) {
    val options = listOf(Scope.BOOK to "Book", Scope.SHELF to "Shelf", Scope.LIBRARY to "Library")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (scope, label) ->
            ToggleButton(
                checked = selected == scope,
                onCheckedChange = { onSelect(scope) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) { Text(label) }
        }
    }
}
