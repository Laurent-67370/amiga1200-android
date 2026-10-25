package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Écran d'accueil : un résumé de la machine, des préréglages en un geste, des cartes (Kickstart, disques, réglages)
 *  et le bouton « Allumer » toujours visible en bas. */
class MainActivity : Activity() {
    private lateinit var s: Settings
    private lateinit var summary: TextView
    private lateinit var romText: TextView
    private lateinit var romList: LinearLayout
    private lateinit var df0Text: TextView
    private lateinit var cd32Text: TextView
    private lateinit var cdtvText: TextView
    private lateinit var cdText: TextView
    private lateinit var cfgList: LinearLayout
    private lateinit var midiText: TextView
    private lateinit var midiBox: LinearLayout
    private lateinit var hdList: LinearLayout
    private lateinit var presets: LinearLayout
    private lateinit var resumeBtn: Button
    private lateinit var libList: LinearLayout
    private lateinit var libTabs: LinearLayout
    private var libKind = Library.WHD
    private var libAll = false
    private val switches = mutableListOf<Pair<Switch, () -> Boolean>>()
    private val choices = mutableListOf<Pair<TextView, () -> Boolean>>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        s = Settings(this)
        val pad = dp(14)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        // En-tête : titre, résumé de la machine, aide
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Amiga 1200"; textSize = 26f; setTypeface(typeface, Typeface.BOLD); setTextColor(ACCENT)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(chip("✨ Assistant") { wizard() })
            addView(chip("? Aide") { help() })
            addView(chip("🌐 Web") { web() })
        })
        summary = TextView(this).apply { textSize = 14f; alpha = 0.85f; setPadding(0, dp(2), 0, dp(10)) }
        col.addView(summary)

        // Préréglages
        card(col, "⚡ Préréglages") { c ->
            presets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(presets)
            c.addView(note("Un geste règle la machine ; les détails restent modifiables plus bas."))
        }

        // Bibliothèque
        card(col, "📚 Bibliothèque") { c ->
            libTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            c.addView(libTabs)
            libList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
            c.addView(libList)
            c.addView(row(button("Ajouter des fichiers…") { pick(REQ_LIB, multiple = true) }, button("Scanner un dossier…") { scanFolder() }))
            c.addView(note("Jeux WHDLoad (.lha) lancés d'un toucher, disquettes et disques durs retrouvés sans les rechercher. Appui long : raccourci sur l'écran d'accueil d'Android, ou retrait (le fichier n'est pas effacé)."))
        }

        // Kickstart
        card(col, "🔑 Kickstart") { c ->
            romText = TextView(this).also { c.addView(it) }
            romList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(romList)
            c.addView(row(button("Ajouter un Kickstart…") { pick(REQ_ROM) },
                button("AROS / mes Kickstart") { s.rom = if (s.rom == "aros") "user" else "aros"; refresh() }))
            c.addView(note("Installez-en plusieurs (1.3 pour l'Amiga 500, 3.1 pour l'A1200…) : chacun sert à la machine qui lui convient, reconnu à son contenu. « Scanner un dossier… » (Bibliothèque) les retrouve tous d'un coup, ceux d'Amiga Forever compris."))
            c.addView(label("ROM de la CD32"))
            cd32Text = TextView(this).also { c.addView(it) }
            c.addView(row(button("ROM CD32…") { pick(REQ_CD32) }, button("Extension…") { pick(REQ_CD32EXT) }))
            c.addView(label("ROM du CDTV"))
            cdtvText = TextView(this).also { c.addView(it) }
        }

        // Disques
        card(col, "💾 Disques") { c ->
            c.addView(label("Disquette DF0:"))
            df0Text = TextView(this).also { c.addView(it) }
            c.addView(row(button("Insérer…") { pick(REQ_DF0) }, button("Éjecter") { s.df0 = ""; s.df0Name = ""; profile(); refresh() }))
            c.addView(row(button("Disquette vierge…") { blankDisk() }))
            c.addView(label("CD de la CD32 ou du CDTV (.iso ou .chd)"))
            cdText = TextView(this).also { c.addView(it) }
            c.addView(row(button("Choisir un CD…") { pick(REQ_CD) }))
            c.addView(label("Disques durs (lus sur place, sans copie)"))
            hdList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(hdList)
            c.addView(row(button("Ajouter des disques…") { pick(REQ_HD, multiple = true) }))
            c.addView(note("AGS : sélectionnez d'un coup tous ses fichiers .hdf (Workbench.hdf passe en premier, c'est lui qui démarre)."))
        }

        // Machine
        card(col, "⚙️ Machine") { c ->
            c.addView(row(choice("A500", { s.model == "A500" }) { s.model = "A500" },
                choice("A600", { s.model == "A600" }) { s.model = "A600" },
                choice("A1200", { s.model == "A1200" }) { s.model = "A1200" }))
            c.addView(row(choice("A3000", { s.model == "A3000" }) { s.model = "A3000" },
                choice("A4000", { s.model == "A4000" }) { s.model = "A4000" }))
            c.addView(row(choice("CD32", { s.model == "CD32" }) { s.model = "CD32" },
                choice("CDTV", { s.model == "CDTV" }) { s.model = "CDTV" }))
            c.addView(switch("JIT 68040 : beaucoup plus rapide (A1200, A3000, A4000)", { s.jit }) { s.jit = it })
            c.addView(switch("Carte graphique RTG : haute résolution d'AGS et du Workbench", { s.rtg }) { s.rtg = it })
            c.addView(switch("Volume SHARED: (sauvegardes AGS et WHDLoad)", { s.shared }) { s.shared = it })
            c.addView(switch("NTSC 60 Hz (sinon PAL 50 Hz)", { s.ntsc }) { s.ntsc = it })
            c.addView(row(button("Mémoire et processeur…") { MemoryDialog.show(this, s) { refresh() } }))
        }

        // Commandes
        card(col, "🕹️ Commandes") { c ->
            c.addView(switch("Démarrer avec la manette tactile (sinon souris tactile)", { s.touchPad }) { s.touchPad = it })
            c.addView(switch("Clavier Amiga en AZERTY", { s.azerty }) { s.azerty = it })
            c.addView(switch("Afficher la vitesse en haut de l'écran", { s.showSpeed }) { s.showSpeed = it })
            c.addView(switch("Image étirée sur toute la largeur", { s.fill }) { s.fill = it })
            c.addView(row(button("Réglages des commandes…") { ControlsDialog.show(this, s) }))
            c.addView(row(button("Image : filtre, pixels réguliers…") { DisplayDialog.show(this, s, null) }))
        }
        // Son et MIDI
        card(col, "🔊 Son et MIDI") { c ->
            c.addView(switch("Bruit des lecteurs de disquettes", { s.floppySound }) { s.floppySound = it })
            c.addView(switch("MIDI sur le port série", { s.midi }) { s.midi = it })
            midiBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            midiText = TextView(this).also { midiBox.addView(it) }
            midiBox.addView(row(button("Sortie…") { midiChoose(true) }, button("Entrée…") { midiChoose(false) }))
            midiBox.addView(note("L'Amiga joue sur un synthétiseur USB ou Bluetooth, ou sur une application de synthé d'Android ; un clavier MIDI peut jouer sur l'Amiga (Bars & Pipes, Music-X, OctaMED…)."))
            c.addView(midiBox)
        }
        // Configurations
        card(col, "🗂 Configurations") { c ->
            cfgList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(cfgList)
            c.addView(row(button("Enregistrer…") { saveConfigDialog() }))
            c.addView(row(button("Exporter .uae…") { exportUae() }, button("Importer .uae…") { pick(REQ_UAE) }))
            c.addView(note("Une configuration garde la machine et ses disques. L'export .uae se relit dans WinUAE, Amiberry et la version web ; l'import retrouve les disques par leur nom dans la Bibliothèque."))
        }
        // Changer d'appareil
        card(col, "📦 Changer d'appareil") { c ->
            c.addView(row(button("Tout exporter…") { exportBackup() }, button("Importer…") { pick(REQ_BACKUP_IN) }))
            c.addView(note("Un seul fichier .zip avec les réglages, les touches de la manette, les configurations, les Kickstart, les sauvegardes d'état, celles des jeux (WHDLoad, SHARED:) et les captures. Les jeux et disques n'y sont pas : sur l'autre appareil, ajoutez-les à la Bibliothèque, ils sont retrouvés par leur nom. Ce fichier contient vos Kickstart : ne le partagez pas."))
        }
        col.addView(note("Version ${BuildConfig.VERSION_NAME} · même cœur que la version web laurent-67370.github.io/amiga1200"))

        // Bouton « Allumer » fixé en bas
        val power = Button(this).apply {
            text = "▶  Allumer l'Amiga"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(ACCENT) }
            setOnClickListener { power() }
        }
        // Reprendre où l'on en était : proposé après une extinction, avec la même machine et les mêmes disques
        resumeBtn = Button(this).apply {
            textSize = 17f; setTextColor(Color.WHITE); isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(CHIP) }
            setOnClickListener { resumeGame() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(BG)
            addView(ScrollView(this@MainActivity).apply { addView(col) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(resumeBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { setMargins(dp(14), dp(6), dp(14), 0) })
            addView(power, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { setMargins(dp(14), dp(6), dp(14), dp(14)) })
        }
        // Android 15 et plus dessinent sous les barres du système : l'accueil s'en écarte (heure en haut, boutons en bas)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        refresh()
        window.decorView.post { migrateZips() }
        // Rallumage demandé une seule fois : quand Android recrée l'accueil (retour d'une extinction, mémoire libérée),
        // il lui rend la même demande, qui ne doit pas rallumer l'Amiga de nouveau
        if (state == null && intent?.action == ACTION_PLAY) { playFromShortcut(intent); intent.action = null }
        else if (state == null && intent?.getBooleanExtra(EXTRA_RESTART, false) == true) {
            val uri = intent.getStringExtra(EmuActivity.EXTRA_WHD_URI)
            val name = intent.getStringExtra(EmuActivity.EXTRA_WHD_NAME)
            val hdf = intent.getStringExtra(EmuActivity.EXTRA_HDF_URI)
            val hdfName = intent.getStringExtra(EmuActivity.EXTRA_HDF_NAME)
            intent.removeExtra(EXTRA_RESTART)
            window.decorView.postDelayed({ if (hdf != null) relaunchHdf(hdf, hdfName) else power(uri, name) }, 1200)
        }
        else if (!getSharedPreferences("aide", MODE_PRIVATE).getBoolean("vue", false)) {
            getSharedPreferences("aide", MODE_PRIVATE).edit().putBoolean("vue", true).apply()
            window.decorView.post { wizard() }
        }
    }

    // ---------- état affiché ----------
    /** Kickstart utilisé pour la machine choisie (null : AROS) */
    private fun chosenRom(): Rom? = if (s.rom == "user") Kickstarts.best(this, s.model) else null
    private fun userRom() = chosenRom() != null

    private fun refresh() {
        val a500 = s.model == "A500"
        val cd32 = s.model == "CD32"
        val cdtv = s.model == "CDTV"
        val console = cd32 || cdtv
        val big = Config.bigBox(s.model)
        val parts = mutableListOf(if (console) s.model else "Amiga ${s.model.removePrefix("A")}")
        if (big) parts += if (s.jit) "68040 JIT" else when (s.accel) { "030" -> "68030"; "040" -> "68040"; else -> if (s.model == "A1200") "68020" else "68030" }
        if (!console) {
            val mem = listOfNotNull(
                when (s.chip) { 1 -> "512 Ko Chip"; 2 -> "1 Mo Chip"; 4 -> "2 Mo Chip"; else -> null },
                if (s.fast > 0 && !(s.jit && big)) "${s.fast} Mo Fast" else null)
            if (mem.isNotEmpty()) parts += mem.joinToString(" + ")
        }
        if (big && s.rtg) parts += "RTG"
        if (cd32) parts += if (cd32Rom()) "ROM CD32" else "ROM CD32 manquante"
        else if (cdtv) parts += if (cdtvReady()) Kickstarts.best(this, "CDTV")!!.label.substringBefore(" (") + " + extension" else "ROM du CDTV manquante"
        else parts += chosenRom()?.label?.substringBefore(" (") ?: "AROS"
        val n = s.hds.size
        if (!console && n > 0) parts += "$n disque${if (n > 1) "s" else ""} dur${if (n > 1) "s" else ""}"
        if (!console && s.df0Name.isNotEmpty()) parts += "DF0: ${s.df0Name}"
        if (console) parts += if (s.cdName.isNotEmpty()) "CD : ${s.cdName}" else "sans CD"
        cd32Text.text = if (cd32Rom()) "✓ ROM CD32" + (if (File(Config.sysDir(this), "cd32ext.rom").exists()) " et extension" else " (ROM complète de 1 Mo, ou ajoutez l'extension)")
            else "Aucune : la CD32 a besoin de sa ROM (kick40060.CD32 + extension, ou ROM complète de 1 Mo)."
        val ext = Kickstarts.cdtvExt(this)
        val kick13 = Kickstarts.best(this, "CDTV")
        cdtvText.text = when {
            ext != null && kick13 != null -> "✓ ${ext.label}, avec le ${kick13.label}"
            ext != null -> "✓ ${ext.label} ; il faut aussi un Kickstart de l'Amiga 500 (1.3 de préférence)."
            else -> "Le CDTV a besoin de son extension (ROM de 256 Ko) et d'un Kickstart 1.3 : ajoutez-les avec « Ajouter un Kickstart… »."
        }
        cdText.text = s.cdName.ifEmpty { "aucun" }
        summary.text = parts.joinToString(" · ")
        midiBox.visibility = if (s.midi) View.VISIBLE else View.GONE
        midiText.text = "Sortie : ${s.midiOut.ifEmpty { "premier appareil trouvé" }}\nEntrée : ${s.midiIn.ifEmpty { "aucune" }}"

        val roms = Kickstarts.list(this)
        val chosen = chosenRom()
        val name = if (s.model == "A500") "l'Amiga 500" else "l'${s.model}"
        romText.text = when {
            s.model == "CD32" -> "La CD32 utilise sa propre ROM (ci-dessous)."
            s.model == "CDTV" -> "Le CDTV utilise son Kickstart et son extension (ci-dessous)."
            chosen != null -> "Pour $name : ${chosen.label}"
            s.rom == "aros" && roms.isNotEmpty() -> "AROS (ROM libre) choisi à la place de vos Kickstart."
            roms.isNotEmpty() -> "⚠ Aucun de vos Kickstart ne convient à $name : AROS démarre à sa place. " +
                if (Config.bigBox(s.model)) "L'${s.model} demande un Kickstart 3.0 ou plus récent." else "Ajoutez un Kickstart 1.3 ou 2.0."
            else -> "AROS, ROM libre fournie. Pour les jeux, WHDLoad et AGS, ajoutez vos Kickstart : 3.1 pour l'A1200, 1.3 pour l'Amiga 500."
        }
        romList.removeAllViews()
        for (r in roms) romList.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = (if (r.file == chosen?.file) "✓ " else "• ") + r.label
                if (r.file == chosen?.file) setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(chip("✕") {
                AlertDialog.Builder(this@MainActivity).setTitle(r.label).setMessage("Retirer ce Kickstart de l'application ? Votre fichier d'origine n'est pas effacé.")
                    .setPositiveButton("Retirer") { _, _ -> Kickstarts.remove(r); refresh() }.setNegativeButton("Annuler", null).show()
            })
        })
        df0Text.text = s.df0Name.ifEmpty { "vide" }

        hdList.removeAllViews()
        val hds = s.hds
        if (hds.isEmpty()) hdList.addView(TextView(this).apply { text = "aucun"; alpha = 0.7f })
        hds.forEachIndexed { i, h ->
            hdList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply { text = "DH$i:  ${h.name}"; if (i == 0) setTypeface(typeface, Typeface.BOLD) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (i > 0) addView(chip("↑") { s.hds = hds.toMutableList().apply { add(i - 1, removeAt(i)) }; refresh() })
                addView(chip("✕") { s.hds = hds.filterIndexed { j, _ -> j != i }; refresh() })
            })
        }

        presets.removeAllViews()
        presets.addView(row(
            preset("AGS / Workbench", "A1200, JIT, RTG", s.model == "A1200" && s.jit && s.rtg) { s.model = "A1200"; s.jit = true; s.rtg = true; s.shared = true },
            preset("A1200 d'origine", "68020, jeux AGA", s.model == "A1200" && !s.jit && !s.rtg) { s.model = "A1200"; s.jit = false; s.rtg = false; stockMemory() }))
        presets.addView(row(
            preset("Amiga 500", "jeux et démos OCS", a500) { s.model = "A500"; s.jit = false; s.rtg = false; stockMemory() },
            preset("CD32", "jeux sur CD", cd32) { s.model = "CD32" }))
        val rf = Config.resumeFile(this)
        // Un jeu lancé directement se reprend quels que soient les réglages de l'accueil ; la machine de l'accueil,
        // seulement avec les mêmes disques et la même machine
        val game = s.resumeGame.split("\n").takeIf { it.size == 3 }
        val canResume = rf.exists() && (game != null || s.resumeSig == s.signature())
        resumeBtn.visibility = if (canResume) View.VISIBLE else View.GONE
        if (canResume) resumeBtn.text = "⏯  Reprendre " + (game?.let { it[2].substringBeforeLast('.') + " " } ?: "où j'en étais ") + "(" +
            java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE).format(java.util.Date(rf.lastModified())) + ")"
        refreshLibrary()
        refreshConfigs()
        for ((sw, get) in switches) sw.isChecked = get()
        for ((v, on) in choices) {
            v.background = rounded(if (on()) ACCENT else CHIP)
            v.setTextColor(if (on()) Color.WHITE else Color.rgb(220, 220, 225))
        }
    }

    // ---------- bibliothèque ----------
    private fun refreshLibrary() {
        val items = Library.all(this)
        libTabs.removeAllViews()
        for ((kind, label) in listOf(Library.WHD to "🎮 Jeux", Library.FLOPPY to "💾 Disq.", Library.HDF to "🗄 HDF", Library.CD to "💿 CD")) {
            val n = items.count { it.kind == kind || (kind == Library.WHD && it.kind == Library.WHDHDF) }
            libTabs.addView(TextView(this).apply {
                text = if (n > 0) "$label ($n)" else label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(if (kind == libKind) ACCENT else CHIP, 16)
                setTextColor(Color.WHITE)
                setOnClickListener { libKind = kind; libAll = false; refreshLibrary() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        }
        libList.removeAllViews()
        val shown = items.filter { it.kind == libKind || (libKind == Library.WHD && it.kind == Library.WHDHDF) }
        if (shown.isEmpty()) {
            libList.addView(TextView(this).apply {
                alpha = 0.7f
                text = when (libKind) {
                    Library.WHD -> "Aucun jeu. Ajoutez des archives WHDLoad (.lha) : un toucher les lance, sans disque dur à préparer."
                    Library.FLOPPY -> "Aucune disquette (ADF, DMS, IPF…)."
                    Library.CD -> "Aucun CD de CD32 ou de CDTV (.iso ou .chd : l'image doit tenir dans un seul fichier)."
                    else -> "Aucun disque dur (.hdf)."
                }
            })
            return
        }
        for (it in if (libAll) shown else shown.take(8)) {
            libList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2))
                // Capture du jeu (prise en jouant, menu → Écran → Capture, ou à l'extinction)
                Captures.of(this@MainActivity, it.name).takeIf { f -> f.exists() }?.let { f ->
                    addView(android.widget.ImageView(this@MainActivity).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path))
                    }, LinearLayout.LayoutParams(dp(64), dp(48)).apply { marginEnd = dp(8) })
                }
                addView(TextView(this@MainActivity).apply { text = it.name.substringBeforeLast('.'); textSize = 15f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                when (it.kind) {
                    Library.WHD -> addView(chip("▶ Jouer") { playWhd(it) })
                    Library.WHDHDF -> addView(chip("▶ Jouer") { playWhdHdf(it) })
                    Library.FLOPPY -> addView(chip("DF0:") { useFloppy(it) })
                    Library.CD -> {
                        // CDTV proposé dès que son extension est installée ; sinon la CD32
                        if (Kickstarts.cdtvExt(this@MainActivity) != null) addView(chip("▶ CDTV") { playCd(it, "CDTV") })
                        addView(chip("▶ CD32") { playCd(it) })
                    }
                    else -> addView(chip(if (s.hds.any { h -> h.uri == it.uri }) "✓ branché" else "+ Brancher") { useHdf(it) })
                }
                addView(chip("✕") { removeItem(it) })
                setOnLongClickListener { _ -> libItemMenu(it); true }
            })
        }
        if (!libAll && shown.size > 8) libList.addView(chip("Tout afficher (${shown.size})") { libAll = true; refreshLibrary() })
    }

    /** Jeu WHDLoad : il faut un vrai Kickstart (avec AROS, WHDLoad s'arrête) */
    /** Appui long sur un élément de la Bibliothèque : raccourci sur l'écran d'accueil d'Android, ou retrait */
    private fun libItemMenu(it: LibItem) {
        val canPin = it.kind != Library.HDF
        val items = (if (canPin) listOf("📌 Raccourci sur l'écran d'accueil") else emptyList()) + "Retirer de la bibliothèque"
        AlertDialog.Builder(this).setTitle(it.name.substringBeforeLast('.'))
            .setItems(items.toTypedArray()) { _, i ->
                if (canPin && i == 0) pinShortcut(it)
                else removeItem(it)
            }.setNegativeButton("Annuler", null).show()
    }

    /** Retrait de la Bibliothèque : le fichier de l'utilisateur reste où il est ; une copie extraite d'une archive
     *  (dossier Disques de l'application) est effacée avec */
    private fun removeItem(it: LibItem) {
        val copy = Uri.parse(it.uri).takeIf { u -> u.scheme == "file" }?.path?.let { p -> File(p) }
            ?.takeIf { f -> f.path.startsWith(ZipImport.dir(this).path) }
        AlertDialog.Builder(this).setTitle(it.name.substringBeforeLast('.'))
            .setMessage(if (copy != null) "Retirer de la bibliothèque ? Le disque extrait de l'archive est effacé (votre archive .zip, elle, reste)."
                else "Retirer de la bibliothèque ? Le fichier lui-même n'est pas effacé.")
            .setPositiveButton("Retirer") { _, _ ->
                Library.remove(this, it.uri); copy?.delete()
                if (s.df0 == it.uri) { s.df0 = ""; s.df0Name = "" }
                if (s.hds.any { h -> h.uri == it.uri }) s.hds = s.hds.filter { h -> h.uri != it.uri }
                refresh()
            }
            .setNegativeButton("Annuler", null).show()
    }

    /** Archives .zip rangées comme disquettes par les versions précédentes : analysées une fois (disque dur ou jeu
     *  WHDLoad extraits, archive abîmée signalée) */
    private fun migrateZips() {
        val p = getSharedPreferences("bibliotheque", MODE_PRIVATE)
        val seen = p.getStringSet("zipsVus", emptySet()) ?: emptySet()
        val old = Library.all(this).filter { it.kind == Library.FLOPPY && it.name.endsWith(".zip", true) && it.uri !in seen }
        if (old.isEmpty()) return
        p.edit().putStringSet("zipsVus", seen + old.map { it.uri }).apply()
        importZips(old.map { Uri.parse(it.uri) to it.name }) {}
    }

    /** Icône qui lance le jeu (WHDLoad), la disquette (machine actuelle) ou le CD (CD32) d'un toucher */
    private fun pinShortcut(it: LibItem) {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) { toast("Ce lanceur n'accepte pas les raccourcis"); return }
        val launch = Intent(this, MainActivity::class.java).setAction(ACTION_PLAY)
            .putExtra(EXTRA_PLAY_URI, it.uri).putExtra(EXTRA_PLAY_NAME, it.name).putExtra(EXTRA_PLAY_KIND, it.kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val label = it.name.substringBeforeLast('.')
        val info = android.content.pm.ShortcutInfo.Builder(this, "jeu:" + it.uri.hashCode())
            .setShortLabel(label.take(24)).setLongLabel(label)
            .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(launch).build()
        try { sm.requestPinShortcut(info, null) } catch (e: Exception) { toast("Raccourci impossible : ${e.message}") }
    }

    /** Lancement depuis un raccourci : attend la fin d'une émulation en cours (son processus ne se rallume pas) */
    private fun playFromShortcut(i: Intent) {
        val uri = i.getStringExtra(EXTRA_PLAY_URI) ?: return
        val item = Library.all(this).firstOrNull { it.uri == uri }
            ?: LibItem(uri, i.getStringExtra(EXTRA_PLAY_NAME) ?: "jeu", i.getStringExtra(EXTRA_PLAY_KIND) ?: Library.WHD, 0, 0)
        val am = getSystemService(android.app.ActivityManager::class.java)
        val t0 = System.currentTimeMillis()
        val view = window.decorView
        view.post(object : Runnable {
            override fun run() {
                val busy = am?.runningAppProcesses?.any { it.processName == "$packageName:emu" } == true
                if (busy && System.currentTimeMillis() - t0 < 4000) { view.postDelayed(this, 150); return }
                when (item.kind) {
                    Library.WHD -> playWhd(item)
                    Library.WHDHDF -> playWhdHdf(item)
                    Library.CD -> playCd(item)
                    Library.FLOPPY -> { useFloppy(item); power() }
                }
            }
        })
    }

    /** Jeu installé sur un .hdf qui démarre WHDLoad tout seul : un A1200 avec Kickstart 3.x et 8 Mo de Fast, ce seul disque */
    private fun playWhdHdf(it: LibItem) {
        if (!whdKickstart()) return
        Library.touch(this, it.uri)
        startActivity(Intent(this, EmuActivity::class.java).putExtra(EmuActivity.EXTRA_HDF_URI, it.uri).putExtra(EmuActivity.EXTRA_HDF_NAME, it.name))
    }

    /** Préréglage « d'origine » : mémoire et processeur du modèle */
    private fun stockMemory() { s.chip = 0; s.slow = -1; s.fast = 0; s.accel = "none" }

    /** « Reprendre où j'en étais » : la machine de l'accueil, ou le jeu lancé directement (WHDLoad, .hdf) */
    private fun resumeGame() {
        val i = Intent(this, EmuActivity::class.java).putExtra(EmuActivity.EXTRA_RESUME, true)
        val g = s.resumeGame.split("\n")
        if (g.size == 3) when (g[0]) {
            "whd" -> i.putExtra(EmuActivity.EXTRA_WHD_URI, g[1]).putExtra(EmuActivity.EXTRA_WHD_NAME, g[2])
            "hdf" -> i.putExtra(EmuActivity.EXTRA_HDF_URI, g[1]).putExtra(EmuActivity.EXTRA_HDF_NAME, g[2])
        }
        startActivity(i)
    }

    private fun relaunchHdf(uri: String, name: String?) =
        startActivity(Intent(this, EmuActivity::class.java).putExtra(EmuActivity.EXTRA_HDF_URI, uri).putExtra(EmuActivity.EXTRA_HDF_NAME, name))

    private fun whdKickstart(): Boolean {
        if (Kickstarts.hasForWhd(this)) return true
        AlertDialog.Builder(this).setTitle("Kickstart nécessaire")
            .setMessage("WHDLoad a besoin d'un vrai Kickstart (3.1 de l'A1200 de préférence) : AROS ne lui suffit pas. Choisissez votre fichier ROM dans la carte Kickstart.")
            .setPositiveButton("Choisir…") { _, _ -> pick(REQ_ROM) }.setNegativeButton("Annuler", null).show()
        return false
    }

    /** Archives .zip analysées dans un fil à part : disque dur ou jeu WHDLoad extraits et rangés selon ce qu'ils sont ;
     *  [floppies] reçoit celles qui sont des disquettes (gardées telles quelles) */
    private fun importZips(zips: List<Pair<Uri, String>>, floppies: (List<Pair<Uri, String>>) -> Unit) {
        val wait = AlertDialog.Builder(this).setTitle("Analyse des archives").setMessage("Patientez…").setCancelable(false).show()
        Thread {
            val results = zips.map { (u, n) -> Triple(u, n, ZipImport.inspect(this, u, n)) }
            runOnUiThread {
                wait.dismiss()
                val keepAsIs = mutableListOf<Pair<Uri, String>>()
                val notes = mutableListOf<String>()
                for ((u, n, r) in results) {
                    for (f in r.found) {
                        Library.add(this, Uri.fromFile(f.file).toString(), f.file.name, f.kind)
                        notes += f.file.name + when (f.kind) {
                            Library.WHDHDF -> " : jeu WHDLoad sur disque dur, rangé dans Jeux (▶ Jouer)"
                            Library.WHD -> " : jeu WHDLoad, rangé dans Jeux"
                            else -> " : disque dur, rangé dans HDF"
                        }
                        if (f.kind != Library.HDF) libKind = Library.WHD else libKind = Library.HDF
                    }
                    r.problem?.let { notes += it }
                    // Dossier d'un jeu WHDLoad zippé : rangé avec les jeux, lancé tel quel (le cœur y trouve le .slave)
                    if (r.whdArchive) {
                        Library.remove(this, u.toString()); Library.add(this, u.toString(), n, Library.WHD)
                        notes += "$n : jeu WHDLoad, rangé dans Jeux (▶ Jouer)"; libKind = Library.WHD
                    }
                    // Une archive déjà rangée comme disquette est remplacée par ce qu'elle contient
                    if (r.found.isNotEmpty()) Library.remove(this, u.toString())
                    if (r.found.isEmpty() && r.problem == null && !r.whdArchive) keepAsIs += u to n
                }
                refresh()
                if (notes.isNotEmpty()) AlertDialog.Builder(this).setTitle("Archives").setMessage(notes.joinToString("\n\n")).setPositiveButton("OK", null).show()
                floppies(keepAsIs)
            }
        }.start()
    }

    private fun playWhd(it: LibItem) {
        if (!Kickstarts.hasForWhd(this)) {
            AlertDialog.Builder(this).setTitle("Kickstart nécessaire")
                .setMessage("WHDLoad a besoin d'un vrai Kickstart (3.1 de l'A1200 de préférence) : AROS ne lui suffit pas. Choisissez votre fichier ROM dans la carte Kickstart.")
                .setPositiveButton("Choisir…") { _, _ -> pick(REQ_ROM) }.setNegativeButton("Annuler", null).show()
            return
        }
        Library.touch(this, it.uri)
        power(it.uri, it.name)
    }

    private fun cd32Rom() = File(Config.sysDir(this), "cd32.rom").exists()

    /** Le CDTV a son extension et un Kickstart de l'Amiga 500 */
    private fun cdtvReady() = Kickstarts.cdtvExt(this) != null && Kickstarts.best(this, "CDTV") != null

    private fun cdtvMissing() {
        AlertDialog.Builder(this).setTitle("ROM du CDTV nécessaires")
            .setMessage("Le CDTV démarre sur un Kickstart 1.3 (celui de l'Amiga 500) et sur son extension (ROM de 256 Ko, par exemple « CDTV extended ROM v2.30 ») : ajoutez-les tous deux dans la carte Kickstart. AROS ne le fait pas démarrer.")
            .setPositiveButton("Choisir…") { _, _ -> pick(REQ_ROM) }.setNegativeButton("Annuler", null).show()
    }

    /** CD de la CD32 ou du CDTV : la console démarre dessus (il lui faut ses ROM) */
    private fun playCd(it: LibItem, console: String = "CD32") {
        if (console == "CDTV") {
            if (!cdtvReady()) { cdtvMissing(); return }
            Library.touch(this, it.uri)
            s.cd = it.uri; s.cdName = it.name; s.model = "CDTV"
            refresh()
            power()
            return
        }
        if (!cd32Rom()) {
            AlertDialog.Builder(this).setTitle("ROM de la CD32 nécessaire")
                .setMessage("La CD32 démarre sur sa propre ROM : choisissez kick40060.CD32 (puis son extension), ou une ROM complète de 1 Mo, dans la carte Kickstart.")
                .setPositiveButton("Choisir…") { _, _ -> pick(REQ_CD32) }.setNegativeButton("Annuler", null).show()
            return
        }
        Library.touch(this, it.uri)
        s.cd = it.uri; s.cdName = it.name; s.model = "CD32"
        refresh()
        power()
    }

    private fun useFloppy(it: LibItem) {
        Library.touch(this, it.uri)
        s.df0 = it.uri; s.df0Name = it.name; profile(); refresh()
        toast("DF0: ${it.name}")
    }

    private fun useHdf(it: LibItem) {
        val list = s.hds.toMutableList()
        if (list.any { h -> h.uri == it.uri }) { s.hds = list.filter { h -> h.uri != it.uri }; refresh(); return }
        if (list.size >= 8) { toast("8 disques durs au plus"); return }
        Library.touch(this, it.uri)
        list += Hd(it.uri, it.name)
        list.sortBy { h -> if (h.name.equals("Workbench.hdf", true)) 0 else 1 }
        s.hds = list; profile(); refresh()
    }

    // ---------- configurations ----------
    private fun refreshConfigs() {
        cfgList.removeAllViews()
        val all = s.savedConfigs()
        if (all.length() == 0) { cfgList.addView(TextView(this).apply { text = "aucune"; alpha = 0.7f }); return }
        for (name in all.keys().asSequence().sorted()) {
            cfgList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply { text = name; textSize = 15f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(chip("Charger") { s.restore(all.getJSONObject(name)); refresh(); toast("Configuration « $name »") })
                addView(chip("✕") {
                    AlertDialog.Builder(this@MainActivity).setTitle("Supprimer « $name » ?")
                        .setPositiveButton("Supprimer") { _, _ -> s.deleteConfig(name); refresh() }.setNegativeButton("Annuler", null).show()
                })
            })
        }
    }

    private fun saveConfigDialog() {
        val input = android.widget.EditText(this).apply { hint = "Nom (ex. AGS, Jeux A500…)"; setText(s.bootKey().substringBeforeLast('.')) }
        AlertDialog.Builder(this).setTitle("Enregistrer la configuration").setView(input)
            .setPositiveButton("Enregistrer") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { s.saveConfig(n); refresh(); toast("Configuration « $n » enregistrée") }
            }.setNegativeButton("Annuler", null).show()
    }

    /** Kickstart ajouté aux autres (reconnu à son contenu), choisi ensuite selon la machine */
    private fun installRom(uri: Uri, name: String) {
        val (rom, why) = Kickstarts.install(this, uri)
        if (rom == null) { toast("$name : $why"); return }
        // ROM de la CD32 reconnue : rangée à sa place (carte Kickstart → ROM CD32)
        if ("CD32" in rom.models) {
            val f = File(Config.sysDir(this), if (rom.crc == 0x87746BE2L) "cd32ext.rom" else "cd32.rom")
            rom.file.copyTo(f, overwrite = true); Kickstarts.remove(rom)
            toast(if (rom.crc == 0x87746BE2L) "Extension de la CD32 installée" else "ROM de la CD32 installée")
            return
        }
        s.rom = "user"
        if (rom.cdtvExt) { toast("${rom.label} ajoutée : le CDTV démarre avec elle et un Kickstart 1.3"); return }
        val forModels = listOf("A500", "A600", "A1200", "A3000", "A4000").filter { Kickstarts.fits(rom, it) }
        toast("${rom.label} ajouté" + if (forModels.isEmpty()) "" else " : servira pour " + forModels.joinToString(", ") { if (it == "A500") "l'Amiga 500" else "l'$it" })
    }

    /** Disquette vierge formatée, créée où l'on veut, mise dans DF0: et dans la Bibliothèque */
    private fun blankDisk() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "Vierge.adf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, REQ_BLANK)
    }

    private fun exportBackup() {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.FRANCE).format(java.util.Date())
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "amiga1200-sauvegarde-$day.zip")
        }, REQ_BACKUP_OUT)
    }

    /** Export ou import dans un fil à part (les sauvegardes d'état pèsent plusieurs dizaines de Mo) */
    private fun backupTask(title: String, job: () -> Backup.Result) {
        val wait = AlertDialog.Builder(this).setTitle(title).setMessage("Patientez…").setCancelable(false).show()
        Thread {
            val r = job()
            runOnUiThread {
                wait.dismiss()
                if (isFinishing) return@runOnUiThread
                refresh()
                AlertDialog.Builder(this).setTitle(if (r.ok) title else "Échec").setMessage(r.message).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun exportUae() {
        if (!File(filesDir, "cfg/amiga.uae").exists()) { toast("Allumez l'Amiga une fois avec cette configuration, puis exportez-la"); return }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, (s.bootKey().substringBeforeLast('.').ifEmpty { "amiga" }) + ".uae")
        }, REQ_UAE_OUT)
    }

    // ---------- assistant de premier lancement ----------
    /** Suite de l'assistant, appelée au retour du choix de fichiers */
    private var wizAfter: (() -> Unit)? = null
    private var wizGoal = ""

    /** Quatre étapes : ce que l'on veut faire, les Kickstart, les fichiers, puis le geste qui lance */
    private fun wizard() {
        val goals = listOf(
            "whd" to "🎮 Des jeux WHDLoad (.lha), lancés d'un toucher",
            "floppy" to "💾 Des disquettes de jeux ou de démos (.adf, .dms, .ipf)",
            "ags" to "🗄 AGS ou un Workbench installé (disques .hdf)",
            "cd" to "💿 Des jeux sur CD (CD32 ou CDTV)",
            "aros" to "🔍 Découvrir l'Amiga sans rien installer (AROS)",
        )
        AlertDialog.Builder(this).setTitle("Bienvenue ! Que voulez-vous faire ?")
            .setItems(goals.map { it.second }.toTypedArray()) { _, i -> wizGoal = goals[i].first; wizardRoms() }
            .setNegativeButton("Plus tard", null).show()
    }

    /** Étape 2 : les ROM qu'il faut pour ce choix, ou AROS */
    private fun wizardRoms() {
        if (wizGoal == "aros") { s.rom = "aros"; s.model = "A1200"; s.jit = true; s.rtg = true; refresh(); wizardDone(); return }
        val have = when (wizGoal) {
            "cd" -> cd32Rom() || cdtvReady()
            "floppy" -> Kickstarts.list(this).any { !it.cdtvExt && "CD32" !in it.models }
            else -> Kickstarts.hasForWhd(this)
        }
        val need = when (wizGoal) {
            "whd" -> "WHDLoad a besoin de votre Kickstart 3.1 de l'A1200 (ou 3.0, 3.1.4, 3.2) : avec la ROM libre AROS, il ne démarre pas."
            "ags" -> "AGS et le Workbench ont besoin de votre Kickstart 3.1 de l'A1200 (ou plus récent)."
            "floppy" -> "Les jeux sur disquette veulent le Kickstart de leur machine : 1.3 pour l'Amiga 500 (la plupart), 3.1 pour les jeux A1200. Choisissez-en plusieurs : chacun servira à la machine qui lui convient."
            else -> "La CD32 démarre sur sa ROM (kick40060.CD32 et son extension, ou la ROM complète de 1 Mo) ; le CDTV sur un Kickstart 1.3 et son extension."
        }
        AlertDialog.Builder(this).setTitle(if (have) "Kickstart : ✓ déjà installés" else "Vos Kickstart")
            .setMessage((if (have) "Vous pouvez en ajouter d'autres.\n\n" else "") + need +
                "\n\nLes Kickstart sont les ROM de l'Amiga : ils ne sont pas fournis. On les trouve dans Amiga Forever (Cloanto) ou on les copie de son propre Amiga.")
            .setPositiveButton(if (have) "Continuer" else "Choisir mes Kickstart…") { _, _ ->
                if (have) wizardFiles() else { wizAfter = { wizardFiles() }; pick(REQ_ROM, multiple = true) }
            }
            .setNeutralButton(if (have) "En ajouter…" else "Je n'en ai pas") { _, _ ->
                if (have) { wizAfter = { wizardFiles() }; pick(REQ_ROM, multiple = true) } else wizardFiles()
            }
            .setNegativeButton("Annuler", null).show()
    }

    /** Étape 3 : la machine qui convient, puis les fichiers à ranger dans la Bibliothèque */
    private fun wizardFiles() {
        if (Kickstarts.list(this).isNotEmpty()) s.rom = "user"
        when (wizGoal) {
            "whd" -> { s.model = "A1200"; s.jit = true; s.rtg = true; libKind = Library.WHD }
            "ags" -> { s.model = "A1200"; s.jit = true; s.rtg = true; s.shared = true }
            "floppy" -> { s.model = if (Kickstarts.best(this, "A500") != null || Kickstarts.list(this).isEmpty()) "A500" else "A1200"; s.jit = false; s.rtg = false; libKind = Library.FLOPPY }
            "cd" -> { s.model = if (!cd32Rom() && cdtvReady()) "CDTV" else "CD32"; libKind = Library.CD }
        }
        refresh()
        val what = when (wizGoal) {
            "whd" -> "Choisissez vos jeux WHDLoad (.lha), plusieurs d'un coup si vous voulez. Ils restent où ils sont : l'application les lit sur place."
            "ags" -> "Choisissez d'un coup tous les fichiers .hdf d'AGS (ou votre disque Workbench). Ils sont lus sur place, même s'ils font plusieurs Go."
            "floppy" -> "Choisissez vos disquettes, plusieurs d'un coup si vous voulez. La première ira dans le lecteur DF0:."
            else -> "Choisissez vos CD (.iso ou .chd, en un seul fichier)."
        }
        AlertDialog.Builder(this).setTitle("Vos fichiers").setMessage(what)
            .setPositiveButton("Choisir…") { _, _ ->
                wizAfter = { wizardDone() }
                pick(if (wizGoal == "ags") REQ_HD else REQ_LIB, multiple = true)
            }
            .setNeutralButton("Plus tard") { _, _ -> wizardDone() }.show()
    }

    /** Étape 4 : ce qu'il reste à toucher */
    private fun wizardDone() {
        val items = Library.all(this)
        if (wizGoal == "floppy" && s.df0Name.isEmpty()) items.firstOrNull { it.kind == Library.FLOPPY }?.let { s.df0 = it.uri; s.df0Name = it.name }
        if (wizGoal == "cd" && s.cdName.isEmpty()) items.firstOrNull { it.kind == Library.CD }?.let { s.cd = it.uri; s.cdName = it.name }
        refresh()
        val (msg, canPower) = when (wizGoal) {
            "whd" -> (if (items.any { it.kind == Library.WHD }) "Touchez « ▶ Jouer » à côté d'un jeu, dans la Bibliothèque : il démarre directement." else "Ajoutez vos jeux .lha avec « Ajouter des fichiers… » (Bibliothèque), puis touchez « ▶ Jouer ».") to false
            "floppy" -> (if (s.df0Name.isNotEmpty()) "${s.df0Name} est dans DF0:. Allumez ! Pour changer de disquette, touchez « DF0: » à côté d'une autre dans la Bibliothèque." else "Ajoutez vos disquettes à la Bibliothèque, touchez « DF0: » à côté de l'une d'elles, puis allumez.") to s.df0Name.isNotEmpty()
            "ags" -> (if (s.hds.isNotEmpty()) "Les disques sont branchés : allumez, AGS démarre." else "Ajoutez les disques .hdf dans la carte Disques, puis allumez.") to s.hds.isNotEmpty()
            "cd" -> (if (s.cdName.isNotEmpty()) "${s.cdName} est prêt : allumez, ou touchez « ▶ CD32 » / « ▶ CDTV » à côté d'un autre CD de la Bibliothèque." else "Ajoutez vos CD à la Bibliothèque, puis touchez « ▶ CD32 » ou « ▶ CDTV ».") to s.cdName.isNotEmpty()
            else -> "AROS, la ROM libre, démarre un Workbench à découvrir. Pour les jeux, ajoutez plus tard vos Kickstart (carte Kickstart) ; l'assistant reste en haut de l'écran." to true
        }
        AlertDialog.Builder(this).setTitle("C'est prêt")
            .setMessage(msg + "\n\nPendant le jeu : ☰ pour le menu (disquettes, sauvegardes, manette tactile à disposer à votre goût…), ⏏ pour quitter un jeu WHDLoad.")
            .apply { if (canPower) setPositiveButton("▶ Allumer") { _, _ -> power() } else setPositiveButton("OK", null) }
            .setNeutralButton("Aide détaillée") { _, _ -> help() }.show()
    }

    // ---------- version web ----------
    /** La version web, à l'origine du projet : même cœur, rien à installer ; les configurations .uae passent de l'une à l'autre */
    private fun web() {
        AlertDialog.Builder(this).setTitle("🌐 La version web")
            .setMessage("""
                Amiga 1200 est né dans le navigateur : la version web tourne sur le même cœur Amiberry, sans rien installer, sur ordinateur, tablette ou téléphone.

                $WEB_URL

                Passer de l'une à l'autre : exportez une configuration .uae ici (carte Configurations), importez-la dans la version web, et inversement. Vos Kickstart et vos disques restent à ajouter de chaque côté.
            """.trimIndent())
            .setPositiveButton("Ouvrir la version web") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))) } catch (_: Exception) { toast("Aucun navigateur pour ouvrir $WEB_URL") }
            }
            .setNeutralButton("Copier le lien") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Amiga 1200", WEB_URL))
                toast("Lien copié")
            }
            .setNegativeButton("Fermer", null).show()
    }

    // ---------- aide ----------
    private fun help() {
        AlertDialog.Builder(this)
            .setTitle("Bien démarrer")
            .setMessage("""
                1. Kickstart : ajoutez vos fichiers ROM, plusieurs si vous voulez (3.1 pour l'A1200, 1.3 pour l'Amiga 500) : chacun sert à la machine qui lui convient. Sans eux, la ROM libre AROS démarre, mais WHDLoad, AGS et la plupart des jeux demandent un vrai Kickstart.
                Le plus simple : Bibliothèque → « Scanner un dossier… » (celui d'Amiga Forever, une clé USB, votre dossier Amiga) : Kickstart, jeux WHDLoad, disquettes, disques durs, CD et configurations .uae sont reconnus d'un coup ; un bilan montre ce qui est déjà là et les Kickstart qui manquent.
                2. Jeux et disques : ajoutez vos jeux WHDLoad (.lha), disquettes, disques durs (.hdf) et CD à la Bibliothèque. Ils sont lus sur place, sans copie, même s'ils font plusieurs Go. Un jeu WHDLoad se lance d'un toucher (▶ Jouer). Pour AGS, ajoutez tous ses .hdf d'un coup dans Disques.
                3. Préréglage : « AGS / Workbench » pour AGS et le Workbench, « Amiga 500 » pour les vieux jeux, « CD32 » pour la console. Carte Machine : A600, A3000, A4000 et CDTV aussi (le CDTV demande un Kickstart 1.3 et son extension).
                4. Allumez.

                Pendant le jeu (boutons en haut à droite) :
                ⌨  clavier Amiga : F1-F10, Help, Del, touches Amiga, flèches
                🖱/🕹  souris tactile ou manette tactile
                ⏸  pause · ⤓ sauvegarde rapide · ⤒ reprise rapide
                ⏏  quitter le jeu (F10 ; appui long : * ou Échap)
                ☰  menu : disquettes, sauvegardes d'état, image et captures, touches de la manette, disposition de la manette tactile (boutons déplacés, agrandis, touches du clavier ajoutées), réinitialisation, journal

                Souris tactile : glisser pour déplacer le pointeur, toucher pour cliquer, toucher à deux doigts pour le clic droit, appui long puis glisser pour déplacer une fenêtre.
                Bouton Retour du téléphone, ou F12 sur un clavier : menu.

                À l'accueil aussi : Image (filtre net ou écran cathodique, pixels réguliers), Son et MIDI (bruit des lecteurs, synthé MIDI), configurations enregistrées, et « Changer d'appareil » : tout exporter dans un fichier .zip, puis l'importer sur un autre téléphone. Appui long sur un jeu de la Bibliothèque : raccourci sur l'écran d'accueil du téléphone.

                🌐 Web : la version web d'origine, dans le navigateur ($WEB_URL) ; les configurations .uae passent de l'une à l'autre.
            """.trimIndent())
            .setPositiveButton("Compris", null)
            .show()
    }

    // ---------- fichiers ----------
    /** Scanner un dossier : choisi avec le sélecteur de dossier d'Android, gardé lisible (fichiers lus sur place) */
    private fun scanFolder() {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_SCAN) }
        catch (_: Exception) { toast("Choix de dossier indisponible sur cet appareil : utilisez « Ajouter des fichiers… »") }
    }

    private fun pick(req: Int, multiple: Boolean = false) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, req)
    }

    @Deprecated("API Activity simple, sans AndroidX")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        handleResult(req, res, data)
        // Assistant : étape suivante, que le choix de fichiers ait abouti ou non
        wizAfter?.let { wizAfter = null; it() }
    }

    private fun handleResult(req: Int, res: Int, data: Intent?) {
        if (res != RESULT_OK || data == null) return
        if (req == REQ_SCAN) {
            val tree = data.data ?: return
            try { contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            ScanDialog(this, s, again = { scanFolder() }, done = { refresh() }).start(tree)
            return
        }
        if (req == REQ_ROM && data.clipData != null) {
            // Plusieurs Kickstart choisis d'un coup (assistant)
            val c = data.clipData!!
            for (k in 0 until c.itemCount) c.getItemAt(k).uri.let { installRom(it, Disks.displayName(this, it)) }
            refresh()
            return
        }
        if (req == REQ_LIB) {
            val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            var last = Library.WHD
            val zips = mutableListOf<Pair<Uri, String>>()
            val hdfs = mutableListOf<Pair<Uri, String>>()
            for (u in uris) {
                val n = Disks.displayName(this, u)
                // Un Kickstart choisi avec les jeux : installé comme ROM de la machine, pas rangé en disquette
                if (Library.kindOf(n) == Library.ROM) { installRom(u, n); continue }
                keep(u)
                // Archive .zip : son contenu décide (disque dur, jeu WHDLoad ou disquette)
                if (n.endsWith(".zip", true)) { zips += u to n; continue }
                // Petit .hdf qui démarre WHDLoad tout seul : rangé avec les jeux
                if (Library.kindOf(n) == Library.HDF) { hdfs += u to n; continue }
                Library.add(this, u.toString(), n); last = Library.kindOf(n)
            }
            libKind = last
            for ((u, n) in hdfs) Library.add(this, u.toString(), n)
            if (hdfs.isNotEmpty()) Thread {
                val games = hdfs.filter { (u, _) -> ZipImport.bootsWhdload(this, u) }
                if (games.isNotEmpty()) runOnUiThread {
                    for ((u, n) in games) Library.add(this, u.toString(), n, Library.WHDHDF)
                    libKind = Library.WHD; refresh()
                    toast(games.joinToString(", ") { it.second } + " : jeu WHDLoad sur disque dur, rangé dans Jeux (▶ Jouer)")
                }
            }.start()
            if (zips.isNotEmpty()) importZips(zips) { fl -> for ((u, n) in fl) Library.add(this, u.toString(), n, Library.FLOPPY); if (fl.isNotEmpty()) libKind = Library.FLOPPY; refresh() }
            toast("${uris.size} fichier${if (uris.size > 1) "s" else ""} ajouté${if (uris.size > 1) "s" else ""} à la bibliothèque")
            refresh()
            return
        }
        if (req == REQ_HD) {
            // Un ou plusieurs disques, ajoutés à la suite (8 au plus)
            val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            val list = s.hds.toMutableList()
            for (u in uris) {
                if (list.size >= 8) { toast("8 disques durs au plus"); break }
                keep(u)
                val n = Disks.displayName(this, u)
                Library.add(this, u.toString(), n)
                if (list.none { it.uri == u.toString() }) list += Hd(u.toString(), n)
            }
            // Le Workbench d'AGS démarre : placé en premier s'il vient d'être ajouté
            list.sortBy { if (it.name.equals("Workbench.hdf", true)) 0 else 1 }
            s.hds = list
            profile()
            refresh()
            return
        }
        val uri: Uri = data.data ?: return
        val name = Disks.displayName(this, uri)
        when (req) {
            REQ_BLANK -> {
                if (!BlankDisk.write(this, uri, name.substringBeforeLast('.'))) { toast("Création de la disquette impossible"); return }
                keep(uri); Library.add(this, uri.toString(), name)
                s.df0 = uri.toString(); s.df0Name = name; profile()
                toast("Disquette vierge $name dans DF0:")
            }
            REQ_UAE -> toast(ConfigsIO.import(this, s, uri))
            REQ_BACKUP_OUT -> { backupTask("Export de la sauvegarde") { Backup.export(this, uri) }; return }
            REQ_BACKUP_IN -> {
                AlertDialog.Builder(this).setTitle("Importer la sauvegarde $name ?")
                    .setMessage("Les réglages, configurations et touches de la manette sont remplacés par ceux de la sauvegarde ; ses Kickstart, sauvegardes d'état et captures s'ajoutent (celles de même nom sont remplacées). Éteignez l'Amiga avant.")
                    .setPositiveButton("Importer") { _, _ -> backupTask("Import de la sauvegarde") { Backup.import(this, uri) } }
                    .setNegativeButton("Annuler", null).show()
                return
            }
            REQ_UAE_OUT -> toast(if (ConfigsIO.export(this, s, uri)) "Configuration exportée : $name" else "Export impossible")
            REQ_CD32, REQ_CD32EXT -> {
                val f = File(Config.sysDir(this), if (req == REQ_CD32) "cd32.rom" else "cd32ext.rom")
                if (!Disks.copy(this, uri, f)) toast("Lecture de la ROM impossible")
                else toast(if (req == REQ_CD32) "ROM CD32 : $name" else "Extension CD32 : $name")
            }
            REQ_CD -> { keep(uri); Library.add(this, uri.toString(), name); s.cd = uri.toString(); s.cdName = name; profile() }
            REQ_ROM -> installRom(uri, name)
            REQ_DF0 -> {
                if (Library.kindOf(name) == Library.ROM) { installRom(uri, name); refresh(); return }
                keep(uri)
                if (name.endsWith(".zip", true)) {
                    importZips(listOf(uri to name)) { fl ->
                        if (fl.isEmpty()) return@importZips
                        Library.add(this, uri.toString(), name, Library.FLOPPY); s.df0 = uri.toString(); s.df0Name = name; profile(); refresh()
                    }
                    return
                }
                Library.add(this, uri.toString(), name)
                // Une archive WHDLoad choisie comme disquette : elle se lance comme un jeu
                if (Library.kindOf(name) == Library.WHD) { libKind = Library.WHD; refresh(); playWhd(Library.all(this).first { it.uri == uri.toString() }); return }
                s.df0 = uri.toString(); s.df0Name = name; profile()
            }
        }
        refresh()
    }

    /** Machine retenue pour ce disque de démarrage (JIT ou non…) : reprise d'elle-même */
    private fun profile() {
        if (s.applyProfile()) toast("Réglages retenus pour ${s.bootKey()} repris")
    }

    /** Accès gardé après redémarrage du téléphone ; écriture si le fournisseur l'accepte */
    private fun keep(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: Exception) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
    }

    /** Appareil MIDI : sortie (ce que joue l'Amiga) ou entrée (clavier qui joue sur l'Amiga) */
    private fun midiChoose(output: Boolean) {
        val names = MidiLink.names(this, output)
        val items = listOf(if (output) "Premier appareil trouvé" else "Aucune") + names
        android.app.AlertDialog.Builder(this).setTitle(if (output) "Sortie MIDI" else "Entrée MIDI")
            .setItems(items.toTypedArray()) { _, i ->
                val v = if (i == 0) "" else names[i - 1]
                if (output) s.midiOut = v else s.midiIn = v
                refresh()
            }
            .apply { if (names.isEmpty()) setMessage("Aucun appareil MIDI branché. Branchez un instrument USB ou Bluetooth, ou installez une application de synthé MIDI.") }
            .setNegativeButton("Annuler", null).show()
    }

    private fun power(whdUri: String? = null, whdName: String? = null) {
        if (whdUri == null && s.model == "CDTV" && !cdtvReady()) { cdtvMissing(); return }
        startActivity(Intent(this, EmuActivity::class.java).apply {
            if (whdUri != null) { putExtra(EmuActivity.EXTRA_WHD_URI, whdUri); putExtra(EmuActivity.EXTRA_WHD_NAME, whdName) }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
        CrashReport.check(this)
    }

    /** Redémarrage demandé par l'émulation (« Réinitialiser ») : rallumage une fois son ancien processus terminé */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_PLAY) { playFromShortcut(intent); return }
        if (intent.getBooleanExtra(EXTRA_RESTART, false)) window.decorView.postDelayed({
            val hdf = intent.getStringExtra(EmuActivity.EXTRA_HDF_URI)
            if (hdf != null) relaunchHdf(hdf, intent.getStringExtra(EmuActivity.EXTRA_HDF_NAME))
            else power(intent.getStringExtra(EmuActivity.EXTRA_WHD_URI), intent.getStringExtra(EmuActivity.EXTRA_WHD_NAME))
        }, 1200)
    }

    // ---------- éléments d'interface ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun rounded(color: Int, r: Int = 12) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

    private fun card(col: LinearLayout, title: String, body: (LinearLayout) -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(CARD, 16)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        c.addView(TextView(this).apply { text = title; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(6)) })
        body(c)
        col.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(ACCENT); setPadding(0, dp(8), 0, dp(2)) }
    private fun note(t: String) = TextView(this).apply { text = t; textSize = 12f; alpha = 0.65f; setPadding(0, dp(6), 0, 0) }
    private fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
    private fun chip(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; background = rounded(CHIP, 18); setPadding(dp(14), dp(6), dp(14), dp(6))
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(6) }
    }
    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        for (b in v) addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
    }

    private fun switch(t: String, get: () -> Boolean, set: (Boolean) -> Unit) = Switch(this).apply {
        text = t; isChecked = get(); setPadding(0, dp(4), 0, dp(4))
        setOnCheckedChangeListener { _, on -> if (on != get()) { set(on); refresh() } }
        switches += this to get
    }

    /** Choix exclusif dessiné comme un bouton (machine) */
    private fun choice(t: String, on: () -> Boolean, pick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10))
        setOnClickListener { pick(); refresh() }
        choices += this to on
    }

    /** Préréglage : nom et description, surligné quand il correspond aux réglages */
    private fun preset(name: String, desc: String, active: Boolean, apply: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(if (active) ACCENT else CHIP)
        addView(TextView(this@MainActivity).apply { text = name; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE) })
        addView(TextView(this@MainActivity).apply { text = desc; textSize = 12f; setTextColor(Color.WHITE); alpha = 0.8f })
        setOnClickListener { apply(); refresh(); toast("Préréglage « $name »") }
    }

    companion object {
        /** Version web, à l'origine du projet */
        const val WEB_URL = "https://laurent-67370.github.io/amiga1200/"
        const val REQ_ROM = 1
        const val REQ_DF0 = 2
        const val REQ_HD = 3
        const val REQ_LIB = 4
        const val REQ_CD32 = 5
        const val REQ_CD32EXT = 6
        const val REQ_CD = 7
        const val REQ_UAE = 8
        const val REQ_UAE_OUT = 9
        const val REQ_BLANK = 10
        const val REQ_BACKUP_OUT = 11
        const val REQ_BACKUP_IN = 12
        const val REQ_SCAN = 13
        const val EXTRA_RESTART = "rallumer"
        const val ACTION_PLAY = "io.github.laurent67370.amiga1200.JOUER"
        const val EXTRA_PLAY_URI = "lien"
        const val EXTRA_PLAY_NAME = "nom"
        const val EXTRA_PLAY_KIND = "type"
        private val ACCENT = Color.rgb(230, 92, 30)
        private val BG = Color.rgb(20, 22, 28)
        private val CARD = Color.rgb(34, 37, 46)
        private val CHIP = Color.rgb(52, 56, 68)
    }
}
