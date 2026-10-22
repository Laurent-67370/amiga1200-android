package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import java.io.File

/** Configuration au format WinUAE (.uae) : export lisible par WinUAE, Amiberry et la version web (les chemins des
 *  disques deviennent leurs noms de fichier), import qui reprend la machine et retrouve les disques par leur nom
 *  dans la Bibliothèque. */
object ConfigsIO {
    fun export(ctx: Context, s: Settings, uri: Uri): Boolean = try {
        val lines = File(ctx.filesDir, "cfg/amiga.uae").takeIf { it.exists() }?.readLines() ?: emptyList()
        val out = mutableListOf("; Configuration exportée par amiga1200-android ${BuildConfig.VERSION_NAME}",
            "; Les disques sont désignés par leur nom de fichier : placez-les à côté de cette configuration.")
        if (lines.isEmpty()) out += "quickstart=${s.model},0"
        // Chemins internes de l'application → noms de fichier (les liens dh0-, df1-… reprennent leur vrai nom)
        val path = Regex("""/data/[^,]*/files/(media|sys)/([^,]*)""")
        for (l in lines) out += path.replace(l) { m -> m.groupValues[2].replace(Regex("^(dh\\d+|df\\d)-"), "") }
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write((out.joinToString("\n") + "\n").toByteArray()) }
        true
    } catch (_: Exception) { false }

    /** Import : machine (modèle, JIT, RTG, NTSC) et disques retrouvés par leur nom ; renvoie le compte rendu */
    fun import(ctx: Context, s: Settings, uri: Uri): String {
        val text = try { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.ISO_8859_1) } } catch (_: Exception) { return "Lecture impossible" }
        val kv = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith(";") && it.contains('=') }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val lib = Library.all(ctx)
        fun find(path: String): LibItem? { val n = path.trim('"').substringAfterLast('/').substringAfterLast('\\'); return lib.firstOrNull { it.name.equals(n, true) } }
        val missing = mutableListOf<String>()
        kv.firstOrNull { it.first == "quickstart" }?.second?.substringBefore(',')?.let { q ->
            val m = q.uppercase(); if (m in listOf("A500", "A600", "A1200", "A4000", "CD32")) s.model = m
        }
        kv.firstOrNull { it.first == "cachesize" }?.let { s.jit = (it.second.toIntOrNull() ?: 0) > 0 }
        s.rtg = kv.any { it.first == "gfxcard_size" && (it.second.toIntOrNull() ?: 0) > 0 }
        kv.firstOrNull { it.first == "ntsc" }?.let { s.ntsc = it.second.equals("true", true) }
        for (n in 0..3) {
            val v = kv.firstOrNull { it.first == "floppy$n" }?.second
            if (v.isNullOrEmpty()) { s.setFloppy(n, "", ""); continue }
            val it = find(v); if (it != null) s.setFloppy(n, it.uri, it.name) else { s.setFloppy(n, "", ""); missing += v.substringAfterLast('/') }
        }
        val hds = mutableListOf<Hd>()
        for ((k, v) in kv) if (k == "hardfile2" || k == "hardfile") {
            val p = v.split(',').getOrNull(1)?.substringAfter(':') ?: continue
            val it = find(p); if (it != null) hds += Hd(it.uri, it.name) else missing += p.substringAfterLast('/')
        }
        s.hds = hds
        kv.firstOrNull { it.first == "cdimage0" }?.second?.substringBefore(",image")?.let { p ->
            val it = find(p); if (it != null) { s.cd = it.uri; s.cdName = it.name } else missing += p.substringAfterLast('/')
        }
        return if (missing.isEmpty()) "Configuration importée" else
            "Configuration importée. Absents de la Bibliothèque (ajoutez-les puis importez à nouveau) : " + missing.joinToString(", ")
    }
}
