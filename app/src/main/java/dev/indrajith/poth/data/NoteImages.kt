package dev.indrajith.poth.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Pictures in entries' notes (screenshots, photos), kept as the files they came as. Notes name
 * one as ![](img:NAME), NAME being its file's name here.
 */
class NoteImages(private val dir: File) {
    // Pictures shrunk for the screen, the last few shown.
    private val shown by lazy {
        object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    }

    /** Keeps the picture in [input] (and closes it); its name, or null if it isn't a picture. Slow: call off the main thread. */
    fun add(input: InputStream): String? {
        dir.mkdirs()
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val temp = File(dir, "$id.part")
        try {
            input.use { from -> temp.outputStream().use { from.copyTo(it) } }
        } catch (e: IOException) {
            temp.delete()
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temp.path, bounds)
        val extension = when (bounds.outMimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heif", "image/heic" -> "heic"
            else -> null
        }
        if (bounds.outWidth <= 0 || extension == null) {
            temp.delete()
            return null
        }
        val name = "$id.$extension"
        return if (temp.renameTo(File(dir, name))) name else null
    }

    /** The picture [name], halved until it is no more than twice [width] pixels wide; null if there is none. */
    fun load(name: String, width: Int): Bitmap? {
        val file = file(name) ?: return null
        val key = "$name@$width"
        shown.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        shown.put(key, bitmap)
        return bitmap
    }

    private fun file(name: String): File? = File(dir, name).takeIf { name.matches(NAME) && it.isFile }

    /**
     * Deletes the pictures no note names any more (not in [named]), but none kept in the last
     * day: a new entry being written may name them before it is saved.
     */
    fun sweep(named: Set<String>, now: Long = System.currentTimeMillis()) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name !in named && now - file.lastModified() > DAY) file.delete()
        }
    }

    companion object {
        private val NAME = Regex("[A-Za-z0-9]+\\.[a-z]+")
        private val REFERENCE = Regex("!\\[[^\\]]*]\\(img:([A-Za-z0-9]+\\.[a-z]+)\\)")
        private const val DAY = 24 * 60 * 60 * 1000L

        /** The pictures [notes] name. */
        fun named(notes: String): Set<String> = REFERENCE.findAll(notes).map { it.groupValues[1] }.toSet()

        /** How notes name the picture [name]. */
        fun markdown(name: String) = "![](img:$name)"
    }
}
