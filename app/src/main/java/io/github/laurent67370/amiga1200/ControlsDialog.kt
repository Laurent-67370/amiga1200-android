package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/** Réglages des commandes : vitesse du pointeur, taille de la manette tactile, boutons de souris, boutons de manette.
 *  Ouvert depuis l'accueil ou le menu de l'émulation ; onChange applique les réglages tout de suite. */
object ControlsDialog {
    fun show(a: Activity, s: Settings, onChange: () -> Unit = {}) {
        val d = a.resources.displayMetrics.density
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0) }

        fun slider(label: String, min: Float, max: Float, value: Float, set: (Float) -> Unit) {
            val t = TextView(a)
            fun show(v: Float) { t.text = String.format(Locale.FRANCE, "%s : %.2f×", label, v) }
            show(value)
            col.addView(t)
            col.addView(SeekBar(a).apply {
                this.max = 100
                progress = ((value - min) / (max - min) * 100).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                        val v = min + (max - min) * p / 100f
                        show(v); set(v); onChange()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            })
        }
        fun switch(label: String, value: Boolean, set: (Boolean) -> Unit) = col.addView(Switch(a).apply {
            text = label; isChecked = value; setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            setOnCheckedChangeListener { _, on -> set(on); onChange() }
        })

        slider("Vitesse du pointeur (souris tactile)", 0.5f, 3f, s.mouseSpeed) { s.mouseSpeed = it }
        slider("Taille de la manette tactile", 0.6f, 1.6f, s.padSize) { s.padSize = it }
        switch("Boutons gauche et droit de la souris à l'écran", s.mouseKeys) { s.mouseKeys = it }
        switch("Manette : échanger tir 1 et tir 2", s.swapFire) { s.swapFire = it }
        switch("Manette : bouton B = haut (sauter)", s.jumpB) { s.jumpB = it }
        col.addView(TextView(a).apply {
            text = "Deux manettes branchées : la première joue sur le port joystick, la seconde sur le port souris (jeux à deux)."
            alpha = 0.7f; textSize = 12f; setPadding(0, (8 * d).toInt(), 0, 0)
        })
        AlertDialog.Builder(a).setTitle("Réglages des commandes").setView(col).setPositiveButton("OK", null).show()
    }
}
