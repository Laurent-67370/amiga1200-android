package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** Scanner un dossier (comme la version web) : le dossier choisi est parcouru avec ses sous-dossiers, chaque fichier
 *  reconnu par son contenu (Kickstart par empreinte, ROM chiffrées d'Amiga Forever avec le rom.key du dossier,
 *  disquettes, jeux WHDLoad, disques durs, CD, configurations .uae, archives .zip). Un bilan permet de choisir ce qui
 *  rejoint la Bibliothèque ; les fichiers restent où ils sont (lus sur place, comme ceux choisis un par un) et les
 *  Kickstart sont installés comme ROM de la machine. */
object ScanFolder {
    const val KEY = "key"
    const val ZIP = "zip"
    const val UAE = "uae"
    const val MAX = 20000
    private val SKIP = setOf("__MACOSX", "\$RECYCLE.BIN", "System Volume Information", "LOST.DIR")

    class Item(val uri: Uri, val name: String, val path: String, val size: Long) {
        var kind: String? = null
        /** Ce que c'est (Kickstart reconnu, nom de la configuration…) */
        var label = ""
        /** Pourquoi il n'est pas proposé (déjà là, en double, pistes absentes…) ; vide : importable */
        var why = ""
        var on = true
        /** ROM en clair, prête à installer, et ses usages (a1200, a500, cd32main, cd32ext, cdtvext) */
        var rom: ByteArray? = null
        var tags: Set<String> = emptySet()
    }
    class Stop { @Volatile var stop = false }

