package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.R
import dev.indrajith.poth.actions.QuickAction
import dev.indrajith.poth.actions.QuickActions
import dev.indrajith.poth.data.Definition
import dev.indrajith.poth.data.Entry
import dev.indrajith.poth.data.Lookup
import dev.indrajith.poth.data.NoteImages
import dev.indrajith.poth.data.Scope
import dev.indrajith.poth.data.Wiktionary
import dev.indrajith.poth.dictionary.EntryLinks
import dev.indrajith.poth.dictionary.Forms
import dev.indrajith.poth.graph
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which entries the card shows, and the ones links were followed from, so Back can return
 * through them. Opening the card for a word starts afresh.
 */
@Stable
class CardTrail {
    /** Each step's entries, the one shown last. Empty while the card is closed. */
    var steps by mutableStateOf(emptyList<List<Long>>())
        private set

    /** The entries on the card now. */
    val ids: List<Long> get() = steps.lastOrNull().orEmpty()

    /** The entries Back returns to, if a link was followed to get here. */
    val previous: List<Long>? get() = steps.getOrNull(steps.size - 2)

    fun open(ids: List<Long>) {
        steps = listOf(ids)
    }

    /** Follows a link to [ids]; a link to the entries already shown goes nowhere. */
    fun follow(ids: List<Long>) {
        if (ids != this.ids) steps = steps + listOf(ids)
    }

    fun back() {
        steps = steps.dropLast(1)
    }

    fun close() {
        steps = emptyList()
    }
}

/** What the card shows: its entries, how many links deep, and where Back goes. */
// Compared by value: it is written during composition, so an equal one must not count as a change.
private data class CardPage(val entries: List<Entry>, val depth: Int, val previous: String?)

/**
 * The entries of [trail], the most specific first, over the bottom of the screen. [entries] are
 * all the entries it can show. A link in an entry leads on to the entries it names
 * ([onEntryLink]); Back, or the button at the top, goes back through the links followed, and
 * from the first entry closes the card, as a tap above it does. Without [actions] there are no
 * quick actions, and without [onAddAnother] no way to add another meaning.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(
    trail: CardTrail,
    entries: List<Entry>,
    scopeLabel: (Entry) -> String,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAction: (QuickAction) -> Unit,
    onConfigureActions: () -> Unit,
    onEdit: (Entry) -> Unit,
    onAddAnother: ((Entry) -> Unit)?,
    onEntryLink: (String) -> Unit,
) {
    val current = entries.filter { it.id in trail.ids }
    val open = current.isNotEmpty()
    val linkedFrom = remember(entries) { EntryLinks.backlinks(entries) }
    val previous = trail.previous?.let { ids -> entries.firstOrNull { it.id in ids }?.headword }
    // Kept while the card slides away, so it does not empty out on the way.
    var shown by remember { mutableStateOf(CardPage(current, trail.steps.size, previous)) }
    if (open) shown = CardPage(current, trail.steps.size, previous)
    BackHandler(enabled = open) { if (previous != null) trail.back() else trail.close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxCardHeight = maxHeight * 0.7f
        AnimatedVisibility(visible = open, enter = quickFadeIn(), exit = quickFadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { trail.close() } },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = quickSlideIn { it },
            exit = quickSlideOut { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val scroll = rememberScrollState()
            // Each entry followed to starts at its top.
            LaunchedEffect(shown.depth, shown.entries.map(Entry::id)) { scroll.scrollTo(0) }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxCardHeight),
            ) {
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                        .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Following a link slides the next entry in from the right; going back, from the left.
                    AnimatedContent(
                        targetState = shown,
                        contentKey = { page -> page.depth to page.entries.map(Entry::id) },
                        transitionSpec = {
                            val forward = targetState.depth >= initialState.depth
                            val enter = slideInHorizontally(tween(180)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(180))
                            val exit = slideOutHorizontally(tween(140)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(110))
                            enter togetherWith exit
                        },
                        label = "card",
                    ) { page ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (page.previous != null) BackToEntry(page.previous) { trail.back() }
                            page.entries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider(Modifier.padding(end = 12.dp))
                                EntryView(
                                    entry,
                                    scopeLabel(entry),
                                    linkedFrom = linkedFrom[entry.id].orEmpty(),
                                    onEdit = { onEdit(entry) },
                                    onEntryLink = onEntryLink,
                                    onOpen = { trail.follow(listOf(it.id)) },
                                )
                            }
                        }
                    }
                    // One row across the card: the quick actions on the left, another meaning on the right.
                    val first = shown.entries.firstOrNull()
                    val addAnother = if (first != null && onAddAnother != null) ({ onAddAnother(first) }) else null
                    if (actions.isNotEmpty() || addAnother != null) {
                        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (actions.isNotEmpty()) {
                                    QuickActionRow(actions, actionStore, onAction, onConfigureActions, buttonSize = 48.dp, iconSize = 28.dp)
                                }
                            }
                            if (addAnother != null) {
                                Labelled("Add another meaning") {
                                    FilledTonalIconButton(onClick = addAnother) {
                                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Add another meaning")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Back to the entry a link was followed from, by name. */
