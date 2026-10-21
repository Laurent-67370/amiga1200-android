package io.github.laurent67370.amiga1200

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Écran des réglages : Kickstart, machine, JIT, disquette et disque dur, puis « Allumer ». */
class MainActivity : Activity() {
    private lateinit var s: Settings
    private lateinit var romText: TextView
    private lateinit var df0Text: TextView
    private lateinit var dh0Text: TextView
    private lateinit var jitSwitch: Switch

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        s = Settings(this)
        val pad = dp(16)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        col.addView(TextView(this).apply {
            text = "Amiga 1200 — version Android native"
            textSize = 22f; setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(note("Prototype : cœur Amiberry (WinUAE 6) compilé pour ARM64, avec le JIT. Même cœur que la version web laurent-67370.github.io/amiga1200."))

        section(col, "Kickstart")
        romText = line(col)
        row(col, button("Choisir un Kickstart…") { pick(REQ_ROM) }, button("AROS (libre)") { s.rom = "aros"; refresh() })

        section(col, "Machine")
        val models = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        for ((id, label) in listOf("A1200" to "Amiga 1200", "A500" to "Amiga 500")) {
            models.addView(RadioButton(this).apply {
                text = label; tag = id; isChecked = s.model == id
                setOnClickListener { s.model = id; refresh() }
            })
        }
        col.addView(models)
        jitSwitch = Switch(this).apply {
            text = "JIT : 68040 traduit en code ARM64 (beaucoup plus rapide, un peu moins compatible)"
            isChecked = s.jit
            setOnCheckedChangeListener { _, on -> s.jit = on }
        }
        col.addView(jitSwitch)
        col.addView(Switch(this).apply {
            text = "NTSC (60 Hz) au lieu de PAL (50 Hz)"; isChecked = s.ntsc
            setOnCheckedChangeListener { _, on -> s.ntsc = on }
        })

        section(col, "Disquette DF0:")
        df0Text = line(col)
        row(col, button("Choisir…") { pick(REQ_DF0) }, button("Éjecter") { s.df0 = ""; s.df0Name = ""; refresh() })

        section(col, "Disque dur DH0: (.hdf, lu sur place)")
        dh0Text = line(col)
        row(col, button("Choisir…") { pick(REQ_DH0) }, button("Débrancher") { s.dh0 = ""; s.dh0Name = ""; refresh() })

        section(col, "Commandes")
        col.addView(Switch(this).apply {
            text = "Manette tactile à l'écran (sinon l'écran sert de souris)"; isChecked = s.touchPad
            setOnCheckedChangeListener { _, on -> s.touchPad = on }
        })
        col.addView(note("Souris tactile : glisser déplace le pointeur, toucher = clic gauche, toucher à deux doigts = clic droit, appui long puis glisser = déplacer une fenêtre ou une icône. Clavier et manettes Bluetooth ou USB reconnus. Bouton Retour : menu."))

        col.addView(Button(this).apply {
            text = "Allumer"; textSize = 20f
            setOnClickListener { power() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })
        col.addView(note("Mesurer le gain du JIT : menu → Turbo. La vitesse affichée (images par seconde) est la puissance brute ; comparez avec et sans JIT."))

        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(24, 26, 32)) })
        refresh()
    }

    private fun refresh() {
        romText.text = if (s.rom == "user" && File(Config.sysDir(this), "kick.rom").exists()) "Kickstart : ${s.romName}"
            else "AROS (ROM libre fournie) — pour les jeux, choisissez votre Kickstart 3.1 (A1200) ou 1.3 (A500)"
        df0Text.text = s.df0Name.ifEmpty { "aucune" }
        dh0Text.text = s.dh0Name.ifEmpty { "aucun" }
        jitSwitch.isEnabled = s.model == "A1200"
    }

    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, req)
    }

    @Deprecated("API Activity simple, sans AndroidX")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        val name = Disks.displayName(this, uri)
        when (req) {
            REQ_ROM -> {
                if (Disks.copy(this, uri, File(Config.sysDir(this), "kick.rom"))) { s.rom = "user"; s.romName = name }
                else toast("Lecture du Kickstart impossible")
            }
            REQ_DF0, REQ_DH0 -> {
                // Accès gardé après redémarrage du téléphone ; écriture si le fournisseur l'accepte
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                catch (_: Exception) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
                if (req == REQ_DF0) { s.df0 = uri.toString(); s.df0Name = name } else { s.dh0 = uri.toString(); s.dh0Name = name }
            }
        }
        refresh()
    }

    private fun power() {
        startActivity(Intent(this, EmuActivity::class.java))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun section(col: LinearLayout, t: String) = col.addView(TextView(this).apply {
        text = t; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(4))
    })
    private fun line(col: LinearLayout) = TextView(this).also { col.addView(it) }
    private fun note(t: String) = TextView(this).apply { text = t; alpha = 0.75f; setPadding(0, dp(6), 0, 0) }
    private fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    private fun row(col: LinearLayout, vararg v: View) = col.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START
        for (b in v) addView(b)
    })

    companion object {
        const val REQ_ROM = 1
        const val REQ_DF0 = 2
        const val REQ_DH0 = 3
    }
}
