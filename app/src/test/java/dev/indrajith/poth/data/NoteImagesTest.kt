package dev.indrajith.poth.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteImagesTest {
    @Test
    fun picturesNotesName() {
        val notes = "A map: ![](img:3f9a.png)\n![Tozbek](img:77b0.jpg) ![](https://example.com/x.png) ![](img:../escape.png)"
        assertEquals(setOf("3f9a.png", "77b0.jpg"), NoteImages.named(notes))
        assertEquals("![](img:3f9a.png)", NoteImages.markdown("3f9a.png"))
        assertEquals(setOf("3f9a.png"), NoteImages.named(NoteImages.markdown("3f9a.png")))
    }

    @Test
    fun sweepKeepsNamedAndRecentPictures() {
        val dir = Files.createTempDirectory("images").toFile()
        val now = 10L * 24 * 60 * 60 * 1000
        fun picture(name: String, age: Long) = File(dir, name).apply { writeText("x"); setLastModified(now - age) }
        val named = picture("named.png", 5 * 24 * 60 * 60 * 1000L)
        val recent = picture("recent.png", 60_000)
        val old = picture("old.png", 2 * 24 * 60 * 60 * 1000L)
        NoteImages(dir).sweep(setOf("named.png"), now)
        assertEquals(listOf(true, true, false), listOf(named, recent, old).map(File::exists))
        dir.deleteRecursively()
    }
}
