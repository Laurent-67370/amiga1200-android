package io.github.laurent67370.amiga1200

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Clavier de l'Amiga à l'écran, disposé comme le vrai : Esc, F1 à F10, Del et Help, touches Amiga, flèches, pavé.
 *  Une touche reste enfoncée tant que le doigt la tient (l'Amiga fait lui-même la répétition) ; plusieurs doigts à la
 *  fois. Ctrl, Maj, Alt et les touches Amiga se verrouillent d'une touche et se relâchent après la touche suivante
 *  (deux touches de suite : verrouillage permanent, une troisième le défait). Codes RETROK, comme la version web
 *  (Help = Page suivante, Amiga = Super gauche/droite, parenthèses du pavé = F14/F15, voir les correctifs). */
@SuppressLint("ViewConstructor")
class AmigaKeyboard(ctx: Context, private val azerty: () -> Boolean) : View(ctx) {
    private class Key(val label: String, val code: Int, val w: Float = 1f, val azLabel: String? = null) {
        val rect = RectF()
        val mod get() = code in MODS
    }

    private val main = listOf(
        listOf(Key("Esc", 27, 1.2f)) + (1..10).map { Key("F$it", 281 + it) } + listOf(Key("Del", 127, 1.2f), Key("Help", 281, 1.3f)),
        listOf(Key("`", 96)) + "1234567890".map { Key("$it", it.code) } + listOf(Key("-", 45), Key("=", 61), Key("\\", 92), Key("⌫", 8, 1.4f)),
        listOf(Key("Tab", 9, 1.4f)) + "qwertyuiop".map { c -> Key(c.uppercase(), c.code, 1f, az(c)) } + listOf(Key("[", 91), Key("]", 93), Key("⏎", 13, 1.6f)),
        listOf(Key("Ctrl", 306, 1.2f), Key("Caps", 301, 1.2f)) + "asdfghjkl".map { c -> Key(c.uppercase(), c.code, 1f, az(c)) } +
            listOf(Key(";", 59, 1f, "M"), Key("'", 39), Key("⏎", 13, 1.6f)),
        listOf(Key("Maj", 304, 1.5f), Key("<>", 60)) + "zxcvbnm".map { c -> Key(c.uppercase(), c.code, 1f, az(c)) } +
            listOf(Key(",", 44, 1f, ";"), Key(".", 46, 1f, ":"), Key("/", 47, 1f, "!"), Key("Maj", 303, 1.6f), Key("↑", 273, 1.2f)),
        listOf(Key("Alt", 308, 1.3f), Key("A", 311, 1.3f), Key("Espace", 32, 7.2f), Key("A", 312, 1.3f), Key("Alt", 307, 1.3f),
            Key("←", 276, 1.2f), Key("↓", 274, 1.2f), Key("→", 275, 1.2f))
    )
    /** Pavé numérique, affiché à droite quand la place le permet */
    private val pad = listOf(
        listOf(Key("(", 295), Key(")", 296), Key("/", 267), Key("*", 268)),
        listOf(Key("7", 263), Key("8", 264), Key("9", 265), Key("-", 269)),
        listOf(Key("4", 260), Key("5", 261), Key("6", 262), Key("+", 270)),
        listOf(Key("1", 257), Key("2", 258), Key("3", 259), Key("⏎", 271)),
        listOf(Key("0", 256, 2f), Key(".", 266), Key("⏎", 271)),
        emptyList()
    )
    private var showPad = false
    private val all get() = (main + if (showPad) pad else emptyList()).flatten()

    /** Doigt → touche tenue */
    private val held = HashMap<Int, Key>()
    /** Modificateurs : 1 = pour la touche suivante, 2 = verrouillé */
    private val mods = HashMap<Int, Int>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(222, 218, 205) }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 146, 136) }
    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 136, 0) }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 190, 90) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); textAlign = Paint.Align.CENTER }

    init { setBackgroundColor(Color.argb(235, 40, 40, 46)) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        showPad = w > h * 3.2f
        val gap = min(w, h) * 0.012f
        val rows = main.size
        val rowH = (h - gap) / rows
        val unitsMain = main.maxOf { r -> r.sumOf { it.w.toDouble() } }.toFloat()
        val unitsPad = if (showPad) 4.6f else 0f
        val unit = (w - gap) / (unitsMain + unitsPad)
        fun place(rowsList: List<List<Key>>, x0: Float) {
            rowsList.forEachIndexed { r, row ->
                var x = x0
                for (k in row) {
                    k.rect.set(x + gap, gap + r * rowH, x + k.w * unit, (r + 1) * rowH)
                    x += k.w * unit
                }
            }
        }
        place(main, 0f)
        if (showPad) place(pad, unitsMain * unit + unit * 0.6f)
        text.textSize = rowH * 0.36f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val k = all.firstOrNull { it.rect.contains(e.getX(i), e.getY(i)) } ?: return true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (k.mod) toggleMod(k.code)
                else { held[e.getPointerId(i)] = k; Native.key(true, k.code) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(i))
            MotionEvent.ACTION_CANCEL -> held.keys.toList().forEach { release(it) }
        }
        invalidate()
        return true
    }

    private fun toggleMod(code: Int) {
        when (mods[code] ?: 0) {
            0 -> { mods[code] = 1; Native.key(true, code) }
            1 -> mods[code] = 2
            else -> { mods.remove(code); Native.key(false, code) }
        }
    }

    private fun release(id: Int) {
        val k = held.remove(id) ?: return
        Native.key(false, k.code)
        // Modificateurs pris pour une seule touche : relâchés après elle
        for ((c, st) in mods.entries.toList()) if (st == 1) { mods.remove(c); Native.key(false, c) }
    }

    /** Clavier masqué : rien ne doit rester enfoncé dans l'Amiga */
    fun releaseAll() {
        for (k in held.values) Native.key(false, k.code)
        held.clear()
        for (c in mods.keys) Native.key(false, c)
        mods.clear()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val az = azerty()
        val r = text.textSize * 0.35f
        for (k in all) {
            val st = mods[k.code] ?: 0
            val p = when {
                held.values.any { it === k } || st == 1 -> downPaint
                st == 2 -> lockPaint
                k.mod || k.label.length > 1 && k.code !in 48..122 -> darkPaint
                else -> keyPaint
            }
            c.drawRoundRect(k.rect, r, r, p)
            val label = if (az && k.azLabel != null) k.azLabel else k.label
            text.textSize = if (label.length > 3) k.rect.height() * 0.30f else k.rect.height() * 0.42f
            c.drawText(label, k.rect.centerX(), k.rect.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    companion object {
        private val MODS = setOf(304, 303, 306, 308, 307, 311, 312)

        /** Libellés AZERTY (clavier français de l'Amiga) : seules quelques lettres changent de place */
        private fun az(c: Char): String? = when (c) {
            'q' -> "A"; 'a' -> "Q"; 'w' -> "Z"; 'z' -> "W"; 'm' -> ","
            else -> null
        }
    }
}
