package io.github.laurent67370.amiga1200

import android.view.KeyEvent

/** Touches Android → codes RETROK du clavier libretro (ceux de la version web, wasm/web/puae-frontend.js). */
object Keymap {
    private val map = HashMap<Int, Int>().apply {
        for (i in 0..25) put(KeyEvent.KEYCODE_A + i, 97 + i)
        for (i in 0..9) put(KeyEvent.KEYCODE_0 + i, 48 + i)
        for (i in 0..9) put(KeyEvent.KEYCODE_NUMPAD_0 + i, 256 + i)
        for (i in 0..9) put(KeyEvent.KEYCODE_F1 + i, 282 + i)
        put(KeyEvent.KEYCODE_DEL, 8); put(KeyEvent.KEYCODE_TAB, 9); put(KeyEvent.KEYCODE_ENTER, 13)
        put(KeyEvent.KEYCODE_ESCAPE, 27); put(KeyEvent.KEYCODE_SPACE, 32); put(KeyEvent.KEYCODE_APOSTROPHE, 39)
        put(KeyEvent.KEYCODE_COMMA, 44); put(KeyEvent.KEYCODE_MINUS, 45); put(KeyEvent.KEYCODE_PERIOD, 46)
        put(KeyEvent.KEYCODE_SLASH, 47); put(KeyEvent.KEYCODE_SEMICOLON, 59); put(KeyEvent.KEYCODE_EQUALS, 61)
        put(KeyEvent.KEYCODE_LEFT_BRACKET, 91); put(KeyEvent.KEYCODE_BACKSLASH, 92); put(KeyEvent.KEYCODE_RIGHT_BRACKET, 93)
        put(KeyEvent.KEYCODE_GRAVE, 96); put(KeyEvent.KEYCODE_FORWARD_DEL, 127)
        put(KeyEvent.KEYCODE_NUMPAD_DOT, 266); put(KeyEvent.KEYCODE_NUMPAD_DIVIDE, 267); put(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 268)
        put(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, 269); put(KeyEvent.KEYCODE_NUMPAD_ADD, 270); put(KeyEvent.KEYCODE_NUMPAD_ENTER, 271)
        put(KeyEvent.KEYCODE_DPAD_UP, 273); put(KeyEvent.KEYCODE_DPAD_DOWN, 274)
        put(KeyEvent.KEYCODE_DPAD_RIGHT, 275); put(KeyEvent.KEYCODE_DPAD_LEFT, 276)
        put(KeyEvent.KEYCODE_INSERT, 277); put(KeyEvent.KEYCODE_MOVE_HOME, 278); put(KeyEvent.KEYCODE_MOVE_END, 279)
        put(KeyEvent.KEYCODE_PAGE_UP, 280); put(KeyEvent.KEYCODE_PAGE_DOWN, 281)
        put(KeyEvent.KEYCODE_CAPS_LOCK, 301); put(KeyEvent.KEYCODE_SHIFT_RIGHT, 303); put(KeyEvent.KEYCODE_SHIFT_LEFT, 304)
        put(KeyEvent.KEYCODE_CTRL_RIGHT, 305); put(KeyEvent.KEYCODE_CTRL_LEFT, 306)
        put(KeyEvent.KEYCODE_ALT_RIGHT, 307); put(KeyEvent.KEYCODE_ALT_LEFT, 308)
        put(KeyEvent.KEYCODE_META_LEFT, 311); put(KeyEvent.KEYCODE_META_RIGHT, 312)
    }

    fun retrok(keyCode: Int): Int = map[keyCode] ?: 0

    /** Caractère tapé sur un clavier virtuel → touche (et Maj) du clavier QWERTY de l'Amiga */
    private const val SHIFTED = "~!@#$%^&*()_+{}|:\"<>?"
    private const val PLAIN = "`1234567890-=[]\;',./"
    fun forChar(c: Char): Pair<Int, Boolean>? = when {
        c in 'a'..'z' -> Pair(c.code, false)
        c in 'A'..'Z' -> Pair(c.lowercaseChar().code, true)
        c in '0'..'9' -> Pair(c.code, false)
        c == ' ' -> Pair(32, false)
        c == '\n' -> Pair(13, false)
        c in PLAIN -> Pair(c.code, false)
        c in SHIFTED -> Pair(PLAIN[SHIFTED.indexOf(c)].code, true)
        else -> null
    }

    /** Boutons de manette → bits RETRO_DEVICE_ID_JOYPAD_* (A = tir, B = 2e bouton, comme la version web) */
    fun padBit(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> 1 shl 0
        KeyEvent.KEYCODE_BUTTON_X -> 1 shl 1
        KeyEvent.KEYCODE_BUTTON_SELECT -> 1 shl 2
        KeyEvent.KEYCODE_BUTTON_START -> 1 shl 3
        KeyEvent.KEYCODE_DPAD_UP -> 1 shl 4
        KeyEvent.KEYCODE_DPAD_DOWN -> 1 shl 5
        KeyEvent.KEYCODE_DPAD_LEFT -> 1 shl 6
        KeyEvent.KEYCODE_DPAD_RIGHT -> 1 shl 7
        KeyEvent.KEYCODE_BUTTON_B -> 1 shl 8
        KeyEvent.KEYCODE_BUTTON_Y -> 1 shl 9
        KeyEvent.KEYCODE_BUTTON_L1 -> 1 shl 10
        KeyEvent.KEYCODE_BUTTON_R1 -> 1 shl 11
        else -> 0
    }
}
