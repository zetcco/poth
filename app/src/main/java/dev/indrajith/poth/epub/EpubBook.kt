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

/** One line of the table of contents: [path] is a zip path, [fragment] an id in it (or null). */
data class TocEntry(val title: String, val depth: Int, val path: String, val fragment: String?)

/** An open EPUB. Entries are read from the zip when asked for; close it when done. */
class EpubBook private constructor(
    private val zip: ZipFile,
    val metadata: Metadata,
    val manifest: Map<String, ManifestItem>,
    val spine: List<SpineItem>,
    private val coverId: String?, // from EPUB 2's <meta name="cover" content="…">
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

    /**
     * The cover image's path: EPUB 3's cover-image, EPUB 2's cover meta, or else the first picture
     * on the first page (a title page wrapping the cover). Null if there is none.
     */
    fun cover(): String? {
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.path }
        coverId?.let(manifest::get)?.takeIf { it.mediaType.startsWith("image/") }?.let { return it.path }
        val first = spine.firstOrNull { it.linear }?.item ?: return null
        if (first.mediaType.startsWith("image/")) return first.path
        val page = try {
            html(first.path)
        } catch (e: EpubException) {
            return null
        } catch (e: IOException) {
            return null
        }
        val image = page.select("img[src], image").firstOrNull() ?: return null
        val src = image.attr("src").ifEmpty { image.attr("xlink:href") }.ifEmpty { image.attr("href") }
        return src.takeIf { it.isNotEmpty() }?.let { resolvePath(first.path.substringBeforeLast('/', ""), it) }
    }

    /** A chapter (or any XHTML entry), parsed leniently. The charset comes from the file itself. */
    fun html(path: String): Document = Jsoup.parse(ByteArrayInputStream(read(path)), null, path)

    /**
     * The table of contents: EPUB 3's nav document if there is one, otherwise EPUB 2's NCX.
     * Empty if the book has neither, or they cannot be read.
     */
    val toc: List<TocEntry> by lazy {
        try {
            manifest.values.firstOrNull { "nav" in it.properties }?.let { navToc(it.path) }?.takeIf { it.isNotEmpty() }
                ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.let { ncxToc(it.path) }
                ?: emptyList()
        } catch (e: EpubException) {
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun navToc(path: String): List<TocEntry> {
        val doc = html(path)
        val nav = doc.select("nav").firstOrNull { it.attr("epub:type").split(' ').contains("toc") }
            ?: doc.select("nav").firstOrNull()
            ?: return emptyList()
        val base = path.substringBeforeLast('/', "")
        val entries = mutableListOf<TocEntry>()
        fun list(ol: Element, depth: Int) {
            for (li in ol.children().filter { it.normalName() == "li" }) {
                val link = li.children().firstOrNull { it.normalName() == "a" || it.normalName() == "span" }
                val href = link?.attr("href").orEmpty()
                val title = link?.text()?.trim().orEmpty()
                if (title.isNotEmpty() && href.isNotEmpty()) entries += entry(title, depth, base, href)
                li.children().firstOrNull { it.normalName() == "ol" }?.let { list(it, depth + 1) }
            }
        }
        nav.children().firstOrNull { it.normalName() == "ol" }?.let { list(it, 0) }
        return entries
    }

    private fun ncxToc(path: String): List<TocEntry> {
        val doc = Jsoup.parse(ByteArrayInputStream(read(path)), null, "", Parser.xmlParser())
        val base = path.substringBeforeLast('/', "")
        val entries = mutableListOf<TocEntry>()
        fun points(parent: Element, depth: Int) {
            for (point in parent.childrenNamed("navpoint")) {
                val title = point.childrenNamed("navlabel").firstOrNull()?.text()?.trim().orEmpty()
                val src = point.childrenNamed("content").firstOrNull()?.attr("src").orEmpty()
                if (title.isNotEmpty() && src.isNotEmpty()) entries += entry(title, depth, base, src)
                points(point, depth + 1)
            }
        }
        doc.allNamed("navmap").firstOrNull()?.let { points(it, 0) }
        return entries
    }

    private fun entry(title: String, depth: Int, base: String, href: String) =
        TocEntry(title, depth, resolvePath(base, href), href.substringAfter('#', "").ifEmpty { null })

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

                val metadataElement = opf.allNamed("metadata").firstOrNull()
                val metadata = metadataElement?.let(::metadata) ?: Metadata(file.nameWithoutExtension, emptyList(), null, null, null)
                val coverId = metadataElement?.childrenNamed("meta")?.firstOrNull { it.attr("name") == "cover" }?.attr("content")
                return EpubBook(zip, metadata, manifest, spine, coverId?.takeIf { it.isNotEmpty() })
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
