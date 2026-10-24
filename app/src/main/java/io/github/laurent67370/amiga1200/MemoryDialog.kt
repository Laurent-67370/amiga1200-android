package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/** Mémoire et processeur : retenus pour le disque de démarrage, comme la machine (les vieux jeux qui veulent 512 Ko,
 *  ceux qui manquent de mémoire, les démos qui demandent un 68030…). Prochain allumage. */
object MemoryDialog {
    fun show(a: Activity, s: Settings, onChange: () -> Unit = {}) {
        val d = a.resources.displayMetrics.density
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0) }

        fun group(label: String, items: List<Pair<String, String>>, current: String, set: (String) -> Unit) {
            col.addView(TextView(a).apply { text = label; textSize = 15f; setPadding(0, (10 * d).toInt(), 0, (2 * d).toInt()) })
            val g = RadioGroup(a).apply { orientation = RadioGroup.VERTICAL }
            items.forEachIndexed { i, (v, t) ->
                g.addView(RadioButton(a).apply { id = i + 1; text = t; isChecked = v == current })
            }
            g.setOnCheckedChangeListener { _, id -> set(items[id - 1].first); onChange() }
            col.addView(g)
        }

        group("Mémoire Chip", listOf("0" to "Celle du modèle", "1" to "512 Ko", "2" to "1 Mo (chipset ECS ou AGA)", "4" to "2 Mo (chipset ECS ou AGA)"),
            s.chip.toString()) { s.chip = it.toInt() }
        group("Mémoire Slow (trappe de l'Amiga 500)", listOf("-1" to "Celle du modèle", "0" to "Aucune", "2" to "512 Ko", "4" to "1 Mo"),
            s.slow.toString()) { s.slow = it.toInt() }
        group("Mémoire Fast (sans JIT)", listOf("0" to "Aucune", "2" to "2 Mo", "4" to "4 Mo", "8" to "8 Mo"),
            s.fast.toString()) { s.fast = it.toInt() }
        group("Carte accélératrice (A1200, A3000, A4000 sans JIT)", listOf("none" to "Aucune (processeur du modèle)", "030" to "68030 + FPU 68882",
            "040" to "68040"), s.accel) { s.accel = it }
        col.addView(TextView(a).apply {
            text = "Retenus pour le disque de démarrage, au prochain allumage. Un jeu qui affiche « Error code 103 » manque de mémoire ; " +
                "certains vieux jeux A500 ne marchent qu'avec 512 Ko de Chip et sans Slow. Le JIT a déjà 64 Mo de Fast et un 68040."
            alpha = 0.7f; textSize = 12f; setPadding(0, (10 * d).toInt(), 0, 0)
        })
        AlertDialog.Builder(a).setTitle("Mémoire et processeur")
            .setView(android.widget.ScrollView(a).apply { addView(col) })
            .setPositiveButton("OK", null).show()
    }
}
