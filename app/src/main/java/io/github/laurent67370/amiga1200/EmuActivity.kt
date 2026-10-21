package io.github.laurent67370.amiga1200

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.InputType
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** L'Amiga en plein écran. Tourne dans le processus « :emu » : quitter arrête ce processus (le cœur garde un état
 *  global), l'écran des réglages reste ouvert. */
class EmuActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var s: Settings
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
    private lateinit var info: TextView
    private lateinit var keys: KeyCatcher
    private var pad: TouchPad? = null
    private var kb: AmigaKeyboard? = null
    private lateinit var bar: android.widget.LinearLayout
    private lateinit var modeBtn: TextView
    private lateinit var pauseBtn: TextView
    /** Barre rapide estompée après quelques secondes sans toucher l'écran */
    private val dimBar = Runnable { bar.animate().alpha(0.3f).setDuration(400).start() }
    /** Lecteur (0 = DF0:, 1 = DF1:) du prochain document choisi */
    private var diskDrive = 0
    private val ui = Handler(Looper.getMainLooper())
    private var running = false
    private var paused = false
    private var turbo = false
    /** Manettes : boutons et directions des joueurs 1 (port joystick) et 2 (port souris) ; la manette tactile joue 1 */
    private val padKeys = IntArray(2)
    private val padAxes = IntArray(2)
    private var touchAxes = 0
    /** Manette physique → joueur, dans l'ordre où elles se manifestent */
    private val players = HashMap<Int, Int>()
    /** Boutons de souris : par les gestes et par les boutons à l'écran */
    private var mbGesture = 0
    private var mbKeys = 0
    private var mouseKeysView: View? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        s = Settings(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = object : FrameLayout(this) {
            // Image en 4:3 centrée, ou étirée sur tout l'écran (« Pleine largeur »)
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                super.onLayout(changed, l, t, r, b)
                // Clavier Amiga affiché : l'image prend la place restante au-dessus
                val w = r - l; val h = b - t - (kb?.layoutParams?.height ?: 0)
                var sw = w; var sh = h
                if (!s.fill) { if (w * 3 > h * 4) sw = h * 4 / 3 else sh = w * 3 / 4 }
                val x = (w - sw) / 2; val y = (h - sh) / 2
                screen.layout(x, y, x + sw, y + sh)
            }
        }
        root.setBackgroundColor(Color.BLACK)
        screen = SurfaceView(this)
        screen.holder.addCallback(this)
        root.addView(screen)

        keys = KeyCatcher(this)
        root.addView(keys, FrameLayout.LayoutParams(1, 1))

        info = TextView(this).apply {
            setTextColor(Color.WHITE); setShadowLayer(3f, 0f, 0f, Color.BLACK); textSize = 13f
            setPadding(dp(8), dp(4), dp(8), dp(4)); text = "Démarrage…"
        }
        root.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))

        // Barre d'accès rapide : clavier Amiga, souris ou manette, pause, sauvegarde et reprise rapides, menu
        fun barButton(t: String, f: (View) -> Unit) = TextView(this).apply {
            text = t; textSize = 22f; setTextColor(Color.WHITE); setShadowLayer(4f, 0f, 0f, Color.BLACK)
            setPadding(dp(11), dp(6), dp(11), dp(6))
            setOnClickListener { f(it) }
        }
        val menuBtn = barButton("☰") { menu(it) }
        modeBtn = barButton(if (s.touchPad) "🕹" else "🖱") { toggleTouchMode() }
        pauseBtn = barButton("⏸") { setPaused(!paused) }
        bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.argb(110, 0, 0, 0)) }
            addView(barButton("⌨") { toggleKeyboard() })
            addView(modeBtn)
            addView(pauseBtn)
            addView(barButton("⤓") { saveSlot(0) })
            addView(barButton("⤒") { loadSlot(0) })
            addView(menuBtn)
        }
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        root.tag = menuBtn
        ui.postDelayed(dimBar, 5000)

        root.setOnTouchListener(mouse)
        setTouchPad(s.touchPad)
        setContentView(root)
        immersive()
        boot()
    }

    // ---------- démarrage ----------
    private fun logFile() = File(filesDir, "journal.txt")

    private fun boot() {
        Native.setLogFile(logFile().path)
        Native.log("amiga1200-android ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}")
        val df0 = Disks.link(this, s.df0, s.df0Name)
        if (s.df0.isNotEmpty() && df0 == null) toast("Disquette ${s.df0Name} : accès refusé, choisissez-la à nouveau")
        val df1 = Disks.link(this, s.df1, "df1-${s.df1Name}")
        if (s.df1.isNotEmpty() && df1 == null) toast("Disquette ${s.df1Name} : accès refusé, choisissez-la à nouveau")
        val hds = s.hds.mapIndexedNotNull { i, h ->
            // Nom précédé du numéro : deux disques de même nom venant de dossiers différents restent distincts
            Disks.link(this, h.uri, "dh$i-${h.name}").also { if (it == null) toast("Disque dur ${h.name} : accès refusé, choisissez-le à nouveau") }
        }
        val shared = if (hds.isNotEmpty() && s.shared) Config.sharedDir(this) else null
        val cfg = Config.write(this, s, df0, df1, hds, shared)
        Native.setDirs(Config.sysDir(this).path, Config.saveDir(this).path)
        // Options du cœur : celles de la version web (cadrage automatique, pas de barre d'état, manette 0 au port joystick)
        Native.setOption("amiberry_crop_overscan", "enabled")
        Native.setOption("amiberry_statusline", "disabled")
        Native.setOption("amiberry_joyport_order", "21")
        if (!Native.start(cfg.path)) { fail("le fil de l'émulation n'a pas pu démarrer"); return }
        val t0 = System.currentTimeMillis()
        ui.post(object : Runnable {
            override fun run() {
                when (Native.state()) {
                    2 -> {
                        running = true; Native.frames(); Native.busyUs(); ui.postDelayed(stats, 1000)
                        if (intent.getBooleanExtra(EXTRA_RESUME, false)) ui.postDelayed({ resume() }, 1500)
                        else Config.resumeFile(this@EmuActivity).delete() // les disques vont changer : l'ancien état ne vaut plus
                    }
                    1 -> if (System.currentTimeMillis() - t0 < 30000) ui.postDelayed(this, 50) else fail("le cœur ne répond pas")
                    else -> fail("le cœur a refusé la configuration (Kickstart ou disque illisible ?)")
                }
            }
        })
    }

    private fun fail(why: String) {
        toast("Démarrage impossible : $why")
        quit()
    }

    /** Vitesse (images par seconde, pourcentage du temps réel) et charge (temps de calcul d'une image) */
    private val stats = object : Runnable {
        override fun run() {
            if (!running) return
            val n = Native.frames()
            val busy = Native.busyUs()
            val fps = Native.fps().takeIf { it > 0 } ?: 50.0
            if (s.showSpeed) {
                info.text = when {
                    paused -> "En pause"
                    turbo -> String.format(Locale.FRANCE, "Turbo : %d images/s (%.1f× le temps réel)%s", n, n / fps, if (s.jit && s.model == "A1200") " · JIT" else "")
                    else -> String.format(Locale.FRANCE, "%d images/s · %d %% · calcul %.1f ms/image%s", n, (n * 100 / fps).toInt(),
                        if (n > 0) busy / n / 1000.0 else 0.0, if (s.jit && s.model == "A1200") " · JIT" else "")
                }
            } else info.text = if (paused) "En pause" else ""
            ui.postDelayed(this, 1000)
        }
    }

    // ---------- affichage ----------
    override fun surfaceCreated(holder: SurfaceHolder) = Native.setSurface(holder.surface)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) = Native.setSurface(holder.surface)
    override fun surfaceDestroyed(holder: SurfaceHolder) = Native.setSurface(null)

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onPause() {
        super.onPause()
        Native.pause(true)
    }

    override fun onResume() {
        super.onResume()
        immersive()
        if (!paused) Native.pause(false)
    }

    // ---------- menu ----------
    private fun menu(anchor: View) {
        val m = PopupMenu(this, anchor)
        val mm = m.menu
        mm.add(0, 1, 0, if (paused) "▶  Reprendre" else "⏸  Pause")
        mm.add(0, 5, 0, "💾  Disquettes DF0: / DF1:")
        mm.add(0, 6, 0, "📁  Sauvegardes d'état")
        mm.add(0, 4, 0, if (s.touchPad) "🖱  Passer en souris tactile" else "🕹  Passer en manette tactile")
        mm.addSubMenu(0, 100, 0, "🕹  Commandes").apply {
            add(0, 3, 0, if (kb == null) "Afficher le clavier Amiga" else "Masquer le clavier Amiga")
            add(0, 14, 0, if (s.azerty) "Lettres en QWERTY" else "Lettres en AZERTY")
            add(0, 13, 0, "Clavier du téléphone")
            add(0, 16, 0, "Réglages des commandes…")
        }
        mm.addSubMenu(0, 101, 0, "🖥  Écran").apply {
            add(0, 8, 0, if (s.fill) "Image en 4:3" else "Image sur toute la largeur")
            add(0, 9, 0, if (s.showSpeed) "Masquer la vitesse" else "Afficher la vitesse")
        }
        mm.addSubMenu(0, 102, 0, "🔧  Avancé").apply {
            add(0, 2, 0, if (turbo) "Vitesse normale" else "Turbo (mesure de vitesse)")
            add(0, 12, 0, "Journal (diagnostic)")
            add(0, 15, 0, "Aide")
        }
        if (s.model == "A1200") mm.add(0, 17, 0, if (s.jit) "⚡  Redémarrer sans JIT (démos)" else "⚡  Redémarrer avec le JIT")
        mm.add(0, 10, 0, "↺  Réinitialiser l'Amiga")
        mm.add(0, 11, 0, "⏻  Éteindre")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> setPaused(!paused)
                2 -> { turbo = !turbo; Native.warp(if (turbo) 8 else 1); toastShort(if (turbo) "Turbo" else "Vitesse normale") }
                3 -> toggleKeyboard()
                13 -> keys.show()
                14 -> { s.azerty = !s.azerty; kb?.invalidate() }
                4 -> toggleTouchMode()
                5 -> disks()
                6 -> states()
                8 -> { s.fill = !s.fill; root.requestLayout() }
                9 -> { s.showSpeed = !s.showSpeed }
                10 -> confirm("Réinitialiser l'Amiga ?", "L'Amiga redémarre à froid avec les mêmes disques. Ce qui n'est pas enregistré dans l'Amiga est perdu.") { restart() }
                11 -> confirm("Éteindre l'Amiga ?", "L'Amiga est gardé tel quel : l'accueil proposera « Reprendre où j'en étais ».") { powerOff() }
                12 -> journal()
                15 -> help()
                16 -> ControlsDialog.show(this, s) { applyControls() }
                17 -> {
                    s.jit = !s.jit  // retenu pour ce disque de démarrage
                    toastShort(if (s.jit) "Redémarrage avec le JIT" else "Redémarrage sans JIT : 68020 d'origine")
                    restart()
                }
            }
            true
        }
        m.show()
    }

    private fun setPaused(p: Boolean) {
        paused = p
        Native.pause(p)
        pauseBtn.text = if (p) "▶" else "⏸"
    }

    private fun toggleTouchMode() {
        s.touchPad = !s.touchPad
        setTouchPad(s.touchPad)
        modeBtn.text = if (s.touchPad) "🕹" else "🖱"
        toastShort(if (s.touchPad) "Manette tactile : joystick à gauche, tir à droite" else "Souris tactile : glisser, toucher pour cliquer")
    }

    private fun confirm(title: String, msg: String, ok: () -> Unit) {
        android.app.AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("Oui") { _, _ -> ok() }.setNegativeButton("Annuler", null).show()
    }

    private fun help() {
        android.app.AlertDialog.Builder(this).setTitle("Commandes")
            .setMessage("""
                ⌨  clavier Amiga (F1-F10, Help, Del, touches Amiga, flèches). Ctrl, Maj, Alt et Amiga : un toucher pour la touche suivante, deux pour les bloquer.
                🖱 / 🕹  souris tactile ou manette tactile
                ⏸  pause
                ⤓ / ⤒  sauvegarde et reprise rapides (emplacement 1)
                ☰  menu complet (ou bouton Retour, ou F12)

                Souris tactile : glisser pour déplacer le pointeur, toucher pour cliquer, toucher à deux doigts pour le clic droit, appui long puis glisser pour déplacer une fenêtre.
                Manette tactile : le joystick part de l'endroit où le doigt se pose (moitié gauche) ; bouton de tir en bas à droite, 2e bouton au-dessus.
                Manettes et claviers Bluetooth ou USB : reconnus directement.
            """.trimIndent())
            .setPositiveButton("Compris", null).show()
    }

    /** Toucher l'écran rallume la barre rapide */
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            ui.removeCallbacks(dimBar)
            bar.animate().cancel(); bar.alpha = 1f
            ui.postDelayed(dimBar, 5000)
        }
        return super.dispatchTouchEvent(e)
    }

    /** Journal : d'abord les lignes utiles (disques, ROM, erreurs), puis la fin ; « Partager » envoie tout */
    private fun journal() {
        val all = try { logFile().readText() } catch (_: Exception) { "" }
        val lines = all.lines()
        val key = Regex("(?i)hfd|hardfile|hdf|dh0|uaehf|filesys|rdb|rdsk|partition|kick|rom|disk|floppy|adf|error|erreur|fail|cannot|refus|denied|ouvert|lien|configuration")
        val important = lines.filter { key.containsMatchIn(it) }.takeLast(150)
        val text = "— Lignes utiles —\n" + important.joinToString("\n") + "\n\n— Fin du journal —\n" + lines.takeLast(80).joinToString("\n")
        val tv = TextView(this).apply { this.text = text; textSize = 11f; setTextIsSelectable(true); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Journal du cœur")
            .setView(android.widget.ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Partager") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Journal amiga1200-android")
                    putExtra(Intent.EXTRA_TEXT, all.takeLast(200_000))
                }, "Partager le journal"))
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    // ---------- clavier Amiga ----------
    private fun kbHeight(): Int {
        val m = resources.displayMetrics
        return if (m.widthPixels > m.heightPixels) (m.heightPixels * 0.46f).toInt() else (m.widthPixels * 0.62f).toInt()
    }

    private fun toggleKeyboard() {
        kb?.let { it.releaseAll(); root.removeView(it); kb = null; root.requestLayout(); return }
        kb = AmigaKeyboard(this) { s.azerty }.also {
            root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kbHeight(), Gravity.BOTTOM))
        }
        root.requestLayout()
    }

    // ---------- sauvegardes d'état : quatre emplacements par machine ----------
    private fun statePath(slot: Int) = File(Config.saveDir(this), "etat-${s.model}-${slot + 1}.uss")

    private fun saveSlot(slot: Int) = toast(if (Native.saveState(statePath(slot).path)) "État ${slot + 1} sauvegardé" else "Sauvegarde impossible")
    private fun loadSlot(slot: Int) {
        val f = statePath(slot)
        toast(if (f.exists() && Native.loadState(f.path)) "État ${slot + 1} repris" else "Aucun état ${slot + 1} à reprendre")
    }

    private fun states() {
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        val labels = (0 until 4).map { i ->
            val f = statePath(i)
            "Emplacement ${i + 1} : " + if (f.exists()) fmt.format(java.util.Date(f.lastModified())) else "vide"
        }.toTypedArray()
        var chosen = 0
        android.app.AlertDialog.Builder(this)
            .setTitle("Sauvegardes d'état (⤓ ⤒ : emplacement 1)")
            .setSingleChoiceItems(labels, 0) { _, w -> chosen = w }
            .setPositiveButton("Sauvegarder") { _, _ -> saveSlot(chosen) }
            .setNeutralButton("Reprendre") { _, _ -> loadSlot(chosen) }
            .setNegativeButton("Fermer", null)
            .show()
    }

    // ---------- disquettes ----------
    private fun disks() {
        val names = arrayOf("DF0: " + s.df0Name.ifEmpty { "vide" }, "DF1: " + s.df1Name.ifEmpty { "vide" })
        var chosen = 0
        android.app.AlertDialog.Builder(this)
            .setTitle("Disquettes")
            .setSingleChoiceItems(names, 0) { _, w -> chosen = w }
            .setPositiveButton("Insérer…") { _, _ -> diskDrive = chosen; pickDisk() }
            .setNeutralButton("Éjecter") { _, _ ->
                Native.insertDisk(chosen, "")
                if (chosen == 0) { s.df0 = ""; s.df0Name = "" } else { s.df1 = ""; s.df1Name = "" }
                toast("DF$chosen: éjectée")
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun pickDisk() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, 1)
    }

    @Deprecated("API Activity simple, sans AndroidX")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: Exception) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
        val name = Disks.displayName(this, uri)
        val d = diskDrive
        val path = Disks.link(this, uri.toString(), if (d == 0) name else "df1-$name")
        if (path == null) { toast("Lecture de $name impossible"); return }
        if (d == 0) { s.df0 = uri.toString(); s.df0Name = name } else { s.df1 = uri.toString(); s.df1Name = name }
        Native.insertDisk(d, path)
        toast("DF$d: $name")
    }

    /** Réinitialisation : le cœur avec JIT plante en réinstallant sa mémoire (réinitialisation interne), on rallume donc
     *  l'Amiga à froid dans un nouveau processus, comme à l'allumage. L'écran des réglages relance l'émulation. */
    private fun restart() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_RESTART, true)
        })
        quit()
    }

    /** Extinction : l'état est gardé pour « Reprendre où j'en étais » (une seule fois, avec les mêmes disques) */
    private fun powerOff() {
        if (!running) { quit(); return }
        setPaused(true)
        toastShort("Extinction…")
        Thread {
            val ok = Native.saveState(Config.resumeFile(this).path)
            ui.post { if (ok) s.resumeSig = s.signature(); quit() }
        }.start()
    }

    private fun resume() {
        val f = Config.resumeFile(this)
        val ok = f.exists() && Native.loadState(f.path)
        f.delete()
        toast(if (ok) "Reprise où vous en étiez" else "Reprise impossible : l'Amiga redémarre normalement")
    }

    private fun quit() {
        running = false
        Native.stop()
        finish()
        // Le cœur ne se rallume pas dans le même processus : on le termine (après l'écriture des disques)
        ui.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
    }

    // ---------- souris tactile ----------
    private val mouse = object : View.OnTouchListener {
        var lastX = 0f; var lastY = 0f; var startX = 0f; var startY = 0f; var accX = 0f; var accY = 0f
        var downAt = 0L; var moved = false; var drag = false; var twoFingers = false
        val longPress = Runnable { if (!moved && !twoFingers) { drag = true; mbGesture = 1; sendMouseButtons() } }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            // Déplacement en pixels de l'Amiga : le pointeur suit le doigt à la même vitesse sur l'image
            val scale = s.mouseSpeed * if (screen.width > 0 && Native.videoW() > 0) Native.videoW().toFloat() / screen.width else 1f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y; startX = e.x; startY = e.y; accX = 0f; accY = 0f
                    downAt = e.eventTime; moved = false; drag = false; twoFingers = false
                    ui.postDelayed(longPress, 400)
                }
                MotionEvent.ACTION_POINTER_DOWN -> { twoFingers = true; ui.removeCallbacks(longPress) }
                MotionEvent.ACTION_MOVE -> if (!twoFingers) {
                    if (!moved && abs(e.x - startX) + abs(e.y - startY) > dp(10)) moved = true
                    accX += (e.x - lastX) * scale; accY += (e.y - lastY) * scale
                    lastX = e.x; lastY = e.y
                    val dx = accX.toInt(); val dy = accY.toInt()
                    if (dx != 0 || dy != 0) { Native.mouseMove(dx, dy); accX -= dx; accY -= dy }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longPress)
                    when {
                        drag -> { mbGesture = 0; sendMouseButtons() }
                        twoFingers -> click(2)
                        !moved && e.eventTime - downAt < 300 -> click(1)
                    }
                }
            }
            return true
        }
    }

    private fun click(button: Int) {
        mbGesture = button; sendMouseButtons()
        ui.postDelayed({ mbGesture = 0; sendMouseButtons() }, 60)
    }

    private fun sendMouseButtons() = Native.mouseButtons(mbGesture or mbKeys)

    /** Boutons gauche et droit de la souris en bas de l'écran (souris tactile) */
    private fun setMouseKeys(on: Boolean) {
        mouseKeysView?.let { root.removeView(it) }
        mouseKeysView = null
        mbKeys = 0
        if (!on) return
        fun key(label: String, bit: Int, gravity: Int) = TextView(this).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE); this.gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.argb(90, 255, 255, 255)) }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { mbKeys = mbKeys or bit; v.alpha = 0.6f }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { mbKeys = mbKeys and bit.inv(); v.alpha = 1f }
                }
                sendMouseButtons(); true
            }
            layoutParams = FrameLayout.LayoutParams(dp(76), dp(64), Gravity.BOTTOM or gravity).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
        }
        val holder = FrameLayout(this)
        holder.addView(key("G", 1, Gravity.START))
        holder.addView(key("D", 2, Gravity.END))
        holder.isClickable = false
        // Seuls les deux boutons captent le toucher : le reste de l'écran reste la souris
        mouseKeysView = holder
        root.addView(holder, 2, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Réglages des commandes modifiés : appliqués tout de suite */
    private fun applyControls() {
        setTouchPad(s.touchPad)
    }

    private fun setTouchPad(on: Boolean) {
        pad?.let { root.removeView(it) }
        pad = null
        touchAxes = 0
        if (on) {
            pad = TouchPad(this, s.padSize).also {
                root.addView(it, 2, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        setMouseKeys(!on && s.mouseKeys)
        sendJoy(0)
    }

    private fun sendJoy(player: Int) = Native.joy(player, padKeys[player] or padAxes[player] or (if (player == 0) touchAxes else 0))

    /** Joueur d'une manette physique : la première vue joue 1, la deuxième joue 2 */
    private fun playerOf(deviceId: Int): Int = players.getOrPut(deviceId) { minOf(players.size, 1) }

    /** Bouton de manette → bit du joystick, selon les réglages (tir échangés, B = haut) */
    private fun padBit(keyCode: Int): Int {
        if (s.jumpB && keyCode == KeyEvent.KEYCODE_BUTTON_B) return 1 shl 4
        val b = Keymap.padBit(keyCode)
        if (!s.swapFire) return b
        return when (b) { 1 shl 0 -> 1 shl 8; 1 shl 8 -> 1 shl 0; else -> b }
    }

    // ---------- clavier et manettes ----------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        // Retour, ou F12 comme dans la version web : le menu
        if (e.keyCode == KeyEvent.KEYCODE_BACK || e.keyCode == KeyEvent.KEYCODE_F12) {
            if (!down) menu(root.tag as View)
            return true
        }
        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e)
        val fromPad = e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val bit = if (fromPad || KeyEvent.isGamepadButton(e.keyCode)) padBit(e.keyCode) else 0
        if (bit != 0) {
            if (e.repeatCount == 0) {
                val pl = playerOf(e.deviceId)
                padKeys[pl] = if (down) padKeys[pl] or bit else padKeys[pl] and bit.inv()
                sendJoy(pl)
            }
            return true
        }
        val k = Keymap.retrok(e.keyCode)
        if (k != 0) {
            if (e.repeatCount == 0) Native.key(down, k)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.isFromSource(InputDevice.SOURCE_JOYSTICK) && e.actionMasked == MotionEvent.ACTION_MOVE) {
            val x = e.getAxisValue(MotionEvent.AXIS_X) + e.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = e.getAxisValue(MotionEvent.AXIS_Y) + e.getAxisValue(MotionEvent.AXIS_HAT_Y)
            var m = 0
            if (y < -0.5f) m = m or (1 shl 4)
            if (y > 0.5f) m = m or (1 shl 5)
            if (x < -0.5f) m = m or (1 shl 6)
            if (x > 0.5f) m = m or (1 shl 7)
            val pl = playerOf(e.deviceId)
            if (m != padAxes[pl]) { padAxes[pl] = m; sendJoy(pl) }
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    /** Joystick tactile : touché par TouchPad */
    fun touchJoy(mask: Int) {
        touchAxes = mask
        sendJoy(0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_RESUME = "reprendre"
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun toastShort(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}

/** Vue invisible qui reçoit le clavier virtuel : touches spéciales en événements, lettres en texte */
class KeyCatcher(ctx: Context) : View(ctx) {
    init { isFocusable = true; isFocusableInTouchMode = true }

    fun show() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, 0)
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(out: EditorInfo): InputConnection {
        out.inputType = InputType.TYPE_NULL
        out.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, pos: Int): Boolean {
                for (c in text) {
                    val (k, shift) = Keymap.forChar(c) ?: continue
                    if (shift) Native.key(true, 304)
                    Native.key(true, k); Native.key(false, k)
                    if (shift) Native.key(false, 304)
                }
                return true
            }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                repeat(maxOf(before, 1)) { Native.key(true, 8); Native.key(false, 8) }
                return true
            }
        }
    }
}
