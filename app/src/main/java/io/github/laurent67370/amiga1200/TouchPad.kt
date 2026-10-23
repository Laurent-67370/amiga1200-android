package io.github.laurent67370.amiga1200

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Bouton de la manette tactile : tir 1 (FIRE1), tir 2 (FIRE2) ou touche du clavier Amiga (code RETROK) ;
 *  centre en fraction de l'écran, taille relative */
data class PadButton(val key: Int, var x: Float, var y: Float, var size: Float = 1f) {
    val label: String get() = when (key) { PadLayout.FIRE1 -> "Tir"; PadLayout.FIRE2 -> "Tir 2"; else -> PadKeys.label(key) }
}

/** Disposition de la manette tactile, retenue pour un jeu ou pour tous (« touches », comme les touches de la
 *  manette physique : elle part avec la sauvegarde complète) */
object PadLayout {
    const val FIRE1 = -1
    const val FIRE2 = -2
    private const val ALL = "manette:*"

    fun default() = mutableListOf(PadButton(FIRE1, 0.90f, 0.80f, 1f), PadButton(FIRE2, 0.80f, 0.56f, 0.8f))

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("touches", Context.MODE_PRIVATE)

    private fun parse(t: String?): MutableList<PadButton>? {
        val a = try { JSONArray(t ?: return null) } catch (_: Exception) { return null }
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { PadButton(it.getInt("k"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.optDouble("s", 1.0).toFloat()) }
            .toMutableList()
    }

    /** Celle du jeu, sinon celle de tous les jeux, sinon tir 1 et tir 2 à droite */
    fun load(ctx: Context, game: String) = parse(prefs(ctx).getString("manette:$game", null)) ?: parse(prefs(ctx).getString(ALL, null)) ?: default()
    fun hasOwn(ctx: Context, game: String) = prefs(ctx).contains("manette:$game")

    /** [forAll] : pour tous les jeux (et ce jeu ne garde plus la sienne) ; sinon pour ce jeu seulement */
    fun save(ctx: Context, game: String, list: List<PadButton>, forAll: Boolean) {
        val a = JSONArray()
        for (b in list) a.put(JSONObject().put("k", b.key).put("x", b.x.toDouble()).put("y", b.y.toDouble()).put("s", b.size.toDouble()))
        val e = prefs(ctx).edit()
        if (forAll) e.putString(ALL, a.toString()).remove("manette:$game") else e.putString("manette:$game", a.toString())
        e.apply()
    }
}

/** Manette tactile : joystick là où le doigt se pose dans la moitié gauche (hors boutons), boutons placés à volonté
 *  (tir 1, tir 2, touches du clavier Amiga). Plusieurs doigts à la fois. En mode édition, les boutons se déplacent
 *  au doigt et le bouton touché devient le bouton choisi. */
@SuppressLint("ViewConstructor")
class TouchPad(private val emu: EmuActivity, private val size: Float = 1f, var layout: MutableList<PadButton>) : View(emu) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(110, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val chosen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.rgb(230, 92, 30) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); textAlign = Paint.Align.CENTER }
    private var stickId = -1
    private var cx = 0f; private var cy = 0f; private var kx = 0f; private var ky = 0f
    private var dirs = 0
    /** doigt → bouton tenu */
    private val held = HashMap<Int, PadButton>()
    private var buttons = 0

    /** Édition de la disposition : [onSelect] reçoit le bouton choisi */
    var editing = false
        set(v) { field = v; selected = null; held.clear(); stickId = -1; invalidate() }
    var selected: PadButton? = null
        private set
    var onSelect: (PadButton?) -> Unit = {}
    private var dragId = -1; private var dragDx = 0f; private var dragDy = 0f

    private val r get() = min(width, height) * 0.16f * size
    private fun radius(b: PadButton) = r * 0.75f * b.size
    private fun bx(b: PadButton) = b.x * width
    private fun by(b: PadButton) = b.y * height

    /** Bouton sous le doigt (un peu de marge autour) */
    private fun hit(x: Float, y: Float): PadButton? =
        layout.filter { hypot(x - bx(it), y - by(it)) < radius(it) * 1.25f }.minByOrNull { hypot(x - bx(it), y - by(it)) }

    fun select(b: PadButton?) { selected = b; onSelect(b); invalidate() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editing) return edit(e)
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                // Un bouton ; sinon le joystick à gauche ; à droite, le bouton le plus proche (comme les grandes zones de tir)
                val b = hit(x, y) ?: if (x >= width / 2f) layout.minByOrNull { hypot(x - bx(it), y - by(it)) } else null
                if (b != null) press(id, b)
                else if (stickId < 0) { stickId = id; cx = x; cy = y; kx = x; ky = y }
            }
            MotionEvent.ACTION_MOVE -> for (p in 0 until e.pointerCount) if (e.getPointerId(p) == stickId) { kx = e.getX(p); ky = e.getY(p) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { stickId = -1; for (id in held.keys.toList()) release(id) }
                val id = e.getPointerId(i)
                if (id == stickId) stickId = -1
                release(id)
            }
        }
        // Direction : 8 directions, zone morte d'un quart du rayon
        var d = 0
        if (stickId >= 0) {
            val dx = kx - cx; val dy = ky - cy; val dead = r * 0.25f
            if (dy < -dead && abs(dy) > abs(dx) * 0.4f) d = d or (1 shl 4)
            if (dy > dead && abs(dy) > abs(dx) * 0.4f) d = d or (1 shl 5)
            if (dx < -dead && abs(dx) > abs(dy) * 0.4f) d = d or (1 shl 6)
            if (dx > dead && abs(dx) > abs(dy) * 0.4f) d = d or (1 shl 7)
        }
        dirs = d
        update()
        invalidate()
        return true
    }

    private fun press(id: Int, b: PadButton) {
        held[id] = b
        if (b.key > 0 && held.values.count { it === b } == 1) Native.key(true, b.key)
        update()
    }

    private fun release(id: Int) {
        val b = held.remove(id) ?: return
        if (b.key > 0 && held.values.none { it === b }) Native.key(false, b.key)
        update()
    }

    private fun update() {
        val b = held.values.fold(0) { a, v -> a or when (v.key) { PadLayout.FIRE1 -> 1; PadLayout.FIRE2 -> 1 shl 8; else -> 0 } }
        val m = dirs or b
        if (m != buttons) { buttons = m; emu.touchJoy(m) }
    }

    private fun edit(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val b = hit(e.getX(i), e.getY(i))
                select(b)
                if (b != null) { dragId = e.getPointerId(i); dragDx = bx(b) - e.getX(i); dragDy = by(b) - e.getY(i) }
            }
            MotionEvent.ACTION_MOVE -> {
                val b = selected ?: return true
                val p = e.findPointerIndex(dragId); if (p < 0) return true
                b.x = ((e.getX(p) + dragDx) / width).coerceIn(0.03f, 0.97f)
                b.y = ((e.getY(p) + dragDy) / height).coerceIn(0.05f, 0.97f)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragId = -1
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val rr = r
        if (stickId >= 0) {
            c.drawCircle(cx, cy, rr, paint)
            c.drawCircle(kx, ky, rr * 0.45f, fill)
        } else c.drawCircle(rr * 1.6f, height - rr * 1.6f, rr, paint)
        for (b in layout) {
            val br = radius(b)
            val down = held.values.any { it === b }
            c.drawCircle(bx(b), by(b), br, if (down) fill else paint)
            if (editing && b === selected) c.drawCircle(bx(b), by(b), br + 6f, chosen)
            text.textSize = (br * 0.5f).coerceIn(18f, 44f)
            if (b.key > 0 || editing) c.drawText(b.label, bx(b), by(b) + text.textSize * 0.35f, text)
        }
        if (editing) {
            text.textSize = 30f
            c.drawText("Joystick : où le doigt se pose, à gauche", rr * 3.4f, height - rr * 0.4f, text)
        }
    }
}
