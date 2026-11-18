package dev.indrajith.poth.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

/** One sense of a word, as Wiktionary gives it; [formOf] is the word it is a form of ("plural of brute"). */
data class Definition(val partOfSpeech: String, val text: String, val formOf: String? = null)

sealed interface Lookup {
    data class Found(val definitions: List<Definition>) : Lookup {
        /** In Wiktionary's order, the commonest first. */
        val partsOfSpeech get() = definitions.map { it.partOfSpeech }.distinct()

        /** The word this one is a form of, if that is what it mainly is ("lounged": lounge). */
        val formOf get() = definitions.firstOrNull()?.formOf
    }
    data object NotFound : Lookup
    data class Failed(val reason: String) : Lookup
}

/**
 * Definitions from Wiktionary's free API (no key). Only the word leaves the phone: no sentence,
 * no book. Words made up for a book (Parshendi) will not be there.
 */
object Wiktionary {
    private const val API = "https://en.wiktionary.org/api/rest_v1/page/definition/"
    private const val PER_PART_OF_SPEECH = 3

    /** Looks [word] up as written, then in lowercase if it is capitalised and was not found. */
    suspend fun define(word: String, language: String): Lookup = withContext(Dispatchers.IO) {
        val first = fetch(word.trim(), language)
        if (first == Lookup.NotFound && word.any(Char::isUpperCase)) fetch(word.trim().lowercase(), language) else first
    }

    /** Whether Wiktionary has [form] as a form of [word] (lounged of lounge; not seed of see). */
    suspend fun isFormOf(form: String, word: String, language: String): Boolean =
        (define(form, language) as? Lookup.Found)?.definitions.orEmpty().any { it.formOf.equals(word, ignoreCase = true) }

    private fun fetch(word: String, language: String): Lookup {
        if (word.isEmpty()) return Lookup.NotFound
        val title = URLEncoder.encode(word.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val connection = URL(API + title).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            // Wikimedia asks API users to say who they are.
            connection.setRequestProperty("User-Agent", "poth/0.1 (personal EPUB reader for Android)")
            when (connection.responseCode) {
                200 -> {
                    val definitions = parse(connection.inputStream.bufferedReader().use { it.readText() }, language)
                    if (definitions.isEmpty()) Lookup.NotFound else Lookup.Found(definitions)
                }
                404 -> Lookup.NotFound
                else -> Lookup.Failed("Wiktionary answered ${connection.responseCode}")
            }
        } catch (e: IOException) {
            Lookup.Failed("Couldn't reach Wiktionary")
        } finally {
            connection.disconnect()
        }
    }

    /** The definitions in the API's answer for [language], without their HTML. */
    internal fun parse(json: String, language: String): List<Definition> = try {
        val sections = JSONObject(json).optJSONArray(language) ?: return emptyList()
        buildList {
            for (i in 0 until sections.length()) {
                val section = sections.getJSONObject(i)
                val partOfSpeech = section.optString("partOfSpeech").lowercase()
                val senses = section.optJSONArray("definitions") ?: continue
                var taken = 0
                for (j in 0 until senses.length()) {
                    if (taken == PER_PART_OF_SPEECH) break
                    val html = Jsoup.parse(senses.getJSONObject(j).optString("definition"))
                    val text = html.text().trim()
                    if (text.isEmpty()) continue
                    val formOf = html.selectFirst(".form-of-definition-link")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                    add(Definition(partOfSpeech, text, formOf))
                    taken++
                }
            }
        }
    } catch (e: JSONException) {
        emptyList()
    }

    /** A definition as a line of Markdown for an entry's notes. */
    fun markdown(definition: Definition) =
        if (definition.partOfSpeech.isEmpty()) definition.text else "*${definition.partOfSpeech}* — ${definition.text}"
}
