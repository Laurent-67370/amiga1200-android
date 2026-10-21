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
    private lateinit var df0Text: TextView
    private lateinit var hdList: LinearLayout
    private lateinit var presets: LinearLayout
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
            addView(chip("? Aide") { help() })
        })
        summary = TextView(this).apply { textSize = 14f; alpha = 0.85f; setPadding(0, dp(2), 0, dp(10)) }
        col.addView(summary)

        // Préréglages
        card(col, "⚡ Préréglages") { c ->
            presets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(presets)
            c.addView(note("Un geste règle la machine ; les détails restent modifiables plus bas."))
        }

        // Kickstart
        card(col, "🔑 Kickstart") { c ->
            romText = TextView(this).also { c.addView(it) }
            c.addView(row(button("Choisir un fichier…") { pick(REQ_ROM) }, button("AROS (libre)") { s.rom = "aros"; refresh() }))
        }

        // Disques
        card(col, "💾 Disques") { c ->
            c.addView(label("Disquette DF0:"))
            df0Text = TextView(this).also { c.addView(it) }
            c.addView(row(button("Insérer…") { pick(REQ_DF0) }, button("Éjecter") { s.df0 = ""; s.df0Name = ""; refresh() }))
            c.addView(label("Disques durs (lus sur place, sans copie)"))
            hdList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(hdList)
            c.addView(row(button("Ajouter des disques…") { pick(REQ_HD, multiple = true) }))
            c.addView(note("AGS : sélectionnez d'un coup tous ses fichiers .hdf (Workbench.hdf passe en premier, c'est lui qui démarre)."))
        }

        // Machine
        card(col, "⚙️ Machine") { c ->
            c.addView(row(choice("Amiga 1200", { s.model == "A1200" }) { s.model = "A1200" },
                choice("Amiga 500", { s.model == "A500" }) { s.model = "A500" }))
            c.addView(switch("JIT 68040 : beaucoup plus rapide (A1200)", { s.jit }) { s.jit = it })
            c.addView(switch("Carte graphique RTG : haute résolution d'AGS et du Workbench", { s.rtg }) { s.rtg = it })
            c.addView(switch("Volume SHARED: (sauvegardes AGS et WHDLoad)", { s.shared }) { s.shared = it })
            c.addView(switch("NTSC 60 Hz (sinon PAL 50 Hz)", { s.ntsc }) { s.ntsc = it })
        }

        // Commandes
        card(col, "🕹️ Commandes") { c ->
            c.addView(switch("Démarrer avec la manette tactile (sinon souris tactile)", { s.touchPad }) { s.touchPad = it })
            c.addView(switch("Clavier Amiga en AZERTY", { s.azerty }) { s.azerty = it })
            c.addView(switch("Afficher la vitesse en haut de l'écran", { s.showSpeed }) { s.showSpeed = it })
            c.addView(switch("Image étirée sur toute la largeur", { s.fill }) { s.fill = it })
        }
        col.addView(note("Version ${BuildConfig.VERSION_NAME} · même cœur que la version web laurent-67370.github.io/amiga1200"))

        // Bouton « Allumer » fixé en bas
        val power = Button(this).apply {
            text = "▶  Allumer l'Amiga"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(ACCENT) }
            setOnClickListener { power() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(BG)
            addView(ScrollView(this@MainActivity).apply { addView(col) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(power, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { setMargins(dp(14), dp(6), dp(14), dp(14)) })
        }
        setContentView(root)
        refresh()
        if (intent?.getBooleanExtra(EXTRA_RESTART, false) == true) window.decorView.postDelayed({ power() }, 1200)
        else if (!getSharedPreferences("aide", MODE_PRIVATE).getBoolean("vue", false)) {
            getSharedPreferences("aide", MODE_PRIVATE).edit().putBoolean("vue", true).apply()
            window.decorView.post { help() }
        }
    }

    // ---------- état affiché ----------
    private fun userRom() = s.rom == "user" && File(Config.sysDir(this), "kick.rom").exists()

    private fun refresh() {
        val a500 = s.model == "A500"
        val parts = mutableListOf(if (a500) "Amiga 500" else "Amiga 1200")
        if (!a500) parts += if (s.jit) "68040 JIT" else "68020"
        if (!a500 && s.rtg) parts += "RTG"
        parts += if (userRom()) "Kickstart ${s.romName.substringBeforeLast('.')}" else "AROS"
        val n = s.hds.size
        if (n > 0) parts += "$n disque${if (n > 1) "s" else ""} dur${if (n > 1) "s" else ""}"
        if (s.df0Name.isNotEmpty()) parts += "DF0: ${s.df0Name}"
        summary.text = parts.joinToString(" · ")

        romText.text = if (userRom()) "✓ ${s.romName}" else "AROS, ROM libre fournie. Pour les jeux et AGS, choisissez votre Kickstart 3.1 (A1200) ou 1.3 (A500)."
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
            preset("A1200 d'origine", "68020, jeux AGA", s.model == "A1200" && !s.jit && !s.rtg) { s.model = "A1200"; s.jit = false; s.rtg = false }))
        presets.addView(row(
            preset("Amiga 500", "jeux et démos OCS", a500) { s.model = "A500"; s.jit = false; s.rtg = false }))
        for ((sw, get) in switches) sw.isChecked = get()
        for ((v, on) in choices) {
            v.background = rounded(if (on()) ACCENT else CHIP)
            v.setTextColor(if (on()) Color.WHITE else Color.rgb(220, 220, 225))
        }
    }

    // ---------- aide ----------
    private fun help() {
        AlertDialog.Builder(this)
            .setTitle("Bien démarrer")
            .setMessage("""
                1. Kickstart : choisissez votre fichier ROM (Kickstart 3.1 pour l'A1200). Sans lui, la ROM libre AROS démarre.
                2. Disques : insérez une disquette, ou ajoutez vos disques durs .hdf. Ils sont lus sur place, sans copie, même s'ils font plusieurs Go. Pour AGS, ajoutez tous ses fichiers d'un coup.
                3. Préréglage : « AGS / Workbench » pour AGS et le Workbench, « Amiga 500 » pour les vieux jeux.
                4. Allumez.

                Pendant le jeu (boutons en haut à droite) :
                ⌨  clavier Amiga : F1-F10, Help, Del, touches Amiga, flèches
                🖱/🕹  souris tactile ou manette tactile
                ⏸  pause · ⤓ sauvegarde rapide · ⤒ reprise rapide
                ☰  menu : disquettes, sauvegardes, écran, réinitialisation, journal

                Souris tactile : glisser pour déplacer le pointeur, toucher pour cliquer, toucher à deux doigts pour le clic droit, appui long puis glisser pour déplacer une fenêtre.
                Bouton Retour du téléphone, ou F12 sur un clavier : menu.
            """.trimIndent())
            .setPositiveButton("Compris", null)
            .show()
    }

    // ---------- fichiers ----------
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
        if (res != RESULT_OK || data == null) return
        if (req == REQ_HD) {
            // Un ou plusieurs disques, ajoutés à la suite (8 au plus)
            val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            val list = s.hds.toMutableList()
            for (u in uris) {
                if (list.size >= 8) { toast("8 disques durs au plus"); break }
                keep(u)
                if (list.none { it.uri == u.toString() }) list += Hd(u.toString(), Disks.displayName(this, u))
            }
            // Le Workbench d'AGS démarre : placé en premier s'il vient d'être ajouté
            list.sortBy { if (it.name.equals("Workbench.hdf", true)) 0 else 1 }
            s.hds = list
            refresh()
            return
        }
        val uri: Uri = data.data ?: return
        val name = Disks.displayName(this, uri)
        when (req) {
            REQ_ROM -> {
                if (Disks.copy(this, uri, File(Config.sysDir(this), "kick.rom"))) { s.rom = "user"; s.romName = name }
                else toast("Lecture du Kickstart impossible")
            }
            REQ_DF0 -> { keep(uri); s.df0 = uri.toString(); s.df0Name = name }
        }
        refresh()
    }

    /** Accès gardé après redémarrage du téléphone ; écriture si le fournisseur l'accepte */
    private fun keep(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: Exception) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
    }

    private fun power() {
        startActivity(Intent(this, EmuActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        refresh()
        CrashReport.check(this)
    }

    /** Redémarrage demandé par l'émulation (« Réinitialiser ») : rallumage une fois son ancien processus terminé */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESTART, false)) window.decorView.postDelayed({ power() }, 1200)
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
        const val REQ_ROM = 1
        const val REQ_DF0 = 2
        const val REQ_HD = 3
        const val EXTRA_RESTART = "rallumer"
        private val ACCENT = Color.rgb(230, 92, 30)
        private val BG = Color.rgb(20, 22, 28)
        private val CARD = Color.rgb(34, 37, 46)
        private val CHIP = Color.rgb(52, 56, 68)
    }
}
