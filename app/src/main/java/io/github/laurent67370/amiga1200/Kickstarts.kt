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
    /** ROM d'extension du CDTV (jamais un Kickstart à elle seule) */
    val cdtvExt: Boolean get() = known?.cdtvExt == true
}

/** Plusieurs Kickstart : chacun est choisi automatiquement pour la machine qui lui convient (1.3 pour l'Amiga 500,
 *  3.1 pour l'A1200…). Ils sont rangés dans files/sys, où le cœur les retrouve aussi pour WHDLoad. */
object Kickstarts {
    class Known(val label: String, val models: Set<String>, val cdtvExt: Boolean = false)

    private fun k(label: String, vararg models: String) = Known(label, models.toSet())

    /** ROM de Commodore et d'Hyperion (somme CRC32 du fichier), d'après la table d'Amiberry comme la version web.
     *  « A500 » couvre aussi l'A1000, l'A2000, l'A500+ et le CDTV ; « A4000 » l'A4000T. */
    val known: Map<Long, Known> = mapOf(
        0xA6CE1636L to k("Kickstart 1.2 (A500)", "A500"),
        0xC4F0F55FL to k("Kickstart 1.3 (A500)", "A500"),
        0xE0F37258L to k("Kickstart 1.3 (A3000)", "A3000"),
        0xBC0EC13FL to k("Kickstart 1.4 (A3000)", "A3000"),
        0xC3BDB240L to k("Kickstart 2.04 (A500+)", "A500"),
        0x234A7233L to k("Kickstart 2.04 (A3000)", "A3000"),
        0x83028FB5L to k("Kickstart 2.05 (A600)", "A600"),
        0x64466C2AL to k("Kickstart 2.05 (A600HD)", "A600"),
        0x43B0DF7BL to k("Kickstart 2.05 (A600HD)", "A600"),
        0x6C9B07D2L to k("Kickstart 3.0 (A1200)", "A1200"),
        0x9E6AC152L to k("Kickstart 3.0 (A4000)", "A4000"),
        0xFC24AE0DL to k("Kickstart 3.1 (A500, A600, A2000)", "A500", "A600"),
        0x1483A091L to k("Kickstart 3.1 (A1200)", "A1200"),
        0xEFB239CCL to k("Kickstart 3.1 (A3000)", "A3000"),
        0xD6BAE334L to k("Kickstart 3.1 (A4000)", "A4000"),
        0x2B4566F1L to k("Kickstart 3.1 (A4000)", "A4000"),
        0x43B6DD22L to k("Kickstart 3.1 (A4000)", "A4000"),
        0x75932C3AL to k("Kickstart 3.1 (A4000T)", "A4000"),
        0x3AC99EDCL to k("Kickstart 3.X (A4000)", "A4000"),
        0xD52B52FDL to k("Kickstart 3.1.4 (A500, A600, A2000)", "A500", "A600"),
        0xF17FA97FL to k("Kickstart 3.1.4 (A1200)", "A1200"),
        0x50C3529CL to k("Kickstart 3.1.4 (A3000)", "A3000"),
        0xD47E18FDL to k("Kickstart 3.1.4 (A4000)", "A4000"),
        0x75A2B2A5L to k("Kickstart 3.1.4 (A4000T)", "A4000"),
        0x568F8786L to k("Kickstart 3.1.4.2 (A500, A600, A2000)", "A500", "A600"),
        0xB87506A7L to k("Kickstart 3.1.4.2 (A1200)", "A1200"),
        0xBA35F8EBL to k("Kickstart 3.1.4.2 (A3000)", "A3000"),
        0x1B84CB33L to k("Kickstart 3.1.4.2 (A4000)", "A4000"),
        0xD6D0EF3EL to k("Kickstart 3.1.4.2 (A4000T)", "A4000"),
        0x8173D7B6L to k("Kickstart 3.2 (A500, A600, A2000)", "A500", "A600"),
        0xBD1FF75EL to k("Kickstart 3.2 (A1200)", "A1200"),
        0xF3AF46CCL to k("Kickstart 3.2 (A3000)", "A3000"),
        0x9BB8FC93L to k("Kickstart 3.2 (A4000)", "A4000"),
        0x9188A509L to k("Kickstart 3.2 (A4000T)", "A4000"),
        0x4F078456L to k("Kickstart 3.2.1 (A500, A600, A2000)", "A500", "A600"),
        0x2B653371L to k("Kickstart 3.2.1 (A1200)", "A1200"),
        0x0078F607L to k("Kickstart 3.2.1 (A3000)", "A3000"),
        0xF3CED3B8L to k("Kickstart 3.2.1 (A4000)", "A4000"),
        0xAF3452ECL to k("Kickstart 3.2.1 (A4000T)", "A4000"),
        0xE4458462L to k("Kickstart 3.2.2 (A500, A600, A2000)", "A500", "A600"),
        0x5C40328AL to k("Kickstart 3.2.2 (A1200)", "A1200"),
        0x46335B57L to k("Kickstart 3.2.2 (A3000)", "A3000"),
        0x4BEA9798L to k("Kickstart 3.2.2 (A4000)", "A4000"),
        0x36BBCD8AL to k("Kickstart 3.2.2 (A4000T)", "A4000"),
        0xE1F50B0BL to k("Kickstart 3.2.3 (A500, A600, A2000)", "A500", "A600"),
        0xB18D3B67L to k("Kickstart 3.2.3 (A1200)", "A1200"),
        0x74C0B23FL to k("Kickstart 3.2.3 (A3000)", "A3000"),
        0xB6A4698EL to k("Kickstart 3.2.3 (A4000)", "A4000"),
        0x588A5E6DL to k("Kickstart 3.2.3 (A4000T)", "A4000"),
        // Extension du CDTV : s'ajoute au Kickstart 1.3 de la console
        0x42BAA124L to Known("Extension du CDTV 1.0", setOf("CDTV"), cdtvExt = true),
        0xCEAE68D2L to Known("Extension du CDTV 2.7", setOf("CDTV"), cdtvExt = true),
        0x30B54232L to Known("Extension du CDTV 2.30", setOf("CDTV"), cdtvExt = true),
        0xAB6274E7L to Known("Extension du CDTV 47.1", setOf("CDTV"), cdtvExt = true),
        // ROM de la CD32 : jamais prise pour un autre Amiga (elle se choisit dans la carte Kickstart → ROM CD32)
        0x1E62D4A5L to k("ROM de la CD32", "CD32"),
        0x87746BE2L to k("Extension de la CD32", "CD32"),
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

    /** ROM faites pour cette machine, par ordre de préférence (l'A3000 et l'A4000 se prêtent leurs ROM ; le CDTV
     *  démarre sur le Kickstart de l'Amiga 500) */
    private fun groups(model: String): List<Set<String>> = when (model) {
        "A3000" -> listOf(setOf("A3000"), setOf("A4000"))
        "A4000" -> listOf(setOf("A4000"), setOf("A3000"))
        "CDTV" -> listOf(setOf("A500"))
        else -> listOf(setOf(model))
    }

    /** 0 : ne convient pas ; plus c'est grand, mieux elle convient (ROM connue faite pour la machine en tête) */
    private fun rank(r: Rom, model: String): Int {
        if (r.cdtvExt) return 0
        if (r.models.isNotEmpty()) {
            val g = groups(model)
            g.forEachIndexed { i, set -> if (r.models.any { it in set }) return g.size - i + 1 }
            return 0
        }
        // ROM inconnue : d'après sa version. 1.x : Amiga 500 ; 2.0 : A500 et A600 ; 3.x : toutes
        val v = r.version
        val ok = when (model) {
            "A500", "CDTV" -> v in 30..37 || v >= 39
            "A600" -> v == 37 || v >= 39
            "A1200", "A3000", "A4000" -> v >= 39
            else -> false
        }
        return if (ok) 1 else 0
    }

    fun fits(r: Rom, model: String) = rank(r, model) > 0

    /** Le meilleur Kickstart pour la machine (celui qui lui est destiné, sinon le plus récent qui convient ; le 1.3
     *  d'abord pour le CDTV, celui de la vraie console), ou null */
    fun best(ctx: Context, model: String): Rom? =
        list(ctx).filter { fits(it, model) }.maxWithOrNull(compareBy<Rom>({ rank(it, model) },
            { if (model == "CDTV" && it.version == 34) 1 else 0 }, { it.version }, { it.revision }))

    /** Extension du CDTV installée, ou null */
    fun cdtvExt(ctx: Context): Rom? = list(ctx).firstOrNull { it.cdtvExt }

    /** WHDLoad a besoin d'un Kickstart 3.x (il choisit lui-même la machine) */
    fun hasForWhd(ctx: Context) = list(ctx).any {
        !it.cdtvExt && "CD32" !in it.models && (it.version >= 39 || it.models.any { m -> m in setOf("A1200", "A3000", "A4000") })
    }
}
