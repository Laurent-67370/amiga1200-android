package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/** Réglages de l'image : filtre, pixels en multiple entier, et (en cours de partie) cadrage du jeu.
 *  onChange applique les réglages tout de suite. */
object DisplayDialog {
    /** [game] : jeu en cours (cadrage retenu pour lui), null depuis l'accueil */
    fun show(a: Activity, s: Settings, game: String?, onChange: () -> Unit = {}) {
        val d = a.resources.displayMetrics.density
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0) }
        fun note(t: String) = col.addView(TextView(a).apply { text = t; alpha = 0.7f; textSize = 12f; setPadding(0, 0, 0, (6 * d).toInt()) })

        val names = listOf("Lisse (adoucie)", "Nette (pixels francs)", "Écran cathodique (lignes de balayage)")
        col.addView(RadioGroup(a).apply {
            names.forEachIndexed { i, n -> addView(RadioButton(a).apply { id = 100 + i; text = n }) }
            check(100 + s.filter.coerceIn(0, 2))
            setOnCheckedChangeListener { _, id -> s.filter = id - 100; onChange() }
        })
        col.addView(Switch(a).apply {
            text = "Pixels réguliers (agrandissement en multiple entier)"; isChecked = s.integer
            setPadding(0, (6 * d).toInt(), 0, (2 * d).toInt())
            setOnCheckedChangeListener { _, on -> s.integer = on; onChange() }
        })
        note("Tous les pixels de l'Amiga ont la même taille ; l'image peut laisser une marge noire.")

        if (game != null) {
            var zoom = s.zoom(game); var shift = s.shiftY(game)
            val bars = mutableListOf<SeekBar>()
            fun slider(label: String, min: Float, max: Float, value: Float, fmt: (Float) -> String, set: (Float) -> Unit) {
                val t = TextView(a).apply { setPadding(0, (8 * d).toInt(), 0, 0) }
                fun show(v: Float) { t.text = "$label : ${fmt(v)}" }
                show(value)
                col.addView(t)
                col.addView(SeekBar(a).apply {
                    this.max = 100
                    progress = ((value - min) / (max - min) * 100).toInt().coerceIn(0, 100)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                            val v = min + (max - min) * p / 100f
                            show(v); set(v); s.setFraming(game, zoom, shift); onChange()
                        }
                        override fun onStartTrackingTouch(sb: SeekBar) {}
                        override fun onStopTrackingTouch(sb: SeekBar) {}
                    })
                }.also { bars += it })
            }
            slider("Zoom (ce jeu)", 1f, 1.6f, zoom, { String.format(Locale.FRANCE, "%.2f×", it) }) { zoom = it }
            slider("Position verticale", -0.25f, 0.25f, shift, { v -> if (kotlin.math.abs(v) < 0.01f) "centrée" else String.format(Locale.FRANCE, "%+d %%", (v * 100).toInt()) }) { shift = it }
            note("Le cadrage est retenu pour ce jeu : zoomez pour retirer les bandes noires, puis remontez ou descendez l'image.")
            AlertDialog.Builder(a).setTitle("Image").setView(col).setPositiveButton("OK", null)
                .setNeutralButton("Cadrage d'origine") { _, _ -> s.setFraming(game, 1f, 0f); onChange() }.show()
            return
        }
        AlertDialog.Builder(a).setTitle("Image").setView(col).setPositiveButton("OK", null).show()
    }
}