@Composable
private fun BackToEntry(headword: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 8.dp, end = 16.dp),
        modifier = Modifier.offset(x = (-8).dp),
    ) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(headword, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An entry on the card: its word, where it shows, its notes, and the entries [linkedFrom] it, to [onOpen]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryView(
    entry: Entry,
    scope: String,
    linkedFrom: List<Entry>,
    onEdit: () -> Unit,
    onEntryLink: (String) -> Unit,
    onOpen: (Entry) -> Unit,
) {
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
        if (linkedFrom.isNotEmpty()) {
            FlowRow(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Linked from",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                for (from in linkedFrom) {
                    SuggestionChip(onClick = { onOpen(from) }, label = { Text(from.headword, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    }
}

/**
 * Writing an entry: the word, other ways it is written (and its forms, to add a tap each), and
 * notes in Markdown filling the screen. Under them, by Save, which books see it, so that is the
 * last thing passed on the way out; looking the word up opens a sheet. Shown over the whole
 * screen while [entry] is not null. Closing with changes asks first. [onSave] gets the shelf
 * name to put the book on when the entry is for a shelf and the book is on none yet. [linkable]
 * are the entries offered while writing a [[link]]. With a [place], the entry stays where it
 * shows: it can only be moved from a book. [bookTitle] names the book where it can show, and
 * [wordCounts] (by [Words.key]) are how often the book uses each word, for the forms it has.
 */
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
    linkable: List<Entry> = emptyList(),
    place: String? = null,
    bookTitle: String? = null,
    wordCounts: Map<String, Int>? = null,
) {
    var shown by remember { mutableStateOf(entry) }
    if (entry != null) shown = entry
    AnimatedVisibility(
        visible = entry != null,
        enter = quickSlideIn { it / 4 },
        exit = quickSlideOut { it / 4 },
    ) {
        val editing = shown ?: return@AnimatedVisibility
        EditorContent(
            editing, shelfName, suggestedShelf, language, actions, actionStore, onAction, onConfigureActions,
            onSave, onDelete, onClose, linkable, place, bookTitle, wordCounts,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    linkable: List<Entry>,
    place: String?,
    bookTitle: String?,
    wordCounts: Map<String, Int>?,
) {
    val colors = MaterialTheme.colorScheme
    val key = entry.id to entry.headword
    var headword by rememberSaveable(key) { mutableStateOf(entry.headword) }
    var aliases by rememberSaveable(key) { mutableStateOf(entry.aliases.joinToString(", ")) }
    val notes = rememberSaveable(key, saver = TextFieldState.Saver) { TextFieldState(entry.body) }
    val body = notes.text.toString()
    var notesFocused by remember(key) { mutableStateOf(false) }
    var scope by rememberSaveable(key) { mutableStateOf(entry.scope) }
    var newShelf by rememberSaveable(key) { mutableStateOf(suggestedShelf) }
    var preview by rememberSaveable(key) { mutableStateOf(false) }
    var asking by remember(key) { mutableStateOf<String?>(null) } // "discard" or "delete"
    var lookingUpOpen by rememberSaveable(key) { mutableStateOf(false) }
    // Wiktionary's answer, and the word it was for: the word may have been changed since.
    var lookup by remember(key) { mutableStateOf<Lookup?>(null) }
    var lookedUp by remember(key) { mutableStateOf("") }
    var lookingUp by remember(key) { mutableStateOf(false) }
    val coroutines = rememberCoroutineScope()
    fun lookUp() {
        val word = headword.trim()
        if (lookingUp || word.isEmpty()) return
        lookingUp = true
        coroutines.launch {
            lookup = Wiktionary.define(word, language)
            lookedUp = word
            lookingUp = false
        }
    }
    // Pictures go into the notes where the cursor is, each on a line of its own, once kept.
    val images = LocalContext.current.graph().images
    val resolver = LocalContext.current.contentResolver
    fun addPictures(inputs: List<InputStream>) {
        if (inputs.isEmpty()) return
        coroutines.launch {
            val names = withContext(Dispatchers.IO) { inputs.mapNotNull(images::add) }
            if (names.isEmpty()) return@launch
            notes.edit {
                val start = selection.min
                val before = if (start > 0 && asCharSequence()[start - 1] != '\n') "\n" else ""
                val text = before + names.joinToString("\n", transform = NoteImages::markdown) + "\n"
                replace(start, selection.max, text)
                selection = TextRange(start + text.length)
            }
        }
    }
    val pickPictures = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        addPictures(uris.mapNotNull { runCatching { resolver.openInputStream(it) }.getOrNull() })
    }
    // A general word is looked up straight away; names rarely are in a dictionary.
    LaunchedEffect(key) { if (entry.id == 0L && entry.scope == Scope.LIBRARY && entry.body.isBlank()) lookUp() }

    val word = headword.trim()
    val found = (lookup as? Lookup.Found)?.takeIf { lookedUp == word }
    val general = scope == Scope.LIBRARY
    // A new entry for a form of a word (lounged) offers to be for the word itself.
    val baseWord = found?.formOf?.takeIf { entry.id == 0L && !it.equals(word, ignoreCase = true) }
    // A general word's forms, each once Wiktionary says it is one (so not "seed" for see).
    var sureForms by remember(key) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(found, general) {
        sureForms = emptyList()
        if (found == null || !general || found.formOf != null) return@LaunchedEffect
        val guesses = Forms.regular(word, Forms.kinds(found.partsOfSpeech))
        sureForms = guesses.map { async { it.takeIf { Wiktionary.isFormOf(it, word, language) } } }.awaitAll().filterNotNull()
    }
    val forms = remember(word, general, found, sureForms, wordCounts) {
        // Otherwise, forms by rule that the book uses: a name's plural, a general word's endings.
        val likely = when {
            general && found != null -> emptyList()
            general -> Forms.regular(word, setOf(Forms.Kind.NOUN, Forms.Kind.VERB))
            else -> Forms.regular(word, setOf(Forms.Kind.NOUN))
        }
        Forms.offer(word, sureForms, likely, wordCounts)
    }
    val aliasList = aliases.split(',').map(String::trim).filter(String::isNotEmpty)
    fun hasAlias(form: String) = aliasList.any { it.equals(form, ignoreCase = true) }
    fun setAliases(list: List<String>) {
        aliases = list.joinToString(", ")
    }

    val needsShelf = place == null && scope == Scope.SHELF && shelfName == null
    val edited = entry.copy(
        headword = word,
        aliases = aliasList,
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
    // Where the entry shows is chosen in a menu over the editor, opening up from its button.
    var choosingScope by remember(key) { mutableStateOf(false) }
    var scopeAnchor by remember { mutableStateOf(Rect.Zero) }
    BackHandler(enabled = choosingScope) { choosingScope = false }

    Surface(color = colors.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                    Text(if (entry.id == 0L) "New entry" else "Edit entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (entry.id != 0L) {
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), contentDescription = "More") }
                            DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1), containerColor = colors.surfaceContainerHigh) {
                                    DropdownMenuItem(
                                        onClick = {
                                            menu = false
                                            asking = "delete"
                                        },
                                        text = { Text("Delete entry") },
                                        shape = MenuDefaults.standaloneItemShape,
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                        colors = MenuDefaults.itemColors(textColor = colors.error, leadingIconColor = colors.error),
                                    )
                                }
                            }
                        }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    FillingColumn(
                        height = maxHeight - 16.dp,
                        lastMin = 160.dp,
                        spacing = 12.dp,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        OutlinedTextField(headword, { headword = it }, label = { Text("Word") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (baseWord != null) {
                            BaseWordNote(found?.definitions?.firstOrNull()?.text.orEmpty(), baseWord) {
                                if (!hasAlias(word)) setAliases(aliasList + word)
                                headword = baseWord
                                lookUp()
                            }
                        }
                        OutlinedTextField(
                            aliases,
                            { aliases = it },
                            label = { Text("Also matches") },
                            placeholder = { Text("Other spellings or names, separated by commas") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (forms.isNotEmpty()) {
                            FormChips(
                                word = word,
                                forms = forms,
                                added = ::hasAlias,
                                onToggle = { form -> setAliases(if (hasAlias(form)) aliasList.filterNot { it.equals(form, ignoreCase = true) } else aliasList + form) },
                                onAddAll = if (sureForms.isEmpty()) null else ({ setAliases(aliasList + forms.map { it.text }.filterNot(::hasAlias)) }),
                            )
                        }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Notes", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            IconButton(onClick = { pickPictures.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(painterResource(R.drawable.ic_add_photo), contentDescription = "Add pictures")
                            }
                            TextButton(onClick = { preview = !preview }) { Text(if (preview) "Edit" else "Preview") }
                        }
                        if (preview) {
                            Box(Modifier.fillMaxWidth().border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp)).padding(16.dp)) {
                                if (body.isBlank()) {
                                    Text("Nothing written yet.", color = colors.onSurfaceVariant)
                                } else {
                                    MarkdownText(body, onEntryLink = {})
                                }
                            }
                        } else {
                            OutlinedTextField(
                                state = notes,
                                placeholder = { Text("Markdown: **bold**, *italic*, - lists, # headings, [[another entry]]. Paste or add pictures too.") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { notesFocused = it.isFocused }
                                    // Pictures pasted, or sent from the keyboard (a screenshot from its clipboard).
                                    .contentReceiver { content ->
                                        if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                                        val inputs = mutableListOf<InputStream>()
                                        // Opened at once, while the app that sent them still lets us read them.
                                        val rest = content.consume { item ->
                                            val input = item.uri?.let { runCatching { resolver.openInputStream(it) }.getOrNull() }
                                            input?.let(inputs::add) != null
                                        }
                                        addPictures(inputs)
                                        rest
                                    },
                            )
                        }
                    }
                }
                EditorBar(
                    writing = notesFocused && !preview,
                    notes = TextFieldValue(body, notes.selection),
                    linkable = linkable,
                    word = word,
                    onNotes = { edited ->
                        notes.edit {
                            replace(0, length, edited.text)
                            selection = edited.selection
                        }
                    },
                    onLookUp = {
                        lookingUpOpen = true
                        if (found == null) lookUp()
                    },
                    canSave = canSave,
                    onSave = { onSave(edited, if (needsShelf) newShelf.trim() else null) },
                ) {
                    ScopeButton(
                        scope = scope,
                        place = place,
                        open = choosingScope,
                        onClick = { choosingScope = !choosingScope },
                        modifier = Modifier.onGloballyPositioned { scopeAnchor = it.boundsInRoot() },
                    )
                }
            }
            if (choosingScope) {
                // A tap anywhere else closes the menu.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { choosingScope = false } })
            }
            AnimatedVisibility(
                visible = choosingScope,
                enter = quickFadeIn() + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
                exit = quickFadeOut() + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
                modifier = Modifier.above(scopeAnchor, gap = 8.dp, margin = 12.dp),
            ) {
                ScopeMenu(scope, place, bookTitle, shelfName ?: newShelf.trim().ifEmpty { null }) {
                    scope = it
                    choosingScope = false
                }
            }
        }
    }

    if (lookingUpOpen) {
        LookUpSheet(
            word = word,
            lookup = lookup?.takeIf { lookedUp == word },
            lookingUp = lookingUp,
            actions = actions,
            actionStore = actionStore,
            onAction = { onAction(it, word) },
            onConfigureActions = {
                lookingUpOpen = false
                onConfigureActions()
            },
            onLookUp = ::lookUp,
            added = { body.contains(Wiktionary.markdown(it)) },
            onPick = { definition ->
                val line = Wiktionary.markdown(definition)
                val text = if (body.isBlank()) line else body.trimEnd() + "\n" + line
                notes.setTextAndPlaceCursorAtEnd(text)
            },
            onDismiss = { lookingUpOpen = false },
        )
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

/**
 * A column whose last child gets the height left over in [height], and no less than [lastMin]:
 * the notes fill the screen, and once there is more than fits, the column grows (and scrolls).
 */
@Composable
private fun FillingColumn(height: Dp, lastMin: Dp, spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val above = measurables.dropLast(1).map { it.measure(loose) }
        val used = above.sumOf { it.height } + gap * above.size
        val lastHeight = max(lastMin.roundToPx(), height.roundToPx() - used)
        val last = measurables.lastOrNull()?.measure(constraints.copy(minHeight = lastHeight, maxHeight = Constraints.Infinity))
        layout(constraints.maxWidth, used + (last?.height ?: 0)) {
            var y = 0
            for (placeable in above) {
                placeable.placeRelative(0, y)
                y += placeable.height + gap
            }
            last?.placeRelative(0, y)
        }
    }
}

