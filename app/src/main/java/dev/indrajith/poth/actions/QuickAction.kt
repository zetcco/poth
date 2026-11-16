package dev.indrajith.poth.actions

/** How a quick action hands text to another app. */
enum class ActionKind {
    SHARE,  // "Share" to one app: AI assistants, notes
    TEXT,   // the app's text-selection action (Translate's pop-up, for one)
    SEARCH, // a web search
    SHEET,  // the system's share sheet, to pick an app each time
}

/**
 * A chip on the dictionary card and editor: hands the word, filled into [template], to an app.
 * [packageName] and [activity] say which app; SEARCH may leave the activity to the system, and
 * SHEET has neither.
 */
data class QuickAction(
    val label: String,
    val kind: ActionKind,
    val packageName: String? = null,
    val activity: String? = null,
    val template: String = Templates.WORD,
)

/** What a quick action is about: the word, and where it was read. */
data class LookupContext(val word: String, val sentence: String, val book: String, val author: String, val chapter: String)

object Templates {
    const val WORD = "{word}"

    /** For AI assistants: enough to answer, and a request not to spoil what comes later. */
    const val ASK =
        "In the book \"{book}\" by {author}, what does \"{word}\" mean? I'm reading {chapter}, at this sentence: " +
            "\"{sentence}\". Answer without spoiling anything that happens after this point."

    val placeholders = listOf("word", "sentence", "book", "author", "chapter")

    /** [template] with each {placeholder} replaced; unknown ones are left as they are. */
    fun fill(template: String, context: LookupContext): String = template
        .replace("{word}", context.word)
        .replace("{sentence}", context.sentence)
        .replace("{book}", context.book)
        .replace("{author}", context.author)
        .replace("{chapter}", context.chapter.ifEmpty { "the book" })
}
