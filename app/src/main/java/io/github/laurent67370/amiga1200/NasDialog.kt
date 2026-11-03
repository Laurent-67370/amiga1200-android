package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Fenêtres « Serveur / NAS » : réglages du partage SMB, puis parcours de ses dossiers pour brancher des disques durs
 *  (lus et écrits sur place, par le réseau) et ranger jeux, disquettes et CD dans la Bibliothèque. */
class NasDialog(private val a: Activity, private val s: Settings, private val done: () -> Unit) {
    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    private fun text(t: String, size: Float = 14f, bold: Boolean = false, dim: Boolean = false) = TextView(a).apply {
        text = t; textSize = size; if (bold) setTypeface(typeface, Typeface.BOLD); if (dim) alpha = 0.7f; setPadding(0, dp(4), 0, dp(4))
    }
    private fun column() = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(8)) }
    private fun wait(title: String) = AlertDialog.Builder(a).setTitle(title).setMessage("Connexion au serveur…").setCancelable(false).show()

    /** Réglages : adresse, partage, compte */
    fun show(message: String = "") {
        val cur = Smb.load(a)
        val col = column()
        if (message.isNotEmpty()) col.addView(text(message, bold = true))
        fun field(label: String, value: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            col.addView(text(label, 12f, dim = true))
            return EditText(a).apply { setText(value); this.hint = hint; isSingleLine = true; inputType = type }.also { col.addView(it) }
        }
        val host = field("Adresse du serveur", cur?.host ?: "", "192.168.1.20 ou nas.local", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val share = field("Partage", cur?.share ?: "", "Amiga")
        val user = field("Utilisateur (vide : invité)", cur?.user ?: "", "")
        val pass = field("Mot de passe", cur?.password ?: "", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val domain = field("Domaine (facultatif)", cur?.domain ?: "", "WORKGROUP")
        col.addView(text("Le partage doit être en SMB 2 ou 3 (réglage par défaut des NAS récents). Le mot de passe reste sur ce téléphone, chiffré.", 12f, dim = true))
        col.addView(text("Les disques sont lus et écrits directement sur le serveur : gardez le Wi-Fi actif pendant la partie, et un seul appareil à la fois sur un même disque.", 12f, dim = true))
        AlertDialog.Builder(a).setTitle("Serveur / NAS").setView(ScrollView(a).apply { addView(col) })
            .setPositiveButton("Parcourir…") { _, _ ->
                val srv = SmbServer(host.text.toString().trim().removePrefix("smb://").removePrefix("\\\\").trimEnd('/'),
                    share.text.toString().trim().trim('/', '\\'), user.text.toString().trim(), pass.text.toString(), domain.text.toString().trim())
                if (srv.host.isEmpty() || srv.share.isEmpty()) { show("Indiquez l'adresse du serveur et le nom du partage."); return@setPositiveButton }
                Smb.save(a, srv)
                browse(srv, "")
            }
            .setNegativeButton("Fermer", null).show()
    }

    /** Contenu d'un dossier du partage : dossiers à ouvrir, fichiers Amiga à cocher */
    private fun browse(srv: SmbServer, dir: String) {
        val w = wait(srv.share + if (dir.isEmpty()) "" else "/$dir")
        Thread {
            val r = runCatching { Smb.share(a, srv.host, srv.share).list(dir) }
            a.runOnUiThread {
                if (w.isShowing) w.dismiss()
                r.onSuccess { list(srv, dir, it) }.onFailure { show("Connexion impossible : ${Smb.why(it)}") }
            }
        }.start()
    }

    private fun list(srv: SmbServer, dir: String, entries: List<SmbShare.Entry>) {
        val col = column()
        val picked = LinkedHashMap<String, String>()   // emplacement → nom
        lateinit var d: AlertDialog
        col.addView(text("\\\\${srv.host}\\${srv.share}" + (if (dir.isEmpty()) "" else "\\" + dir.replace('/', '\\')), 12f, dim = true))
        if (dir.isNotEmpty()) col.addView(text("⬆  Dossier parent", 16f).apply {
            setPadding(0, dp(10), 0, dp(10)); setOnClickListener { d.dismiss(); browse(srv, dir.substringBeforeLast('/', "")) }
        })
        val files = entries.filter { !it.dir && accepted(it.name) }
        for (e in entries.filter { it.dir }) col.addView(text("📁  ${e.name}", 16f).apply {
            setPadding(0, dp(10), 0, dp(10)); setOnClickListener { d.dismiss(); browse(srv, if (dir.isEmpty()) e.name else "$dir/${e.name}") }
        })
        for (e in files) {
            val uri = SmbLoc(srv.host, srv.share, if (dir.isEmpty()) e.name else "$dir/${e.name}").uri
            col.addView(CheckBox(a).apply {
                text = "${icon(e.name)}  ${e.name}  —  ${size(e.size)}"
                setOnCheckedChangeListener { _, on -> if (on) picked[uri] = e.name else picked.remove(uri) }
            })
        }
        if (entries.none { it.dir } && files.isEmpty()) col.addView(text("Aucun dossier ni fichier Amiga ici.", dim = true))
        else if (files.isNotEmpty()) col.addView(text("Cochez les disques durs .hdf à brancher (pour AGS : tous ses .hdf), et les jeux, disquettes ou CD à ranger dans la Bibliothèque.", 12f, dim = true))
        d = AlertDialog.Builder(a).setTitle("Serveur / NAS").setView(ScrollView(a).apply { addView(col) })
            .setPositiveButton("Ajouter") { _, _ -> add(picked) }
            .setNeutralButton("Réglages") { _, _ -> show() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun accepted(name: String) = name.substringAfterLast('.', "").lowercase() in setOf(
        "hdf", "hdz", "vhd", "adf", "adz", "dms", "ipf", "lha", "lzh", "lzx", "iso", "chd", "cue", "rom")
    private fun icon(name: String) = when (Library.kindOf(name)) {
        Library.HDF -> "🗄"; Library.WHD -> "🎮"; Library.CD -> "💿"; Library.ROM -> "🔑"; else -> "💾"
    }
    private fun size(n: Long) = if (n >= 1L shl 30) "%.1f Go".format(n / 1073741824.0) else if (n >= 1L shl 20) "%.1f Mo".format(n / 1048576.0) else "${n / 1024} Ko"

    /** Disques durs branchés (8 au plus, Workbench.hdf en premier), le reste rangé dans la Bibliothèque */
    private fun add(picked: Map<String, String>) {
        if (picked.isEmpty()) return
        val notes = mutableListOf<String>()
        val hds = s.hds.toMutableList()
        val roms = mutableListOf<Pair<String, String>>()
        for ((uri, name) in picked) {
            when (Library.kindOf(name)) {
                Library.ROM -> roms += uri to name
                Library.HDF -> {
                    Library.add(a, uri, name)
                    if (hds.any { it.uri == uri }) continue
                    if (hds.size >= 8) { notes += "$name : 8 disques durs au plus, rangé dans la Bibliothèque"; continue }
                    hds += Hd(uri, name)
                }
                else -> Library.add(a, uri, name)
            }
        }
        hds.sortBy { if (it.name.equals("Workbench.hdf", true)) 0 else 1 }
        s.hds = hds
        val n = picked.size - roms.size
        if (n > 0) notes.add(0, "$n fichier${if (n > 1) "s" else ""} du NAS ajouté${if (n > 1) "s" else ""} (disques durs branchés, le reste dans la Bibliothèque).")
        if (roms.isEmpty()) { finish(notes); return }
        // Kickstart : copiés dans l'application (ROM de la machine)
        val w = wait("Kickstart")
        Thread {
            for ((uri, name) in roms) {
                val loc = SmbLoc.parse(uri) ?: continue
                val r = runCatching {
                    val f = SmbBlockFile(Smb.share(a, loc.host, loc.share), loc.path, false)
                    try { if (f.size > 1048576) null else ByteArray(f.size.toInt()).also { f.read(0, it.size, it) } } finally { f.close() }
                }
                val rom = r.getOrNull()?.let { Kickstarts.installBytes(a, it) }
                notes += if (rom != null) "$name : ${rom.label} installé" else "$name : ${r.exceptionOrNull()?.let { Smb.why(it) } ?: "Kickstart non reconnu"}"
                if (rom != null) s.rom = "user"
            }
            a.runOnUiThread { if (w.isShowing) w.dismiss(); finish(notes) }
        }.start()
    }

    private fun finish(notes: List<String>) {
        done()
        if (notes.size == 1) Toast.makeText(a, notes[0], Toast.LENGTH_LONG).show()
        else AlertDialog.Builder(a).setTitle("Serveur / NAS").setMessage(notes.joinToString("\n\n")).setPositiveButton("OK", null).show()
    }
}