    /** Tous les fichiers du dossier et de ses sous-dossiers (dossiers cachés et corbeilles ignorés) */
    fun walk(ctx: Context, tree: Uri, stop: Stop, progress: (Int) -> Unit): List<Item> {
        val out = mutableListOf<Item>()
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
        fun dir(docId: String, path: String, depth: Int) {
            if (depth > 12 || stop.stop || out.size >= MAX) return
            val sub = mutableListOf<Pair<String, String>>()
            try {
                ctx.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size < MAX && !stop.stop) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (name.startsWith(".") || name in SKIP) continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) { sub += id to name; continue }
                        out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, path + name, if (c.isNull(3)) -1L else c.getLong(3))
                        if (out.size % 50 == 0) progress(out.size)
                    }
                }
            } catch (_: Exception) {}
            for ((id, n) in sub) dir(id, "$path$n/", depth + 1)
        }
        dir(DocumentsContract.getTreeDocumentId(tree), "", 0)
        // Les fichiers les moins enfouis d'abord : l'original passe avant ses copies rangées plus loin
        return out.sortedWith(compareBy<Item>({ it.path.count { c -> c == '/' } }, { it.path }))
    }

    private fun read(ctx: Context, u: Uri): ByteArray? = try { ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() } } catch (_: Exception) { null }

    /** Archive LhA d'un jeu WHDLoad : le nom d'un .slave figure en clair dans les en-têtes */
    private fun hasSlave(ctx: Context, u: Uri): Boolean {
        try {
            ctx.contentResolver.openInputStream(u)!!.use { ins ->
                val buf = ByteArray(1 shl 20); var tail = ""
                while (true) {
                    val n = ins.read(buf); if (n <= 0) break
                    val t = tail + String(buf, 0, n, Charsets.ISO_8859_1)
                    if (t.contains(".slave", true)) return true
                    tail = t.takeLast(16)
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /** Contenu d'un .zip : ZIP (disque dur ou jeu .lha, à extraire), WHD (dossier d'un jeu WHDLoad), FLOPPY, ou null */
    private fun zipKind(ctx: Context, u: Uri): String? {
        var slave = false; var extract = false; var floppy = false
        try {
            ctx.contentResolver.openInputStream(u)!!.use { ins ->
                ZipInputStream(ins.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory) continue
                        val n = e.name.substringAfterLast('/').lowercase()
                        if (n.endsWith(".slave")) slave = true
                        when (n.substringAfterLast('.', "")) {
                            "hdf", "hdz", "lha", "lzh" -> extract = true
                            "adf", "adz", "dms", "ipf" -> floppy = true
                        }
                    }
                }
            }
        } catch (_: Exception) { return null }
        return when { extract -> ZIP; slave -> Library.WHD; floppy -> Library.FLOPPY; else -> null }
    }

    class RomInfo(val plain: ByteArray?, val label: String, val tags: Set<String>, val aros: Boolean = false)

    /** Ce qu'est une ROM (déchiffrée avec [key] si elle vient d'Amiga Forever) ; null si ce n'en est pas une */
    fun romInfo(b: ByteArray, key: ByteArray?): RomInfo? {
        var p = b
        if (b.size >= 11 && String(b, 0, 11, Charsets.US_ASCII) == "AMIROMTYPE1") {
            if (key == null || key.isEmpty()) return RomInfo(null, "ROM chiffrée d'Amiga Forever : il faut son fichier rom.key dans le dossier", emptySet())
            p = ByteArray(b.size - 11) { (b[it + 11].toInt() xor key[it % key.size].toInt()).toByte() }
        }
        if (p.size !in setOf(262144, 524288, 1048576)) return null
        // AROS : déjà intégré, il ressemble à un Kickstart 3.1.4 mais n'en est pas un
        if (String(p, 0, 256, Charsets.ISO_8859_1).contains("AROS")) return RomInfo(p, "AROS, déjà intégré à l'application", emptySet(), aros = true)
        val crc = CRC32().apply { update(p) }.value
        val k = Kickstarts.known[crc]
        if (k != null) {
            val tags = mutableSetOf<String>()
            when {
                k.cdtvExt -> tags += "cdtvext"
                "CD32" in k.models -> tags += if (crc == CD32_EXT) "cd32ext" else "cd32main"
                else -> { if ("A1200" in k.models) tags += "a1200"; if (k.label.startsWith("Kickstart 1.3")) tags += "a500" }
            }
            return RomInfo(p, k.label, tags)
        }
        var v = 0
        if (p[0] == 0x11.toByte() && (p[1] == 0x11.toByte() || p[1] == 0x14.toByte() || p[1] == 0x16.toByte())) {
            v = ((p[12].toInt() and 0xff) shl 8) or (p[13].toInt() and 0xff)
            if (v !in 30..60) v = 0
        }
        if (v == 0) return null
        // ROM de 1 Mo non répertoriée : la ROM complète d'une CD32
        if (p.size == 1048576) return RomInfo(p, "ROM CD32 complète (1 Mo)", setOf("cd32main"))
        return RomInfo(p, "Kickstart ${Kickstarts.family(v)} ($v)", if (v == 34) setOf("a500") else if (v >= 39) setOf("a1200") else emptySet())
    }
    const val CD32_EXT = 0x87746BE2L

    private fun crcOf(f: File): Long = try { CRC32().apply { update(f.readBytes()) }.value } catch (_: Exception) { -1L }

    /** Reconnaissance de chaque fichier, doublons compris (Bibliothèque, Kickstart installés, configurations) */
    fun classify(ctx: Context, s: Settings, items: List<Item>, stop: Stop, progress: (Int, String) -> Unit) {
        val norm = { k: String -> if (k == Library.WHDHDF) Library.HDF else k }
        val lib = Library.all(ctx).map { norm(it.kind) + "|" + it.name.lowercase() }.toSet()
        val roms = Kickstarts.list(ctx).map { it.crc }.toMutableSet()
        for (n in listOf("cd32.rom", "cd32ext.rom")) File(Config.sysDir(ctx), n).takeIf { it.exists() }?.let { roms += crcOf(it) }
        val configs = s.savedConfigs().keys().asSequence().map { it.lowercase() }.toSet()
        val key = items.firstOrNull { it.name.endsWith(".key", true) && it.size in 1L..65535L }?.let { read(ctx, it.uri) }
        val byDir = items.groupBy { it.path.substringBeforeLast('/', "") }
        val seen = HashSet<String>()
        val tracks = HashSet<Uri>()
        for ((i, it) in items.withIndex()) {
            if (stop.stop) break
            if (i % 10 == 0) progress(i, it.path)
            val n = it.name.lowercase()
            val ext = n.substringAfterLast('.', "")
            try {
                when {
                    ext == "key" -> if (it.size in 1L..65535L) { it.kind = KEY; it.why = "sert à lire les ROM chiffrées d'Amiga Forever du dossier" }
                    ext in setOf("adf", "adz", "dms", "ipf", "scp") || n.endsWith(".adf.gz") -> it.kind = Library.FLOPPY
                    ext == "lha" || ext == "lzh" -> if (hasSlave(ctx, it.uri)) it.kind = Library.WHD
                    ext in setOf("hdf", "hdz", "vhd") -> {
                        it.kind = Library.HDF
                        if (ZipImport.bootsWhdload(ctx, it.uri)) { it.kind = Library.WHDHDF; it.label = "jeu WHDLoad sur disque dur (▶ Jouer)" }
                    }
                    ext == "iso" || ext == "chd" -> it.kind = Library.CD
                    ext == "cue" && it.size in 1L..65535L -> {
                        val text = read(ctx, it.uri)?.toString(Charsets.ISO_8859_1) ?: ""
                        val wanted = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).findAll(text)
                            .map { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).substringAfterLast('/').substringAfterLast('\\') }.toList()
                        if (wanted.isNotEmpty()) {
                            it.kind = Library.CD
                            val pool = byDir[it.path.substringBeforeLast('/', "")] ?: emptyList()
                            val missing = wanted.filter { w -> pool.none { p -> p.name.equals(w, true) } }
                            pool.filter { p -> wanted.any { w -> p.name.equals(w, true) } }.forEach { p -> tracks += p.uri }
                            if (missing.isNotEmpty()) it.why = "pistes absentes du dossier : " + missing.joinToString(", ")
                        }
                    }
                    ext == "uae" && it.size in 1L..1048575L -> {
                        it.kind = UAE
                        val text = read(ctx, it.uri)?.toString(Charsets.ISO_8859_1) ?: ""
                        it.label = (Regex("""^\s*config_description\s*=\s*(.+?)\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(text)?.groupValues?.get(1)
                            ?: it.name.substringBeforeLast('.')).take(60)
                        if (it.label.lowercase() in configs) it.why = "déjà dans les configurations"
                    }
                    ext == "zip" -> zipKind(ctx, it.uri)?.let { k ->
                        it.kind = k
                        it.label = when (k) { ZIP -> "disque dur ou jeu à extraire"; Library.WHD -> "dossier d'un jeu WHDLoad"; else -> "disquette zippée" }
                    }
                    (ext == "rom" || ext == "bin") && it.size in 1L..(2L * 1048576 + 11) ||
                        it.size in setOf(262144L, 524288L, 1048576L, 262155L, 524299L, 1048587L) -> read(ctx, it.uri)?.let { b ->
                        val info = romInfo(b, key)
                        if (info != null) {
                            it.kind = Library.ROM; it.label = info.label; it.tags = info.tags; it.rom = info.plain
                            when {
                                info.aros -> it.why = "inutile"
                                info.plain == null -> it.why = "illisible sans rom.key"
                                else -> {
                                    val c = "rom|" + CRC32().apply { update(info.plain) }.value
                                    if (CRC32().apply { update(info.plain) }.value in roms) it.why = "déjà installé"
                                    else if (!seen.add(c)) it.why = "en double dans le dossier"
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { it.kind = null }
            val k = it.kind ?: continue
            if (k in setOf(Library.FLOPPY, Library.WHD, Library.WHDHDF, Library.HDF, Library.CD, ZIP) && it.why.isEmpty()) {
                val id = norm(if (k == ZIP) Library.FLOPPY else k) + "|" + n
                // Empreinte des disquettes et des jeux (petits fichiers) : même contenu sous un autre nom
                val c = if ((k == Library.FLOPPY || k == Library.WHD) && it.size in 1L..(16L shl 20)) read(ctx, it.uri)?.let { b -> "crc|" + CRC32().apply { update(b) }.value } ?: "" else ""
                if (id in lib || (k == ZIP && ("${Library.HDF}|$n" in lib || "${Library.WHD}|$n" in lib))) { it.why = "déjà dans la Bibliothèque"; if (c.isNotEmpty()) seen.add(c) }
                else if (!seen.add(id) || (c.isNotEmpty() && !seen.add(c))) it.why = "en double dans le dossier"
            }
            if (it.why.isNotEmpty()) it.on = false
        }
        // Les pistes d'un .cue ne comptent pas à part
        for (it in items) if (it.kind == null && it.uri in tracks) it.why = "piste"
    }

    /** Usages couverts par les Kickstart déjà installés */
    fun libTags(ctx: Context): Set<String> {
        val t = mutableSetOf<String>()
        for (r in Kickstarts.list(ctx)) when {
            r.cdtvExt -> t += "cdtvext"
            "CD32" in r.models -> {}
            else -> {
                if (Kickstarts.fits(r, "A1200")) t += "a1200"
                if (r.version == 34 || r.label.startsWith("Kickstart 1.3")) t += "a500"
            }
        }
        if (File(Config.sysDir(ctx), "cd32.rom").exists()) t += "cd32main"
        return t
    }
    fun kickLine(t: Set<String>) = listOf("Amiga 1200 (3.x)" to ("a1200" in t), "Amiga 500 (1.3)" to ("a500" in t),
        "CD32" to ("cd32main" in t), "CDTV (extension)" to ("cdtvext" in t)).joinToString(" · ") { (n, ok) -> (if (ok) "✓ " else "✗ ") + n }

    class Report { val added = LinkedHashMap<String, Int>(); val notes = mutableListOf<String>() }

    /** Ajout de ce qui est coché : Kickstart installés, le reste rangé dans la Bibliothèque (lu sur place) */
    fun importAll(ctx: Context, items: List<Item>, stop: Stop, progress: (Int, Int, String) -> Unit): Report {
        val r = Report()
        val todo = items.filter { it.kind != null && it.on && it.why.isEmpty() }
        fun count(k: String) { r.added[k] = (r.added[k] ?: 0) + 1 }
        for ((i, it) in todo.withIndex()) {
            if (stop.stop) break
            progress(i, todo.size, it.path)
            try {
                when (it.kind) {
                    Library.ROM -> {
                        val p = it.rom ?: continue
                        if ("cd32main" in it.tags || "cd32ext" in it.tags) {
                            File(Config.sysDir(ctx), if ("cd32ext" in it.tags) "cd32ext.rom" else "cd32.rom").writeBytes(p); count(Library.ROM)
                        } else if (Kickstarts.installBytes(ctx, p) != null) count(Library.ROM)
                        else r.notes += "${it.name} : pas la taille d'un Kickstart"
                    }
                    ZIP -> {
                        val res = ZipImport.inspect(ctx, it.uri, it.name)
                        for (f in res.found) Library.add(ctx, Uri.fromFile(f.file).toString(), f.file.name, f.kind)
                        res.problem?.let { p -> r.notes += p }
                        if (res.whdArchive) Library.add(ctx, it.uri.toString(), it.name, Library.WHD)
                        else if (res.found.isEmpty() && res.problem == null) Library.add(ctx, it.uri.toString(), it.name, Library.FLOPPY)
                        if (res.found.isNotEmpty() || res.whdArchive) count(ZIP)
                    }
                    UAE -> count(UAE) // enregistrée par l'écran d'accueil (réglages de l'application)
                    else -> { Library.add(ctx, it.uri.toString(), it.name, it.kind!!); count(it.kind!!) }
                }
            } catch (x: Exception) { r.notes += "${it.name} : ${x.message}" }
        }
        return r
    }

    val CATS = listOf(Library.ROM to "Kickstart et ROM", KEY to "Clé rom.key (Amiga Forever)", Library.FLOPPY to "Disquettes",
        Library.WHD to "Jeux WHDLoad", ZIP to "Archives .zip (disques durs, jeux)", Library.HDF to "Disques durs",
        Library.CD to "CD (CD32, CDTV)", UAE to "Configurations .uae")
    fun cat(kind: String?) = if (kind == Library.WHDHDF) Library.WHD else kind
    val NAMES = mapOf(Library.ROM to ("Kickstart ou ROM" to "Kickstart ou ROM"), Library.FLOPPY to ("disquette" to "disquettes"),
        Library.WHD to ("jeu WHDLoad" to "jeux WHDLoad"), Library.WHDHDF to ("jeu WHDLoad sur disque dur" to "jeux WHDLoad sur disque dur"),
        ZIP to ("archive" to "archives"), Library.HDF to ("disque dur" to "disques durs"), Library.CD to ("CD" to "CD"),
        UAE to ("configuration" to "configurations"))
}

/** Fenêtres du scan : avancée, bilan à cocher, compte rendu */
class ScanDialog(private val a: Activity, private val s: Settings, private val again: () -> Unit, private val done: () -> Unit) {
    private val stop = ScanFolder.Stop()
    private var items: List<ScanFolder.Item> = emptyList()
    private var dirName = ""
    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    private fun text(t: String, size: Float = 14f, bold: Boolean = false, dim: Boolean = false) = TextView(a).apply {
        text = t; textSize = size; if (bold) setTypeface(typeface, Typeface.BOLD); if (dim) alpha = 0.65f; setPadding(0, dp(4), 0, dp(4))
    }
    private fun progress(title: String): Pair<AlertDialog, TextView> {
        val msg = TextView(a).apply { setPadding(dp(24), dp(12), dp(24), dp(4)); textSize = 14f }
        val d = AlertDialog.Builder(a).setTitle(title).setView(msg).setCancelable(false)
            .setNegativeButton("Arrêter") { _, _ -> stop.stop = true }.show()
        return d to msg
    }

    fun start(tree: Uri) {
        dirName = DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifEmpty { "dossier" }
        stop.stop = false
        val (d, msg) = progress("Scanner « $dirName »")
        msg.text = "Recherche des fichiers…"
        Thread {
            val list = ScanFolder.walk(a, tree, stop) { n -> a.runOnUiThread { msg.text = "Recherche des fichiers… $n trouvés" } }
            ScanFolder.classify(a, s, list, stop) { i, p -> a.runOnUiThread { msg.text = "Analyse : ${i + 1} / ${list.size}\n$p" } }
            a.runOnUiThread { if (d.isShowing) d.dismiss(); items = list; summary(stop.stop) }
        }.start()
    }

    private fun summary(stopped: Boolean) {
        val found = items.filter { it.kind != null }
        val ignored = items.count { it.kind == null && it.why != "piste" }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(8)) }
        col.addView(text("${items.size} fichier${if (items.size > 1) "s" else ""}, ${found.size} reconnu${if (found.size > 1) "s" else ""}" +
            (if (ignored > 0) " ($ignored autre${if (ignored > 1) "s" else ""} ignoré${if (ignored > 1) "s" else ""})" else "") +
            if (stopped) ". Analyse arrêtée avant la fin." else ".", bold = true))
        if (found.isEmpty()) {
            col.addView(text("Aucun fichier Amiga reconnu ici. Essayez un autre dossier, ou le dossier parent."))
            AlertDialog.Builder(a).setTitle("Scanner « $dirName »").setView(col)
                .setPositiveButton("Autre dossier…") { _, _ -> again() }.setNegativeButton("Fermer", null).show()
            return
        }
        val kicks = text("")
        col.addView(kicks)
        val warn = text("Les jeux WHDLoad demandent votre Kickstart 3.1 de l'A1200 (AROS ne leur suffit pas) ; certains jeux A500 veulent aussi le 1.3.", 13f, dim = true)
        col.addView(warn)
        var dlg: AlertDialog? = null
        fun update() {
            val t = ScanFolder.libTags(a) + found.filter { it.kind == Library.ROM && it.on }.flatMap { it.tags }
            kicks.text = "Kickstart après l'ajout : " + ScanFolder.kickLine(t)
            warn.visibility = if (found.any { ScanFolder.cat(it.kind) == Library.WHD && it.on } && "a1200" !in t) View.VISIBLE else View.GONE
            val n = found.count { it.on }
            dlg?.getButton(AlertDialog.BUTTON_POSITIVE)?.let { b -> b.text = if (n > 0) "Ajouter ($n)" else "Rien à ajouter"; b.isEnabled = n > 0 }
        }
        for ((kind, title) in ScanFolder.CATS) {
            val list = found.filter { ScanFolder.cat(it.kind) == kind }
            if (list.isEmpty()) continue
            val fresh = list.count { it.why.isEmpty() }
            val other = list.size - fresh
            val head = CheckBox(a).apply {
                text = "$title : $fresh" + if (other > 0) " (+$other déjà là ou non importable${if (other > 1) "s" else ""})" else ""
                setTypeface(typeface, Typeface.BOLD); isChecked = list.any { it.on }; isEnabled = fresh > 0
            }
            col.addView(head)
            val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, dp(6)) }
            val rows = mutableListOf<Pair<CheckBox, ScanFolder.Item>>()
            for (it in list.take(300)) {
                val c = CheckBox(a).apply {
                    text = it.path + listOf(it.label, it.why).filter { x -> x.isNotEmpty() }.joinToString(" · ").let { d -> if (d.isEmpty()) "" else "\n$d" }
                    textSize = 13f; isChecked = it.on; isEnabled = it.why.isEmpty()
                    setOnCheckedChangeListener { _, v -> it.on = v; head.isChecked = list.any { x -> x.on }; update() }
                }
                rows += c to it; box.addView(c)
            }
            if (list.size > 300) box.addView(text("… et ${list.size - 300} autres", 13f, dim = true))
            val open = kind == Library.ROM || list.size <= 6
            box.visibility = if (open) View.VISIBLE else View.GONE
            if (!open) col.addView(text("▸ Voir la liste", 13f).apply {
                setOnClickListener { v -> box.visibility = View.VISIBLE; v.visibility = View.GONE }
            })
            col.addView(box)
            head.setOnClickListener {
                val on = head.isChecked
                for ((c, x) in rows) if (x.why.isEmpty()) { c.isChecked = on; x.on = on }
                for (x in list.drop(300)) if (x.why.isEmpty()) x.on = on
                update()
            }
        }
        col.addView(text("Les fichiers restent où ils sont : l'application les lit sur place. Les Kickstart sont installés comme ROM de la machine.", 12f, dim = true))
        dlg = AlertDialog.Builder(a).setTitle("Scanner « $dirName »").setView(ScrollView(a).apply { addView(col) })
            .setPositiveButton("Ajouter") { _, _ -> import() }
            .setNeutralButton("Autre dossier…") { _, _ -> again() }
            .setNegativeButton("Fermer", null).show()
        update()
    }

    private fun import() {
        stop.stop = false
        val (d, msg) = progress("Ajout à la Bibliothèque")
        Thread {
            val r = ScanFolder.importAll(a, items, stop) { i, n, p -> a.runOnUiThread { msg.text = "${i + 1} / $n\n$p" } }
            a.runOnUiThread {
                if (d.isShowing) d.dismiss()
                // Configurations .uae : enregistrées sous leur nom, sans changer la machine en cours
                for (it in items.filter { x -> x.kind == ScanFolder.UAE && x.on && x.why.isEmpty() }) {
                    val keep = s.snapshot()
                    ConfigsIO.import(a, s, it.uri)
                    s.saveConfig(it.label.ifEmpty { it.name.substringBeforeLast('.') })
                    s.restore(keep)
                }
                if ((r.added[Library.ROM] ?: 0) > 0 && s.rom == "aros" && Kickstarts.best(a, "A1200") != null) s.rom = "user"
                done()
                val parts = r.added.entries.map { (k, n) -> "$n " + (ScanFolder.NAMES[k]?.let { p -> if (n > 1) p.second else p.first } ?: k) }
                val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(8)) }
                col.addView(text(if (parts.isEmpty()) "Rien n'a été ajouté." else "Ajouté : " + parts.joinToString(", ") + ".", bold = true))
                if (stop.stop) col.addView(text("Ajout arrêté avant la fin.", dim = true))
                if (r.notes.isNotEmpty()) col.addView(text(r.notes.take(50).joinToString("\n"), 13f))
                AlertDialog.Builder(a).setTitle("Scan terminé").setView(ScrollView(a).apply { addView(col) })
                    .setPositiveButton("OK", null).setNeutralButton("Autre dossier…") { _, _ -> again() }.show()
            }
        }.start()
    }
}
