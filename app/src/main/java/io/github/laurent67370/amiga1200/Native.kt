package io.github.laurent67370.amiga1200

import android.view.Surface

/** Cœur Amiberry et frontal de la version web, reliés par native/jni_bridge.c (bibliothèque libamiga.so). */
object Native {
    init { System.loadLibrary("amiga") }

    external fun setDirs(sys: String, save: String)
    external fun setOption(key: String, value: String)
    external fun setSurface(surface: Surface?)
    external fun start(path: String): Boolean
    /** 1 en démarrage, 2 en marche, -1 refus du cœur, 0 échec */
    external fun state(): Int
    external fun pause(paused: Boolean)
    external fun warp(factor: Int)
    external fun reset()
    external fun stop()

    external fun mouseMove(dx: Int, dy: Int)
    /** 1 gauche, 2 droit, 4 milieu */
    external fun mouseButtons(mask: Int)
    /** bits RETRO_DEVICE_ID_JOYPAD_* */
    external fun joy(port: Int, mask: Int)
    external fun key(down: Boolean, retrok: Int)

    /** Chemin vide : éjecte */
    external fun insertDisk(drive: Int, path: String)
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean

    /** Images calculées depuis le dernier appel */
    external fun frames(): Int
    /** Temps de calcul (µs) depuis le dernier appel */
    external fun busyUs(): Double
    external fun fps(): Double
    external fun videoW(): Int
    external fun videoH(): Int
    external fun led(index: Int): Int
}
