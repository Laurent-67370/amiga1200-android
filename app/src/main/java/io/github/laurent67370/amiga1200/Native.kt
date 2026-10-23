package io.github.laurent67370.amiga1200

import android.view.Surface

/** Cœur Amiberry et frontal de la version web, reliés par native/jni_bridge.c (bibliothèque libamiga.so). */
object Native {
    init { System.loadLibrary("amiga") }

    /** Journal du cœur et de l'application (menu → Journal) */
    external fun setLogFile(path: String)
    external fun log(message: String)

    external fun setDirs(sys: String, save: String)
    external fun setOption(key: String, value: String)
    external fun setSurface(surface: Surface?)
    /** Filtre de l'image (0 lisse, 1 net, 2 écran cathodique) et taille de la vue */
    external fun setFilter(mode: Int, viewW: Int, viewH: Int)
    /** Image affichée en pixels ARGB (largeur videoW()), null sans image */
    external fun frame(): IntArray?
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
    /** Type de manette d'un port libretro (513 : manette CD32 d'Amiberry) */
    external fun portDevice(port: Int, device: Int)

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

    /** Dossier courant du cœur : il y cherche data/floppy_sounds */
    external fun setHome(path: String): Boolean
    /** Bruits des lecteurs de disquettes, à chaud */
    external fun floppySound(on: Boolean)

    /** MIDI du port série : sortie (l'Amiga joue) et entrée (un clavier joue sur l'Amiga) */
    external fun midiEnable(out: Boolean, input: Boolean)
    /** Octets envoyés par l'Amiga depuis le dernier appel */
    external fun midiRead(dst: ByteArray): Int
    external fun midiWrite(src: ByteArray, offset: Int, length: Int)
}
