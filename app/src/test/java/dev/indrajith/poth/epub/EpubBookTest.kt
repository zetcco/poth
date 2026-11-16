package dev.indrajith.poth.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup
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
            assertEquals(listOf("two"), PlainText.paragraphs(book.html(book.spine[2].item.path)))
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
            assertEquals(listOf("found"), PlainText.paragraphs(book.html(book.spine[0].item.path)))
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
    fun plainTextSplitsBlocksAndKeepsInlineTextTogether() {
        val chapter = Jsoup.parse(
            """
            <html><head><title>Not text</title></head><body>
              <h1>SIX YEARS AGO</h1>
              <div><p>Jasnah&nbsp;Kholin pretended to <i>enjoy</i>
                 the party&mdash;giving&#8217;s   no indication.</p>
                <p>   </p>
                <div>Nested <b>div</b><br/>after a break</div>
              </div>
              Loose text at the end
            </body></html>
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "SIX YEARS AGO",
                "Jasnah Kholin pretended to enjoy the party—giving’s no indication.",
                "Nested div",
                "after a break",
                "Loose text at the end",
            ),
            PlainText.paragraphs(chapter),
        )
    }
}
