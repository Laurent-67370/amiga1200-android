package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Changer d'appareil : tout ce que l'application a gardé (réglages, touches de la manette, configurations, Kickstart,
 *  sauvegardes d'état, sauvegardes des jeux WHDLoad et du volume SHARED:, captures) dans un seul fichier .zip.
 *  Les jeux et disques eux-mêmes n'y sont pas : ils sont lus sur place, là où ils sont rangés ; sur l'autre appareil,
 *  ajoutez-les à la Bibliothèque et les réglages qui les citent les retrouvent par leur nom. */
object Backup {
    private const val INDEX = "sauvegarde.json"
    /** Réglages repris (les plantages et l'aide déjà vue restent propres à l'appareil) */
    private val PREFS = listOf("reglages", "touches", "bibliotheque")

    /** Dossiers gardés : nom dans l'archive → dossier de l'appareil */
    private fun roots(ctx: Context) = mapOf(
        "sys" to Config.sysDir(ctx),
        "save" to Config.saveDir(ctx),
        "captures" to File(ctx.filesDir, "captures"),
        "SHARED" to Config.sharedDir(ctx),
    )

    /** Fichiers de l'APK, recopiés d'eux-mêmes : inutiles dans la sauvegarde (WHDBoot, sauf les sauvegardes des jeux) */
    private fun skip(root: String, rel: String) =
        (root == "sys" && rel.startsWith("aros-")) ||
        (root == "save" && rel.startsWith("WHDBoot/") && !rel.startsWith("WHDBoot/save-data/"))

    class Result(val ok: Boolean, val message: String)

    fun export(ctx: Context, uri: Uri): Result = try {
        var files = 0; var bytes = 0L
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                val prefs = JSONObject()
                for (name in PREFS) prefs.put(name, prefsToJson(ctx, name))
                val index = JSONObject().put("app", "amiga1200-android").put("version", BuildConfig.VERSION_NAME)
                    .put("date", System.currentTimeMillis()).put("prefs", prefs)
                z.putNextEntry(ZipEntry(INDEX)); z.write(index.toString(1).toByteArray()); z.closeEntry()
                for ((root, dir) in roots(ctx)) {
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(dir).invariantSeparatorsPath
                        if (skip(root, rel)) return@forEach
                        z.putNextEntry(ZipEntry("$root/$rel").apply { time = f.lastModified() })
                        f.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                        files++; bytes += f.length()
                    }
                }
            }
        }
        Result(true, "Sauvegarde exportée : réglages et $files fichier${if (files > 1) "s" else ""} (${bytes / (1024 * 1024)} Mo)")
    } catch (e: Exception) { Result(false, "Export impossible : ${e.message}") }

    fun import(ctx: Context, uri: Uri): Result {
        val roots = roots(ctx)
        var index: JSONObject? = null
        var files = 0
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                ZipInputStream(ins.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        // Le sommaire vient en tête : sans lui, ce n'est pas une sauvegarde de l'application
                        if (index == null) {
                            if (e.name != INDEX) return Result(false, "Ce fichier n'est pas une sauvegarde d'Amiga 1200")
                            index = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                            continue
                        }
                        if (e.isDirectory) continue
                        val root = e.name.substringBefore('/')
                        val dir = roots[root] ?: continue
                        val dest = File(dir, e.name.substringAfter('/'))
                        // Jamais en dehors des dossiers de l'application
                        if (!dest.canonicalPath.startsWith(dir.canonicalPath + File.separator)) continue
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { z.copyTo(it) }
                        if (e.time > 0) dest.setLastModified(e.time)
                        files++
                    }
                }
            }
        } catch (e: Exception) { return Result(false, "Import impossible : ${e.message}") }
        val prefs = index?.optJSONObject("prefs") ?: return Result(false, "Ce fichier n'est pas une sauvegarde d'Amiga 1200")
        for (name in PREFS) {
            val o = prefs.optJSONObject(name) ?: continue
            if (name == "bibliotheque") mergeLibrary(ctx, o) else jsonToPrefs(ctx, name, o)
        }
        val missing = relink(ctx)
        val msg = "Sauvegarde importée : réglages et $files fichier${if (files > 1) "s" else ""}"
        return Result(true, if (missing.isEmpty()) msg else
            "$msg. Disques à ajouter à la Bibliothèque (ils seront retrouvés par leur nom) : " + missing.joinToString(", "))
    }

    // ---------- réglages ----------
    private fun prefsToJson(ctx: Context, name: String): JSONObject {
        val o = JSONObject()
        for ((k, v) in ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
            val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; is Set<*> -> "S"; else -> continue }
            o.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
        }
        return o
    }

    private fun jsonToPrefs(ctx: Context, name: String, o: JSONObject) {
        val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        for (k in o.keys()) {
            val x = o.optJSONObject(k) ?: continue
            when (x.optString("t")) {
                "b" -> e.putBoolean(k, x.optBoolean("v"))
                "i" -> e.putInt(k, x.optInt("v"))
                "l" -> e.putLong(k, x.optLong("v"))
                "f" -> e.putFloat(k, x.optDouble("v").toFloat())
                "s" -> e.putString(k, x.optString("v"))
                "S" -> x.optJSONArray("v")?.let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        e.commit()
    }

    /** Bibliothèque : celle de cet appareil est gardée ; s'y ajoutent les éléments de la sauvegarde encore accessibles
     *  (même appareil). Ceux d'un autre appareil n'y sont pas : leurs adresses n'ont pas de sens ici. */
    private fun mergeLibrary(ctx: Context, o: JSONObject) {
        val text = o.optJSONObject("items")?.optString("v") ?: return
        val held = held(ctx)
        val old = try { JSONArray(text) } catch (_: Exception) { return }
        for (i in 0 until old.length()) {
            val it = old.getJSONObject(i)
            val u = it.optString("uri")
            if (u in held && Library.all(ctx).none { x -> x.uri == u }) Library.add(ctx, u, it.optString("name"))
        }
    }

    private fun held(ctx: Context) = ctx.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()

    /** Disques des réglages et des configurations : une adresse inaccessible ici est remplacée par l'élément de même
     *  nom de la Bibliothèque ; renvoie les noms introuvables */
    private fun relink(ctx: Context): List<String> {
        val held = held(ctx)
        val lib = Library.all(ctx)
        val missing = linkedSetOf<String>()
        fun fix(uri: String, name: String): String {
            if (uri.isEmpty() || uri in held) return uri
            lib.firstOrNull { it.name.equals(name, true) }?.let { return it.uri }
            missing += name
            return uri
        }
        val s = Settings(ctx)
        for (n in 0..3) { val (u, nm) = s.floppy(n); if (u.isNotEmpty()) s.setFloppy(n, fix(u, nm), nm) }
        if (s.cd.isNotEmpty()) s.cd = fix(s.cd, s.cdName)
        s.hds = s.hds.map { Hd(fix(it.uri, it.name), it.name) }
        val configs = s.savedConfigs()
        for (k in configs.keys().asSequence().toList()) {
            val c = configs.getJSONObject(k)
            for (n in 0..3) c.optString("df$n").takeIf { it.isNotEmpty() }?.let { c.put("df$n", fix(it, c.optString("df${n}Name"))) }
            c.optString("cd").takeIf { it.isNotEmpty() }?.let { c.put("cd", fix(it, c.optString("cdName"))) }
            c.optJSONArray("hds")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { h -> h.put("uri", fix(h.optString("uri"), h.optString("name"))) } }
        }
        ctx.getSharedPreferences("reglages", Context.MODE_PRIVATE).edit().putString("configs", configs.toString()).commit()
        return missing.toList()
    }
}
