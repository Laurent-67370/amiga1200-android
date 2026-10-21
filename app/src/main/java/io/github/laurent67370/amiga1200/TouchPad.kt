package io.github.laurent67370.amiga1200

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** Manette tactile : moitié gauche = joystick (la direction part de l'endroit où le doigt se pose),
 *  moitié droite = bouton de tir (en bas) et 2e bouton (en haut). Plusieurs doigts à la fois. */
@SuppressLint("ViewConstructor")
class TouchPad(private val emu: EmuActivity) : View(emu) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(110, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private var stickId = -1
    private var cx = 0f; private var cy = 0f; private var kx = 0f; private var ky = 0f
    private var dirs = 0
    /** doigt → bit du bouton tenu */
    private val held = HashMap<Int, Int>()
    private var buttons = 0

    private val r get() = min(width, height) * 0.16f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                if (x < width / 2f) { if (stickId < 0) { stickId = id; cx = x; cy = y; kx = x; ky = y } }
                else { held[id] = if (y > height / 2f) 1 shl 0 else 1 shl 8; update() }
            }
            MotionEvent.ACTION_MOVE -> for (p in 0 until e.pointerCount) if (e.getPointerId(p) == stickId) { kx = e.getX(p); ky = e.getY(p) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(i)
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { stickId = -1; held.clear() }
                if (id == stickId) stickId = -1
                held.remove(id)
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

    private fun update() {
        val b = held.values.fold(0) { a, v -> a or v }
        val m = dirs or b
        if (m != buttons) { buttons = m; emu.touchJoy(m) }
    }

    override fun onDraw(c: Canvas) {
        val rr = r
        if (stickId >= 0) {
            c.drawCircle(cx, cy, rr, paint)
            c.drawCircle(kx, ky, rr * 0.45f, fill)
        } else c.drawCircle(rr * 1.6f, height - rr * 1.6f, rr, paint)
        val bx = width - rr * 1.5f
        c.drawCircle(bx, height - rr * 1.5f, rr * 0.75f, if (buttons and 1 != 0) fill else paint)
        c.drawCircle(bx - rr * 0.4f, height - rr * 3.6f, rr * 0.6f, if (buttons and (1 shl 8) != 0) fill else paint)
    }
}
