package io.github.laurent67370.amiga1200

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Programme envoyé par l'application 68k Trainer (dépôt amiga1200, dossier trainer-src) : une disquette .adf
 * amorçable (OFS, sa Startup-Sequence lance le programme), compressée en gzip, et le modèle demandé.
 * Elle est rangée dans files/Trainer (un essai remplace le précédent du même programme), puis insérée dans DF0:.
 *
 * Intent : action [ACTION], extras [EXTRA_ADF_GZ] (octets gzip), [EXTRA_NOM] (nom du programme), [EXTRA_MODELE]
 * (« A500 » ou « A1200 »).
 */
object Trainer {
    const val ACTION = "io.github.laurent67370.amiga1200.TRAINER"
    const val EXTRA_ADF_GZ = "adf_gz"
    const val EXTRA_NOM = "nom"
    const val EXTRA_MODELE = "modele"

    private const val TAILLE_ADF = 901120

    class Disquette(val uri: String, val name: String, val model: String)

    fun dir(ctx: Context) = File(ctx.filesDir, "Trainer").apply { mkdirs() }

    /** La disquette reçue, enregistrée ; null si l'envoi n'en contient pas une valide (880 Ko). */
    fun receive(ctx: Context, i: Intent): Disquette? {
        val gz = i.getByteArrayExtra(EXTRA_ADF_GZ) ?: return null
        if (gz.size > TAILLE_ADF) return null
        val adf = try {
            GZIPInputStream(gz.inputStream()).use { input ->
                val out = java.io.ByteArrayOutputStream(TAILLE_ADF)
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > TAILLE_ADF) return null
                }
                out.toByteArray()
            }
        } catch (e: Exception) { return null }
        if (adf.size != TAILLE_ADF || adf[0] != 'D'.code.toByte() || adf[1] != 'O'.code.toByte() || adf[2] != 'S'.code.toByte()) return null
        val base = (i.getStringExtra(EXTRA_NOM) ?: "").replace(Regex("[^A-Za-z0-9_-]"), "").take(30).ifEmpty { "programme" }
        val file = File(dir(ctx), "$base.adf")
        file.writeBytes(adf)
        val model = if (i.getStringExtra(EXTRA_MODELE) == "A1200") "A1200" else "A500"
        return Disquette(Uri.fromFile(file).toString(), file.name, model)
    }
}
