package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.CRC32

/** Kickstart installé : fichier dans files/sys (kick-<crc>.rom), reconnu à son contenu */
data class Rom(val file: File, val crc: Long, val version: Int, val revision: Int) {
    private val known get() = Kickstarts.known[crc]
    /** « Kickstart 3.1 (A1200) », ou la version lue dans la ROM */
    val label: String get() = known?.label ?: if (version > 0) "Kickstart ${Kickstarts.family(version)} ($version.$revision)" else "Kickstart (${file.length() / 1024} Ko)"
    /** Machines pour lesquelles cette ROM est faite, quand elle est connue */
    val models: Set<String> get() = known?.models ?: emptySet()
}

/** Plusieurs Kickstart : chacun est choisi automatiquement pour la machine qui lui convient (1.3 pour l'Amiga 500,
 *  3.1 pour l'A1200…). Ils sont rangés dans files/sys, où le cœur les retrouve aussi pour WHDLoad. */
object Kickstarts {
    class Known(val label: String, val models: Set<String>)

    /** ROM de Commodore les plus courantes (somme CRC32 du fichier) */
    val known: Map<Long, Known> = mapOf(
        0xA6CE1636L to Known("Kickstart 1.2 (A500)", setOf("A500")),
        0xC4F0F55FL to Known("Kickstart 1.3 (A500)", setOf("A500")),
        0xC3BDB240L to Known("Kickstart 2.04 (A500+)", setOf("A500")),
        0x83028FB5L to Known("Kickstart 2.05 (A600)", setOf("A600")),
        0x64466C2AL to Known("Kickstart 2.05 (A600HD)", setOf("A600")),
        0x43B0DF7BL to Known("Kickstart 2.05 (A600HD)", setOf("A600")),
        0x6C9B07D2L to Known("Kickstart 3.0 (A1200)", setOf("A1200")),
        0x9E6AC152L to Known("Kickstart 3.0 (A4000)", setOf("A4000")),
        0xFC24AE0DL to Known("Kickstart 3.1 (A500, A600, A2000)", setOf("A500", "A600")),
        0x1483A091L to Known("Kickstart 3.1 (A1200)", setOf("A1200")),
        0xD6BAE334L to Known("Kickstart 3.1 (A4000)", setOf("A4000")),
        0x2B4566F1L to Known("Kickstart 3.1 (A4000)", setOf("A4000")),
        0xEFB239CCL to Known("Kickstart 3.1 (A3000)", setOf("A4000")),
        // ROM de la CD32 : jamais prise pour un autre Amiga (elle se choisit dans la carte Kickstart → ROM CD32)
        0x1E62D4A5L to Known("ROM de la CD32", setOf("CD32")),
        0x87746BE2L to Known("Extension de la CD32", setOf("CD32")),
    )

    fun family(version: Int) = when {
        version == 34 -> "1.3"
        version == 33 -> "1.2"
        version < 33 -> "1.x"
        version <= 37 -> "2.0"
        version == 39 -> "3.0"
        version == 40 -> "3.1"
        version >= 45 -> "3.x"
        else -> "$version"
    }

    private fun dir(ctx: Context) = Config.sysDir(ctx)

    private fun info(f: File): Rom? {
        val b = try { f.readBytes() } catch (_: Exception) { return null }
        val crc = CRC32().apply { update(b) }.value
        // En-tête d'une ROM de 256 ou 512 Ko (11 11 ou 11 14) : version et révision aux octets 12 à 15
        var v = 0; var r = 0
        if (b.size >= 16 && b[0] == 0x11.toByte() && (b[1] == 0x11.toByte() || b[1] == 0x14.toByte() || b[1] == 0x16.toByte())) {
            v = ((b[12].toInt() and 0xff) shl 8) or (b[13].toInt() and 0xff)
            r = ((b[14].toInt() and 0xff) shl 8) or (b[15].toInt() and 0xff)
            if (v !in 30..60) { v = 0; r = 0 }
        }
        return Rom(f, crc, v, r)
    }

    /** Kickstart installés (l'ancien files/sys/kick.rom unique est repris) */
    fun list(ctx: Context): List<Rom> {
        val old = File(dir(ctx), "kick.rom")
        if (old.exists()) { add(ctx, old.readBytes()); old.delete() }
        return dir(ctx).listFiles { f -> f.name.startsWith("kick-") && f.name.endsWith(".rom") }
            ?.mapNotNull { info(it) }?.sortedBy { it.version } ?: emptyList()
    }

    private fun add(ctx: Context, bytes: ByteArray): Rom? {
        val crc = CRC32().apply { update(bytes) }.value
        val f = File(dir(ctx), "kick-%08x.rom".format(crc))
        if (!f.exists()) f.writeBytes(bytes)
        return info(f)
    }

    /** Ajoute un Kickstart choisi par l'utilisateur ; renvoie la ROM, ou le motif du refus */
    fun install(ctx: Context, uri: Uri): Pair<Rom?, String?> {
        val bytes = try { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } } catch (_: Exception) { return null to "fichier illisible" }
        if (bytes.size >= 11 && String(bytes, 0, 11, Charsets.US_ASCII) == "AMIROMTYPE1")
            return null to "ROM chiffrée d'Amiga Forever : copiez la ROM déchiffrée (ou utilisez l'outil d'Amiga Forever)"
        if (bytes.size !in setOf(262144, 524288, 1048576)) return null to "ce fichier n'a pas la taille d'un Kickstart (256 Ko, 512 Ko ou 1 Mo)"
        return add(ctx, bytes) to null
    }

    fun remove(rom: Rom) { rom.file.delete() }

    /** Convient-elle à cette machine ? 1.x : Amiga 500 ; 2.0 : A500 et A600 ; 3.x : selon la machine pour laquelle elle est faite */
    fun fits(r: Rom, model: String): Boolean {
        if (model in r.models) return true
        if (r.models.isNotEmpty()) return false
        val v = r.version
        return when (model) {
            "A500" -> v in 30..37 || v >= 39
            "A600" -> v == 37 || v >= 39
            "A1200", "A4000" -> v >= 39
            else -> false
        }
    }

    /** Le meilleur Kickstart pour la machine (celui qui lui est destiné, sinon le plus récent qui convient), ou null */
    fun best(ctx: Context, model: String): Rom? =
        list(ctx).filter { fits(it, model) }.maxWithOrNull(compareBy<Rom>({ if (model in it.models) 1 else 0 }, { it.version }, { it.revision }))

    /** WHDLoad a besoin d'un Kickstart 3.x (il choisit lui-même la machine) */
    fun hasForWhd(ctx: Context) = list(ctx).any { it.version >= 39 || "A1200" in it.models || "A4000" in it.models }
}
