package dev.indrajith.poth.epub

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** A file that is not a readable EPUB. The message says why, in words. */
class EpubException(message: String) : Exception(message)

/** What the OPF says about the book. */
data class Metadata(
    val title: String,
    val authors: List<String>,
    val language: String?,
    val series: String?,        // calibre:series, or EPUB 3's belongs-to-collection
    val seriesIndex: Double?,
)

/** One file in the book. [path] is the full path inside the zip, already resolved and decoded. */
data class ManifestItem(val id: String, val path: String, val mediaType: String, val properties: Set<String>)

/** One entry in reading order. Non-linear ones (pop-up notes and the like) are kept but marked. */
data class SpineItem(val item: ManifestItem, val linear: Boolean)

/** An open EPUB. Entries are read from the zip when asked for; close it when done. */
class EpubBook private constructor(
    private val zip: ZipFile,
    val metadata: Metadata,
    val manifest: Map<String, ManifestItem>,
    val spine: List<SpineItem>,
) : Closeable {

    // Some books spell a file's name in a different case in the OPF than in the zip.
    private val byLowercasePath by lazy {
        zip.entries().asSequence().associateBy { it.name.lowercase() }
    }

    /** The bytes of one entry, by its full path inside the zip. */
    fun read(path: String): ByteArray {
        val entry = zip.getEntry(path) ?: byLowercasePath[path.lowercase()]
            ?: throw EpubException("The book is missing $path")
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    /** A chapter (or any XHTML entry), parsed leniently. The charset comes from the file itself. */
    fun html(path: String): Document = Jsoup.parse(ByteArrayInputStream(read(path)), null, path)

    override fun close() = zip.close()

    companion object {
        /** Throws [EpubException] if [file] is not a zip with a container and an OPF. */
        fun open(file: File): EpubBook {
            val zip = try {
                ZipFile(file)
            } catch (e: ZipException) {
                throw EpubException("it is not a zip file")
            }
            try {
                val container = xml(zip, "META-INF/container.xml")
                val opfPath = container.allNamed("rootfile").firstOrNull()?.attr("full-path")?.takeIf { it.isNotBlank() }
                    ?: throw EpubException("its container.xml names no OPF")
                val opf = xml(zip, opfPath)
                val base = opfPath.substringBeforeLast('/', "")

                val manifest = opf.allNamed("manifest").firstOrNull()?.childrenNamed("item").orEmpty()
                    .filter { it.attr("id").isNotEmpty() && it.attr("href").isNotEmpty() }
                    .associate {
                        it.attr("id") to ManifestItem(
                            id = it.attr("id"),
                            path = resolvePath(base, it.attr("href")),
                            mediaType = it.attr("media-type"),
                            properties = it.attr("properties").split(' ').filter(String::isNotEmpty).toSet(),
                        )
                    }
                val spine = opf.allNamed("spine").firstOrNull()?.childrenNamed("itemref").orEmpty()
                    .mapNotNull { ref -> manifest[ref.attr("idref")]?.let { SpineItem(it, ref.attr("linear") != "no") } }
                if (spine.isEmpty()) throw EpubException("its reading order is empty")

                val metadata = opf.allNamed("metadata").firstOrNull()?.let(::metadata)
                    ?: Metadata(file.nameWithoutExtension, emptyList(), null, null, null)
                return EpubBook(zip, metadata, manifest, spine)
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }

        private fun xml(zip: ZipFile, path: String): Document {
            val entry = zip.getEntry(path) ?: throw EpubException("it has no $path")
            return try {
                zip.getInputStream(entry).use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            } catch (e: IOException) {
                throw EpubException("its $path cannot be read")
            }
        }

        private fun metadata(element: Element): Metadata {
            val metas = element.childrenNamed("meta")
            // EPUB 3 says more about an element in a <meta refines="#its-id" property="...">.
            val refinements = metas.filter { it.attr("refines").startsWith("#") }
                .associate { (it.attr("refines").substring(1) to it.attr("property")) to it.text().trim() }
            fun refinement(of: Element, property: String) = refinements[of.id() to property]

            val creators = element.childrenNamed("creator").filter { it.text().isNotBlank() }
            fun role(creator: Element) =
                creator.attributes().firstOrNull { it.key.substringAfter(':') == "role" }?.value
                    ?: refinement(creator, "role")
            val authors = creators.filter { role(it).let { r -> r.isNullOrEmpty() || r == "aut" } }.ifEmpty { creators }

            val calibreSeries = metas.firstOrNull { it.attr("name") == "calibre:series" }?.attr("content")
            val collection = metas.firstOrNull { it.attr("property") == "belongs-to-collection" }
            val series = calibreSeries?.takeIf { it.isNotBlank() } ?: collection?.text()?.takeIf { it.isNotBlank() }
            val seriesIndex = if (calibreSeries.isNullOrBlank()) {
                collection?.let { refinement(it, "group-position") }
            } else {
                metas.firstOrNull { it.attr("name") == "calibre:series_index" }?.attr("content")
            }

            return Metadata(
                title = element.childrenNamed("title").map { it.text().trim() }.firstOrNull { it.isNotEmpty() } ?: "Untitled",
                authors = authors.map { it.text().trim() },
                language = element.childrenNamed("language").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
                series = series?.trim(),
                seriesIndex = seriesIndex?.trim()?.toDoubleOrNull(),
            )
        }
    }
}

/** An href from a file in [base] (a folder inside the zip, "" for the top) as a full zip path. */
internal fun resolvePath(base: String, href: String): String {
    val raw = href.substringBefore('#')
    val decoded = try {
        URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") // a + in a file name is a plus, not a space
    } catch (e: IllegalArgumentException) {
        raw
    }
    val parts = ArrayDeque<String>()
    for (part in (if (base.isEmpty()) decoded else "$base/$decoded").split('/')) {
        when (part) {
            "", "." -> {}
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

// The OPF and container.xml use namespace prefixes (dc:title) that vary from book to book,
// so elements are matched by their local name.
private fun Element.localName() = normalName().substringAfter(':')

private fun Element.childrenNamed(name: String): List<Element> = children().filter { it.localName() == name }

private fun Element.allNamed(name: String): List<Element> = getAllElements().filter { it.localName() == name }
