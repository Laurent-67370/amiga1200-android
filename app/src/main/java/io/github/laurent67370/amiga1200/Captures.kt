package io.github.laurent67370.amiga1200

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.TimeZone

/** Captures d'écran : l'image d'un jeu dans la Bibliothèque (files/captures), une copie dans la galerie du téléphone,
 *  et les miniatures des sauvegardes d'état. */
object Captures {
    /** Image affichée par l'Amiga, ou null s'il n'y en a pas encore */
    fun frame(): Bitmap? {
        val px = Native.frame() ?: return null
        val w = Native.videoW(); val h = Native.videoH()
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "captures").apply { mkdirs() }
    /** Image d'un élément de la Bibliothèque (jeu WHDLoad, disquette, CD), d'après son nom */
    fun of(ctx: Context, name: String) = File(dir(ctx), Disks.safe(name.substringBeforeLast('.')) + ".jpg")

    /** Image réduite (largeur 320) en JPEG : miniatures de la Bibliothèque et des sauvegardes */
    fun saveThumb(b: Bitmap, f: File): Boolean = try {
        val h = (b.height * 320f / b.width).toInt().coerceAtLeast(1)
        val t = Bitmap.createScaledBitmap(b, 320, h, true)
        f.parentFile?.mkdirs()
        f.outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (_: Exception) { false }

    /** Copie en taille réelle dans la galerie (Images/Amiga1200) */
    fun toGallery(ctx: Context, b: Bitmap, title: String): Boolean = if (android.os.Build.VERSION.SDK_INT < 29) false else try {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${Disks.safe(title)}-${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Amiga1200")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        uri != null && (ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false)
    } catch (_: Exception) { false }
}

/** Disquette vierge, formatée (DOS, système de fichiers OFS) : 880 Ko, nom de volume au choix */
object BlankDisk {
    private const val BLOCKS = 1760

    fun adf(label: String): ByteArray {
        val img = ByteArray(BLOCKS * 512)
        val bb = ByteBuffer.wrap(img)   // gros-boutiste, comme le 68000
        bb.putInt(0, 0x444F5300)        // « DOS\0 » : bloc de démarrage d'une disquette OFS (non démarrable)
        // Date AmigaDOS : jours depuis le 1er janvier 1978, minutes, cinquantièmes de seconde
        val c = Calendar.getInstance(TimeZone.getDefault())
        val epoch = Calendar.getInstance(TimeZone.getDefault()).apply { clear(); set(1978, 0, 1) }
        val days = ((c.timeInMillis - epoch.timeInMillis) / 86_400_000L).toInt()
        val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val ticks = c.get(Calendar.SECOND) * 50
        // Bloc racine (880)
        val root = 880 * 512
        fun rl(i: Int, v: Int) = bb.putInt(root + i * 4, v)
        rl(0, 2)            // T_HEADER
        rl(3, 72)           // taille de la table de hachage
        rl(78, -1)          // carte des blocs valide
        rl(79, 881)         // bloc de la carte
        rl(105, days); rl(106, mins); rl(107, ticks)          // dernière modification de la racine
        val name = label.filter { it.code in 32..126 && it != ':' && it != '/' }.take(30).ifEmpty { "Vierge" }
        img[root + 432] = name.length.toByte()
        name.forEachIndexed { i, ch -> img[root + 433 + i] = ch.code.toByte() }
        bb.putInt(root + 472, days); bb.putInt(root + 476, mins); bb.putInt(root + 480, ticks) // modification du disque
        bb.putInt(root + 484, days); bb.putInt(root + 488, mins); bb.putInt(root + 492, ticks) // création
        rl(127, 1)          // ST_ROOT
        rl(5, checksum(bb, root, 5))
        // Carte des blocs (881) : un bit par bloc à partir du bloc 2, 1 = libre ; 880 et 881 sont occupés
        val bm = 881 * 512
        for (b in 2 until BLOCKS) if (b != 880 && b != 881) {
            val i = b - 2
            val off = bm + 4 + (i / 32) * 4
            bb.putInt(off, bb.getInt(off) or (1 shl (i % 32)))
        }
        bb.putInt(bm, checksum(bb, bm, 0))
        return img
    }

    /** Somme de contrôle AmigaDOS : l'opposé de la somme des 128 mots longs du bloc (celui de la somme compté nul) */
    private fun checksum(bb: ByteBuffer, block: Int, at: Int): Int {
        var sum = 0
        for (i in 0 until 128) if (i != at) sum += bb.getInt(block + i * 4)
        return -sum
    }

    fun write(ctx: Context, uri: Uri, label: String): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(adf(label)) } != null
    } catch (_: Exception) { false }
}
