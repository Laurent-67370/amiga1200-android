package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Ajouter des fichiers sur un disque dur (.hdf) sans passer par l'Amiga, avec native/hdtools.c (comme « Fichiers… »
 *  de la version web) : jeux WHDLoad .lha et .zip décompressés, disquettes .adf ouvertes, dossiers copiés tels quels.
 *  Le disque (un document du sélecteur d'Android) est ouvert en écriture et passé au module par un lien vers son
 *  descripteur ; les fichiers à copier sont d'abord recopiés dans le cache de l'application. */
class HdFiles(private val a: Activity, private val uri: String, private val name: String, private val pickFiles: () -> Unit,
              private val pickFolder: () -> Unit) {
    class Part(val name: String, val volume: String, val fs: String, val free: Long, val ok: Boolean, val reason: String)

    private var parts: List<Part> = emptyList()
    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()

    /** Le disque ouvert en écriture, sous un lien qui mène à son descripteur (fopen détourné le suit, voir jni_bridge.c) */
    private fun <T> withDisk(f: (String?) -> T): T {
        val pfd = try { a.contentResolver.openFileDescriptor(Uri.parse(uri), "rw") } catch (e: Exception) { Native.log("$name : écriture refusée ($e)"); null }
            ?: return f(null)
        val link = File(File(a.cacheDir, "hd").apply { mkdirs() }, Disks.safe(name))
        try {
            link.delete()
            Os.symlink("/proc/self/fd/${pfd.fd}", link.path)
            return f(link.path)
        } catch (e: Exception) {
            Native.log("$name : lien impossible ($e)"); return f(null)
        } finally {
            link.delete(); pfd.close()
        }
    }

    fun show(log: String = "") {
        if (Native.state() > 0) {
            AlertDialog.Builder(a).setTitle("Ajouter sur « $name »")
                .setMessage("L'Amiga est allumé : éteignez-le d'abord, le disque ne doit pas servir pendant la copie.").setPositiveButton("OK", null).show()
            return
        }
        val listing = withDisk { p -> if (p == null) "!écriture refusée par Android (disque en lecture seule ?)" else Native.hdPartitions(p) }
        if (listing.startsWith("!")) {
            AlertDialog.Builder(a).setTitle("Ajouter sur « $name »").setMessage("Disque illisible : ${listing.drop(1)}").setPositiveButton("OK", null).show()
            return
        }
        parts = listing.lines().filter { it.isNotEmpty() }.map { l ->
            val f = l.split('\t').toMutableList().apply { while (size < 6) add("") }
            Part(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L, f[4] == "1", f[5])
        }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(8)) }
        fun text(t: String, size: Float = 14f, bold: Boolean = false, dim: Boolean = false) = TextView(a).apply {
            this.text = t; textSize = size; if (bold) setTypeface(typeface, Typeface.BOLD); if (dim) alpha = 0.7f; setPadding(0, dp(4), 0, dp(4))
        }
        val group = RadioGroup(a)
        parts.forEachIndexed { i, p ->
            group.addView(RadioButton(a).apply {
                id = 1000 + i
                this.text = "${p.name}:" + (if (p.volume.isEmpty()) "" else " « ${p.volume} »") + " — ${p.fs}" +
                    (if (p.ok) " — %.1f Mo libres".format(p.free / 1048576.0) else " : ${p.reason}")
                isEnabled = p.ok
            })
        }
        if (part !in parts.indices || !parts[part].ok) part = parts.indexOfFirst { it.ok }
        if (part >= 0) group.check(1000 + part)
        group.setOnCheckedChangeListener { _, id -> part = id - 1000 }
        col.addView(group)
        col.addView(text("Dans le dossier (vide : racine du disque)", 12f, dim = true))
        val destEdit = EditText(a).apply { setText(dest); isSingleLine = true }
        col.addView(destEdit)
        val unpackBox = CheckBox(a).apply { this.text = "Décompresser les archives .lha et .zip, ouvrir les disquettes .adf"; isChecked = unpack }
        col.addView(unpackBox)
        col.addView(text("Un jeu WHDLoad (.lha) arrive dans son propre dossier, avec son icône. Une disquette .adf est ouverte : ses fichiers vont dans un dossier à son nom. Un fichier du même nom est remplacé.", 12f, dim = true))
        val hasKick = Kickstarts.list(a).any { Config.whdKickNames.containsKey(it.crc) }
        col.addView(text("Préparer pour WHDLoad", bold = true))
        col.addView(text(if (hasKick) "Copie WHDLoad dans C: et, dans Devs:Kickstarts, ses fichiers .RTB et vos Kickstart. Ensuite, un jeu WHDLoad copié sur ce disque se lance d'un double-clic sur son icône, depuis le Workbench."
            else "Copie WHDLoad dans C: et ses fichiers .RTB dans Devs:Kickstarts. Aucun Kickstart reconnu pour WHDLoad (1.3 A500, 2.04, 3.0 ou 3.1 A1200) : ajoutez-le d'abord pour qu'il soit copié aussi.", 12f, dim = true))
        val can = part >= 0
        val buttons = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        col.addView(buttons)
        if (log.isNotEmpty()) { col.addView(text("Compte rendu", bold = true)); col.addView(text(log, 13f)) }
        val d = AlertDialog.Builder(a).setTitle("Ajouter sur « $name »").setView(ScrollView(a).apply { addView(col) })
            .setNegativeButton("Fermer", null).show()
        fun save() { dest = destEdit.text.toString().trim(); unpack = unpackBox.isChecked }
        fun button(t: String, f: () -> Unit) = buttons.addView(android.widget.Button(a).apply {
            this.text = t; isAllCaps = false; isEnabled = can; setOnClickListener { save(); d.dismiss(); f() }
        })
        button("Fichiers ou archives…") { pickFiles() }
        button("Un dossier…") { pickFolder() }
        button("Préparer le disque pour WHDLoad") { prepareWhd() }
    }

    /** Fichiers choisis (sélecteur d'Android), décompressés ou non selon la case */
    fun copyUris(uris: List<Uri>) {
        copyAll("Copie en cours") { tmp ->
            val src = uris.mapIndexedNotNull { i, u ->
                val f = File(File(tmp, "$i").apply { mkdirs() }, Disks.displayName(a, u))
                if (Disks.copy(a, u, f)) f.path else null
            }
            src to null
        }
    }

    /** Un dossier entier (sélecteur de dossier), dans un dossier à son nom */
    fun copyTree(tree: Uri) {
        val top = DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifEmpty { "Dossier" }
        copyAll("Copie de « $top »") { tmp ->
            val items = ScanFolder.walk(a, tree, ScanFolder.Stop()) {}
            val src = mutableListOf<String>(); val dst = mutableListOf<String>()
            val base = dest.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            items.forEachIndexed { i, item ->
                val f = File(tmp, "$i")
                if (Disks.copy(a, item.uri, f)) { src += f.path; dst += "$base$top/${item.path}" }
            }
            src to dst
        }
    }

    /** WHDLoad dans C:, ses .RTB et vos Kickstart (noms de WHDLoad) dans Devs:Kickstarts */
    private fun prepareWhd() {
        copyAll("Préparer pour WHDLoad") { _ ->
            WhdBoot.ensure(a)
            val wb = File(Config.saveDir(a), "WHDBoot")
            val src = mutableListOf(File(wb, "WHDLoad").path); val dst = mutableListOf("C/WHDLoad")
            File(wb, "save-data/Kickstarts").listFiles { f -> f.name.endsWith(".RTB", true) }?.sortedBy { it.name }?.forEach {
                src += it.path; dst += "Devs/Kickstarts/${it.name}"
            }
            for (r in Kickstarts.list(a)) Config.whdKickNames[r.crc]?.let { src += r.file.path; dst += "Devs/Kickstarts/$it" }
            src to dst
        }
    }

    private fun copyAll(title: String, prepare: (File) -> Pair<List<String>, List<String>?>) {
        val wait = AlertDialog.Builder(a).setTitle(title).setMessage("Patientez… Le disque est modifié seulement si toute la copie réussit.")
            .setCancelable(false).show()
        val p = part; val d = dest; val u = unpack
        Thread {
            val tmp = File(a.cacheDir, "hdsrc").apply { deleteRecursively(); mkdirs() }
            val rep = try {
                val (src, dst) = prepare(tmp)
                if (src.isEmpty()) "Rien à copier."
                else withDisk { path ->
                    if (path == null) "Copie impossible : écriture refusée par Android. Le disque n'a pas été modifié."
                    else Native.hdCopy(path, p, d, src.toTypedArray(), dst?.toTypedArray(), dst == null && u)
                }
            } catch (e: Exception) { "Copie impossible : $e" }
            tmp.deleteRecursively()
            a.runOnUiThread { if (wait.isShowing) wait.dismiss(); show(rep) }
        }.start()
    }

    companion object {
        /** Réglages gardés d'une fenêtre à l'autre */
        var dest = "Jeux"
        var unpack = true
        var part = 0
    }
}
