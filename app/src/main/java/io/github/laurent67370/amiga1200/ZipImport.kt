package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import java.io.EOFException
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Archives .zip : un disque dur (.hdf) ou un jeu WHDLoad (.lha) zippé ne se lit pas comme une disquette. Ils sont
 *  extraits dans le dossier de l'application (Android/data/…/files/Disques) et rangés selon ce qu'ils sont ; un .hdf
 *  qui démarre WHDLoad tout seul devient un jeu (▶ Jouer). Les disquettes zippées restent lues telles quelles. */
object ZipImport {
    class Found(val file: File, val kind: String)
    class Result(val found: List<Found>, val problem: String?)

    fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Disques").apply { mkdirs() }

    /** Analyse et extraction ; [found] vide et [problem] nul : une disquette zippée, à garder telle quelle */
    fun inspect(ctx: Context, uri: Uri, zipName: String): Result {
        val found = mutableListOf<Found>()
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                ZipInputStream(ins.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory) continue
                        val name = e.name.substringAfterLast('/')
                        val kind = when (name.substringAfterLast('.', "").lowercase()) {
                            "hdf", "hdz" -> Library.HDF
                            "lha", "lzh" -> Library.WHD
                            else -> null
                        } ?: continue
                        val dest = File(dir(ctx), Disks.safe(name))
                        val part = File(dest.path + ".part")
                        // Le CRC de chaque fichier est vérifié par ZipInputStream à la fin de sa lecture
                        try { part.outputStream().use { z.copyTo(it) } }
                        catch (x: Exception) { part.delete(); throw x }
                        if (e.size >= 0 && part.length() != e.size) { part.delete(); return Result(found, incomplete(zipName)) }
                        dest.delete(); part.renameTo(dest)
                        found += Found(dest, if (kind == Library.HDF && bootsWhdload(dest)) Library.WHDHDF else kind)
                    }
                }
            }
        } catch (x: EOFException) { return Result(found, incomplete(zipName)) }
        catch (x: ZipException) { return Result(found, incomplete(zipName) + " (${x.message})") }
        catch (x: Exception) { return Result(found, "$zipName : lecture impossible (${x.message})") }
        return Result(found, null)
    }

    private fun incomplete(n: String) = "$n est incomplet ou abîmé (téléchargement interrompu ?) : retéléchargez-le."

    /** Disque qui démarre WHDLoad de lui-même : S:startup-sequence lance un .slave (« WHDLoad Jeu.Slave Preload ») */
    fun bootsWhdload(f: File): Boolean = f.length() <= 512L * 1024 * 1024 && f.inputStream().use { bootsWhdload(it) }

    /** Même chose pour un .hdf choisi directement (lu sur place) ; les gros disques (AGS…) ne sont pas lus */
    fun bootsWhdload(ctx: Context, uri: Uri): Boolean = try {
        val size = ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        size in 1..(64L * 1024 * 1024) && ctx.contentResolver.openInputStream(uri)!!.use { bootsWhdload(it) }
    } catch (_: Exception) { false }

    private fun bootsWhdload(ins: java.io.InputStream): Boolean {
        val re = Regex("WHDLoad[^\\n]{0,80}\\.slave", RegexOption.IGNORE_CASE)
        // Lu par morceaux de 1 Mo, avec un recouvrement pour une ligne coupée entre deux morceaux
        ins.buffered().let { i ->
            val buf = ByteArray(1 shl 20); var tail = ""
            while (true) {
                val n = i.read(buf); if (n <= 0) break
                val text = tail + String(buf, 0, n, Charsets.ISO_8859_1)
                if (re.containsMatchIn(text)) return true
                tail = text.takeLast(128)
            }
        }
        return false
    }
}
