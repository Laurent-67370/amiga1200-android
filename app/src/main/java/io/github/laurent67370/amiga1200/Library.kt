package io.github.laurent67370.amiga1200

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bibliothèque : les fichiers déjà choisis (jeux WHDLoad, disquettes, disques durs), retrouvés d'un toucher.
 *  Seule l'adresse du document est gardée : le fichier reste où il est, lu sur place. */
data class LibItem(val uri: String, val name: String, val kind: String, val added: Long, val used: Long)

object Library {
    const val WHD = "whd"
    const val FLOPPY = "floppy"
    const val HDF = "hdf"
    const val CD = "cd"
    /** Disque dur qui démarre WHDLoad tout seul (jeu installé sur un petit .hdf) : rangé avec les jeux */
    const val WHDHDF = "whdhdf"
    /** Kickstart : jamais rangé dans la Bibliothèque, installé comme ROM de la machine */
    const val ROM = "rom"

    fun kindOf(name: String): String = if (name.lowercase().contains("kickstart")) ROM else when (name.substringAfterLast('.', "").lowercase()) {
        "rom" -> ROM
        "lha", "lzh", "lzx" -> WHD
        "hdf", "hdz", "vhd" -> HDF
        "iso", "chd", "cue", "ccd", "nrg", "mds" -> CD
        else -> FLOPPY
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bibliotheque", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<LibItem> {
        val a = try { JSONArray(prefs(ctx).getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            LibItem(it.getString("uri"), it.getString("name"), it.optString("kind", kindOf(it.getString("name"))), it.optLong("added"), it.optLong("used"))
        }.filter { it.kind != ROM && kindOf(it.name) != ROM }.sortedByDescending { maxOf(it.used, it.added) }
    }

    private fun save(ctx: Context, items: List<LibItem>) {
        val a = JSONArray()
        for (i in items) a.put(JSONObject().put("uri", i.uri).put("name", i.name).put("kind", i.kind).put("added", i.added).put("used", i.used))
        prefs(ctx).edit().putString("items", a.toString()).commit()
    }

    /** Ajouté (ou rafraîchi) ; le type vient de l'extension, sauf s'il est donné (contenu d'une archive analysé) */
    fun add(ctx: Context, uri: String, name: String, kind: String = kindOf(name)) {
        if (kindOf(name) == ROM) return
        val list = all(ctx).filter { it.uri != uri }.toMutableList()
        val old = all(ctx).firstOrNull { it.uri == uri }
        list += LibItem(uri, name, kind, old?.added ?: System.currentTimeMillis(), old?.used ?: 0)
        save(ctx, list)
    }

    fun touch(ctx: Context, uri: String) =
        save(ctx, all(ctx).map { if (it.uri == uri) it.copy(used = System.currentTimeMillis()) else it })

    fun remove(ctx: Context, uri: String) = save(ctx, all(ctx).filter { it.uri != uri })
}

/** Fichiers de démarrage WHDLoad d'Amiberry, copiés une fois depuis l'APK dans files/save/WHDBoot (le dossier où le
 *  cœur les cherche) ; les sauvegardes des jeux (save-data) restent d'une version à l'autre. */
object WhdBoot {
    fun ensure(ctx: Context) {
        val dest = File(Config.saveDir(ctx), "WHDBoot")
        val stamp = File(dest, ".version")
        if (stamp.exists() && stamp.readText() == BuildConfig.VERSION_NAME) return
        copy(ctx, "whdboot", dest)
        for (d in listOf("Autoboots", "Debugs", "Kickstarts", "Savegames")) File(dest, "save-data/$d").mkdirs()
        stamp.writeText(BuildConfig.VERSION_NAME)
    }

    private fun copy(ctx: Context, asset: String, dest: File) {
        val children = ctx.assets.list(asset) ?: emptyArray()
        if (children.isEmpty()) {
            // Un fichier : les sauvegardes et réglages déjà présents ne sont pas écrasés
            if (dest.exists() && asset.contains("/save-data/")) return
            dest.parentFile?.mkdirs()
            ctx.assets.open(asset).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        for (c in children) copy(ctx, "$asset/$c", File(dest, c))
    }
}

/** Bruits des lecteurs de disquettes d'Amiberry, copiés une fois depuis l'APK dans files/data/floppy_sounds : le cœur
 *  les cherche dans « data/floppy_sounds », sous son dossier courant (files) */
object FloppySounds {
    fun ensure(ctx: Context) {
        val dest = File(ctx.filesDir, "data/floppy_sounds")
        val stamp = File(dest, ".version")
        if (stamp.exists() && stamp.readText() == BuildConfig.VERSION_NAME) return
        dest.mkdirs()
        for (n in ctx.assets.list("floppy_sounds") ?: emptyArray())
            ctx.assets.open("floppy_sounds/$n").use { i -> File(dest, n).outputStream().use { o -> i.copyTo(o) } }
        stamp.writeText(BuildConfig.VERSION_NAME)
    }
}