/** Wiktionary has the word as a form of another ([base]): [what] it says, and a way to write the entry for that one. */
@Composable
private fun BaseWordNote(what: String, base: String, onUse: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wiktionary: $what", style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer, modifier = Modifier.weight(1f))
            Button(onClick = onUse) { Text("Use “$base”", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * Forms of the word to add to what it also matches, a tap each (again to take one off), with how
 * often the book uses them. Given [onAddAll], they are the word's own and can be added at once.
 */
@Composable
private fun FormChips(word: String, forms: List<Forms.Form>, added: (String) -> Boolean, onToggle: (String) -> Unit, onAddAll: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (onAddAll != null) "Forms of “$word”" else "In this book",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            val missing = forms.count { !added(it.text) }
            if (onAddAll != null && missing >= 2) {
                TextButton(onClick = onAddAll) {
                    Icon(painterResource(R.drawable.ic_done_all), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add all $missing")
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (form in forms) {
                val on = added(form.text)
                FilterChip(
                    selected = on,
                    onClick = { onToggle(form.text) },
                    label = {
                        Text(
                            buildAnnotatedString {
                                append(form.text)
                                if ((form.count ?: 0) > 0) withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("  ${form.count}") }
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(if (on) R.drawable.ic_check else R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

/**
 * The bar under the editor, riding on the keyboard as it comes and goes: Look up (or, while
 * notes are written, a [[link]] to another entry), [scope], and Save. While a link is being
 * typed, the entries it could be take the whole bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorBar(
    writing: Boolean,
    notes: TextFieldValue,
    linkable: List<Entry>,
    word: String,
    onNotes: (TextFieldValue) -> Unit,
    onLookUp: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    scope: @Composable () -> Unit,
) {
    val navigation = WindowInsets.navigationBarsIgnoringVisibility
    val keyboard = WindowInsets.ime
    val going = WindowInsets.imeAnimationTarget
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Laid out once for where the keyboard is going; on the way, only moved.
        modifier = Modifier.offset {
            val below = navigation.getBottom(this)
            IntOffset(0, max(below, going.getBottom(this)) - max(below, keyboard.getBottom(this)))
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(navigation.union(going))
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selection = notes.selection
            val open = if (writing && selection.collapsed) EntryLinks.open(notes.text, selection.start) else null
            if (open != null) {
                LinkSuggestions(notes, open, linkable, word, onNotes, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (writing) {
                        val selected = notes.text.substring(selection.min, selection.max).trim()
                        AssistChip(
                            onClick = { EntryLinks.start(notes.text, selection.min, selection.max).let { onNotes(TextFieldValue(it.first, TextRange(it.second))) } },
                            label = { Text(if (selected.isEmpty()) "Link an entry" else "Link “$selected”", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_link), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    } else {
                        TextButton(onClick = onLookUp, enabled = word.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Look up")
                        }
                    }
                }
                scope()
                Button(onClick = onSave, enabled = canSave) { Text("Save") }
            }
        }
    }
}

/** While a [[link]] is being typed: the entries it could be, tapping one finishes it. */
@Composable
private fun LinkSuggestions(notes: TextFieldValue, open: EntryLinks.Open, entries: List<Entry>, writing: String, onChange: (TextFieldValue) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val suggestions = remember(entries, open.query, writing) { EntryLinks.suggest(entries, open.query, writing) }
    fun change(result: Pair<String, Int>) = onChange(TextFieldValue(result.first, TextRange(result.second)))
    val chipColors = SuggestionChipDefaults.suggestionChipColors(labelColor = colors.onSurface)
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (suggestion in suggestions) {
            SuggestionChip(
                onClick = { change(EntryLinks.complete(notes.text, notes.selection.start, open, suggestion.term)) },
                label = { Text(if (suggestion.term == suggestion.headword) suggestion.term else "${suggestion.term} · ${suggestion.headword}") },
                colors = chipColors,
            )
        }
        val typed = open.query.trim()
        if (typed.isNotEmpty() && suggestions.none { it.term.equals(typed, ignoreCase = true) }) {
            // A link to an entry not written yet: tapping it later starts that entry.
            SuggestionChip(
                onClick = { change(EntryLinks.complete(notes.text, notes.selection.start, open, typed)) },
                label = { Text("“$typed” (new)") },
                colors = chipColors,
            )
        } else if (suggestions.isEmpty()) {
            Text("No other entries yet. Type a word, then ]]", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private val Scope.label get() = when (this) {
    Scope.BOOK -> "Book"
    Scope.SHELF -> "Shelf"
    Scope.LIBRARY -> "Library"
}

private val Scope.icon get() = when (this) {
    Scope.BOOK -> R.drawable.ic_book
    Scope.SHELF -> R.drawable.ic_shelf
    Scope.LIBRARY -> R.drawable.ic_library
}

/** Where the entry shows, beside Save; with a [place] it can't be changed here, only told. */
@Composable
private fun ScopeButton(scope: Scope, place: String?, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(start = 12.dp, end = if (place == null) 6.dp else 16.dp)) {
        Icon(painterResource(scope.icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(place ?: scope.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        if (place == null) {
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
    }
}

/**
 * Choosing where the entry shows: Book, Shelf or Library, each saying what it means here
 * ([bookTitle], [shelf]). With a [place] there is no choice, only where it is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeMenu(scope: Scope, place: String?, bookTitle: String?, shelf: String?, onSelect: (Scope) -> Unit) {
    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(0, 1),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(min = 248.dp, max = 320.dp),
    ) {
        if (place != null) {
            Text(
                "Shows in $place. To move it, edit it from a book it shows in.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            val options = Scope.entries
            // The choice in the tonal colour of its button; the others on the menu itself.
            val itemColors = MenuDefaults.selectableItemColors(
                containerColor = Color.Transparent,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            options.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = option == scope,
                    onClick = { onSelect(option) },
                    text = { Text(option.label) },
                    shapes = MenuDefaults.itemShape(index, options.size),
                    leadingIcon = { Icon(painterResource(option.icon), contentDescription = null) },
                    selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                    colors = itemColors,
                    supportingText = {
                        Text(
                            when (option) {
                                Scope.BOOK -> "Only in ${bookTitle ?: "this book"}"
                                Scope.SHELF -> "Every book on ${shelf ?: "this book's shelf"}"
                                Scope.LIBRARY -> "Every book: for general words"
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * Places this, in a parent filling the screen, just above [anchor] (in root coordinates) with
 * its right edge on the anchor's, and kept [margin] from the sides.
 */
private fun Modifier.above(anchor: Rect, gap: Dp, margin: Dp) = this
    .fillMaxSize()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val side = margin.roundToPx()
            val x = (anchor.right.roundToInt() - placeable.width).coerceIn(side, max(side, constraints.maxWidth - placeable.width - side))
            placeable.place(x, anchor.top.roundToInt() - gap.roundToPx() - placeable.height)
        }
    }

/**
 * Looking the word up, in a sheet over the editor: the quick actions, and Wiktionary's
 * definitions (once [lookup] is in), + to add one to the notes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LookUpSheet(
    word: String,
    lookup: Lookup?,
    lookingUp: Boolean,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAction: (QuickAction) -> Unit,
    onConfigureActions: () -> Unit,
    onLookUp: () -> Unit,
    added: (Definition) -> Boolean,
    onPick: (Definition) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text("Look up “$word”", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            if (actions.isNotEmpty()) {
                QuickActionRow(actions, actionStore, onAction, onConfigureActions, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wiktionary", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (lookingUp) {
                    LoadingIndicator(Modifier.padding(12.dp).size(24.dp))
                } else {
                    TextButton(onClick = onLookUp, enabled = word.isNotEmpty()) { Text(if (lookup == null) "Look up" else "Again") }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 12.dp)) {
                when (lookup) {
                    is Lookup.Found -> for (definition in lookup.definitions) {
                        val done = added(definition)
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !done) { onPick(definition) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                buildAnnotatedString {
                                    if (definition.partOfSpeech.isNotEmpty()) {
                                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.primary)) { append(definition.partOfSpeech) }
                                        append("  ")
                                    }
                                    append(definition.text)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                            )
                            Icon(
                                painterResource(if (done) R.drawable.ic_check else R.drawable.ic_add),
                                contentDescription = if (done) "In the notes" else "Add to the notes",
                                tint = colors.primary,
                                modifier = Modifier.padding(12.dp).size(20.dp),
                            )
                        }
                    }
                    Lookup.NotFound -> Text("Wiktionary has no entry for “$word”.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    is Lookup.Failed -> Text(lookup.reason, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                    null -> Text("Definitions of the word, if it is in the dictionary. Only the word is sent.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
