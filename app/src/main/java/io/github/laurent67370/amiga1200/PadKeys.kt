package io.github.laurent67370.amiga1200

import android.app.AlertDialog
import android.content.Context
import android.view.KeyEvent
import org.json.JSONObject

/** Boutons de manette → touches du clavier Amiga, retenus pour chaque jeu (les jeux qui demandent Espace, F1, Return…).
 *  A et B restent les boutons de tir. */
object PadKeys {
    /** Boutons réglables : code Android et nom */
    val buttons = listOf(
        KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_START to "Start", KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_BUTTON_L1 to "L1", KeyEvent.KEYCODE_BUTTON_R1 to "R1",
        KeyEvent.KEYCODE_BUTTON_L2 to "L2", KeyEvent.KEYCODE_BUTTON_R2 to "R2",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3", KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
    )

    /** Touches proposées (codes RETROK du cœur) ; 0 : le bouton reste un bouton de manette */
    val keys: List<Pair<Int, String>> = listOf(
        0 to "Manette (par défaut)", 32 to "Espace", 13 to "Return", 27 to "Échap", 127 to "Del", 8 to "Retour arrière",
        281 to "Help", 9 to "Tab", 304 to "Maj", 306 to "Ctrl", 308 to "Alt", 311 to "Amiga gauche", 312 to "Amiga droite",
        273 to "↑", 274 to "↓", 276 to "←", 275 to "→", 268 to "* (pavé)",
    ) + (1..10).map { 281 + it to "F$it" } + (1..9).map { 48 + it to "$it" } + listOf(48 to "0") +
        "ynpqmsjc".map { it.code to it.uppercase() }

    fun label(k: Int) = keys.firstOrNull { it.first == k }?.second ?: "?"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("touches", Context.MODE_PRIVATE)

    /** Réglage d'un jeu : bouton → touche */
    fun load(ctx: Context, game: String): Map<Int, Int> {
        val o = try { JSONObject(prefs(ctx).getString(game, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        return o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.optInt(k) } }.filter { it.second != 0 }.toMap()
    }

    fun save(ctx: Context, game: String, map: Map<Int, Int>) {
        val o = JSONObject()
        for ((b, k) in map) if (k != 0) o.put(b.toString(), k)
        prefs(ctx).edit().putString(game, o.toString()).apply()
    }

    /** Fenêtre de réglage ; [done] reçoit le nouveau réglage */
    fun dialog(ctx: Context, game: String, title: String, done: (Map<Int, Int>) -> Unit) {
        val map = load(ctx, game).toMutableMap()
        fun show() {
            val rows = buttons.map { (code, name) -> "$name  →  ${label(map[code] ?: 0)}" }
            AlertDialog.Builder(ctx).setTitle("Touches de la manette : $title")
                .setItems(rows.toTypedArray()) { _, i ->
                    val (code, name) = buttons[i]
                    AlertDialog.Builder(ctx).setTitle("Bouton $name")
                        .setItems(keys.map { it.second }.toTypedArray()) { _, j ->
                            val k = keys[j].first
                            if (k == 0) map.remove(code) else map[code] = k
                            save(ctx, game, map); done(map); show()
                        }.setNegativeButton("Retour") { _, _ -> show() }.show()
                }
                .setNeutralButton("Tout effacer") { _, _ -> map.clear(); save(ctx, game, map); done(map) }
                .setPositiveButton("Fermer", null).show()
        }
        show()
    }
}
