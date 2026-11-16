package dev.indrajith.poth.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reading the container, the OPF and the chapters of small hand-made EPUBs. Plain JVM. */
class EpubBookTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun epub(vararg entries: Pair<String, String>): File {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((path, text) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun container(opfPath: String) = "META-INF/container.xml" to """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    @Test
    fun readsEpub2MetadataManifestAndSpine() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>Words of Radiance</dc:title>
                <dc:creator opf:role="aut">Brandon Sanderson</dc:creator>
                <dc:creator opf:role="ill">Someone Else</dc:creator>
                <dc:language>en</dc:language>
                <meta name="calibre:series" content="The Stormlight Archive"/>
                <meta name="calibre:series_index" content="2.0"/>
              </metadata>
              <manifest>
                <item id="c2" href="Text/Chapter%202.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="Text/ch1.xhtml#start" media-type="application/xhtml+xml"/>
                <item id="notes" href="../notes.xhtml" media-type="application/xhtml+xml"/>
                <item id="cover" href="./Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
              <spine><itemref idref="c1"/><itemref idref="notes" linear="no"/><itemref idref="missing"/><itemref idref="c2"/></spine>
            </package>
        """.trimIndent()
        val file = epub(
            container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf,
            "OEBPS/Text/Chapter 2.xhtml" to "<html><body><p>two</p></body></html>",
            "OEBPS/Text/ch1.xhtml" to "<html><body><p>one</p></body></html>",
            "notes.xhtml" to "<html><body><p>notes</p></body></html>",
        )

        EpubBook.open(file).use { book ->
            assertEquals(
                Metadata("Words of Radiance", listOf("Brandon Sanderson"), "en", "The Stormlight Archive", 2.0),
                book.metadata,
            )
            // The spine item that is not in the manifest is skipped.
            assertEquals(
                listOf("OEBPS/Text/ch1.xhtml", "notes.xhtml", "OEBPS/Text/Chapter 2.xhtml"),
                book.spine.map { it.item.path },
            )
            assertEquals(listOf(true, false, true), book.spine.map { it.linear })
            assertEquals("OEBPS/Images/cover.jpg", book.manifest.getValue("cover").path)
            assertEquals(setOf("cover-image"), book.manifest.getValue("cover").properties)
            assertEquals("two", book.html(book.spine[2].item.path).body().text())
        }
    }

    @Test
    fun readsEpub3RefinementsForRolesAndSeries() {
        val opf = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>  </dc:title>
                <dc:title>Oathbringer</dc:title>
                <dc:creator id="ed">An Editor</dc:creator>
                <meta refines="#ed" property="role" scheme="marc:relators">edt</meta>
                <dc:creator id="au">Brandon Sanderson</dc:creator>
                <meta refines="#au" property="role" scheme="marc:relators">aut</meta>
                <meta property="belongs-to-collection" id="c01">The Stormlight Archive</meta>
                <meta refines="#c01" property="collection-type">series</meta>
                <meta refines="#c01" property="group-position">3</meta>
              </metadata>
              <manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="a"/></spine>
            </package>
        """.trimIndent()
        val file = epub(container("content.opf"), "content.opf" to opf, "a.xhtml" to "<p>a</p>")

        EpubBook.open(file).use { book ->
            assertEquals(
                Metadata("Oathbringer", listOf("Brandon Sanderson"), null, "The Stormlight Archive", 3.0),
                book.metadata,
            )
            assertEquals(listOf("a.xhtml"), book.spine.map { it.item.path })
        }
    }

    @Test
    fun findsEntriesWhoseCaseDiffersFromTheOpf() {
        val opf = """
            <package><metadata><title>T</title></metadata>
              <manifest><item id="a" href="text/Chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="a"/></spine></package>
        """.trimIndent()
        val file = epub(container("content.opf"), "content.opf" to opf, "Text/chapter.XHTML" to "<p>found</p>")

        EpubBook.open(file).use { book ->
            assertEquals("found", book.html(book.spine[0].item.path).body().text())
        }
    }

    @Test
    fun refusesFilesThatAreNotEpubs() {
        val notZip = tmp.newFile().apply { writeText("just text") }
        assertEquals("it is not a zip file", assertThrows(EpubException::class.java) { EpubBook.open(notZip) }.message)

        val noContainer = epub("mimetype" to "application/epub+zip")
        assertEquals(
            "it has no META-INF/container.xml",
            assertThrows(EpubException::class.java) { EpubBook.open(noContainer) }.message,
        )

        val emptySpine = epub(container("content.opf"), "content.opf" to "<package><manifest/><spine/></package>")
        assertEquals(
            "its reading order is empty",
            assertThrows(EpubException::class.java) { EpubBook.open(emptySpine) }.message,
        )
    }

    @Test
    fun resolvesHrefsAgainstTheOpfFolder() {
        assertEquals("OEBPS/Text/a b.xhtml", resolvePath("OEBPS", "Text/a%20b.xhtml"))
        assertEquals("Images/c.jpg", resolvePath("OEBPS/Text", "../../Images/c.jpg"))
        assertEquals("a+b.xhtml", resolvePath("", "a+b.xhtml"))
        assertEquals("x.xhtml", resolvePath("", "./x.xhtml#frag"))
    }

    @Test
    fun readsTheNcxTableOfContents() {
        val opf = """
            <package><metadata><title>T</title></metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="a" href="text/a.html" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="a"/></spine></package>
        """.trimIndent()
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint id="p1"><navLabel><text>Prologue: To Question</text></navLabel><content src="text/a.html#pro"/></navPoint>
              <navPoint id="p2"><navLabel><text>Part One</text></navLabel><content src="text/a.html"/>
                <navPoint id="p3"><navLabel><text>1. Santhid</text></navLabel><content src="text/b%20c.html"/></navPoint>
              </navPoint>
            </navMap></ncx>
        """.trimIndent()
        val file = epub(container("content.opf"), "content.opf" to opf, "toc.ncx" to ncx, "text/a.html" to "<p>a</p>")

        EpubBook.open(file).use { book ->
            assertEquals(
                listOf(
                    TocEntry("Prologue: To Question", 0, "text/a.html", "pro"),
                    TocEntry("Part One", 0, "text/a.html", null),
                    TocEntry("1. Santhid", 1, "text/b c.html", null),
                ),
                book.toc,
            )
        }
    }

    @Test
    fun prefersTheEpub3NavDocument() {
        val opf = """
            <package version="3.0"><metadata><title>T</title></metadata>
              <manifest>
                <item id="nav" href="nav/nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="a"/></spine></package>
        """.trimIndent()
        val nav = """
            <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
              <nav epub:type="landmarks"><ol><li><a href="../a.xhtml">Start</a></li></ol></nav>
              <nav epub:type="toc"><h1>Contents</h1><ol>
                <li><a href="../a.xhtml#c1">Chapter One</a><ol><li><a href="../a.xhtml#s1">A scene</a></li></ol></li>
                <li><span>Unlinked heading</span></li>
              </ol></nav>
            </body></html>
        """.trimIndent()
        val file = epub(container("content.opf"), "content.opf" to opf, "nav/nav.xhtml" to nav, "a.xhtml" to "<p>a</p>")

        EpubBook.open(file).use { book ->
            assertEquals(
                listOf(TocEntry("Chapter One", 0, "a.xhtml", "c1"), TocEntry("A scene", 1, "a.xhtml", "s1")),
                book.toc,
            )
        }
    }
}
