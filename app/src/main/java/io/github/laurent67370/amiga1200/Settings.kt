package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Disque dur choisi : adresse du document (lu sur place) et son nom */
data class Hd(val uri: String, val name: String)

/** Réglages (partagés par l'écran des réglages et le processus de l'émulation, relus à chaque démarrage). */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("reglages", Context.MODE_PRIVATE)
    private fun str(k: String, d: String) = p.getString(k, d) ?: d
    private fun put(k: String, v: String?) { p.edit().putString(k, v).commit() }
    private fun flag(k: String, v: Boolean) { p.edit().putBoolean(k, v).commit() }

    /** A500, A600, A1200, A3000, A4000, CD32 ou CDTV */
    var model: String get() = str("model", "A1200"); set(v) { put("model", v); saveProfile() }
    /** JIT 68040 (A1200 seulement) */
    var jit: Boolean get() = p.getBoolean("jit", true); set(v) { flag("jit", v); saveProfile() }
    var ntsc: Boolean get() = p.getBoolean("ntsc", false); set(v) = flag("ntsc", v)
    /** Mémoire Chip en blocs de 512 Ko (0 : celle du modèle ; 1 = 512 Ko, 2 = 1 Mo, 4 = 2 Mo) */
    var chip: Int get() = p.getInt("chip", 0); set(v) { p.edit().putInt("chip", v).commit(); saveProfile() }
    /** Mémoire Slow (« trappe » de l'A500) en blocs de 256 Ko (-1 : celle du modèle ; 0, 2 = 512 Ko, 4 = 1 Mo) */
    var slow: Int get() = p.getInt("slow", -1); set(v) { p.edit().putInt("slow", v).commit(); saveProfile() }
    /** Mémoire Fast Zorro II en Mo (0, 2, 4, 8 ; pas avec le JIT, qui a déjà 64 Mo de Zorro III) */
    var fast: Int get() = p.getInt("fast", 0); set(v) { p.edit().putInt("fast", v).commit(); saveProfile() }
    /** Carte accélératrice sans JIT (A1200, A3000, A4000) : « none », « 030 » (68030/68882) ou « 040 » */
    var accel: String get() = str("accel", "none"); set(v) { put("accel", v); saveProfile() }
    /** « aros » (ROM libre fournie) ou « user » (Kickstart installés, choisi selon la machine : voir Kickstarts) */
    var rom: String get() = str("rom", "aros"); set(v) = put("rom", v)
    var romName: String get() = str("romName", ""); set(v) = put("romName", v)
    /** Disquette DF0: et disque dur DH0: : adresse du document choisi (lu sur place) et son nom */
    var df0: String get() = str("df0", ""); set(v) = put("df0", v)
    var df0Name: String get() = str("df0Name", ""); set(v) = put("df0Name", v)
    var df1: String get() = str("df1", ""); set(v) = put("df1", v)
    var df2: String get() = str("df2", ""); set(v) = put("df2", v)
    var df2Name: String get() = str("df2Name", ""); set(v) = put("df2Name", v)
    var df3: String get() = str("df3", ""); set(v) = put("df3", v)
    var df3Name: String get() = str("df3Name", ""); set(v) = put("df3Name", v)
    /** CD de la CD32 ou du CDTV (image .iso ou .chd, un seul fichier) */
    var cd: String get() = str("cd", ""); set(v) = put("cd", v)
    var cdName: String get() = str("cdName", ""); set(v) = put("cdName", v)
    /** Lecteur DFn: : adresse et nom */
    fun floppy(n: Int): Pair<String, String> = when (n) { 0 -> df0 to df0Name; 1 -> df1 to df1Name; 2 -> df2 to df2Name; else -> df3 to df3Name }
    fun setFloppy(n: Int, uri: String, name: String) {
        when (n) { 0 -> { df0 = uri; df0Name = name }; 1 -> { df1 = uri; df1Name = name }; 2 -> { df2 = uri; df2Name = name }; else -> { df3 = uri; df3Name = name } }
    }
    var df1Name: String get() = str("df1Name", ""); set(v) = put("df1Name", v)
    /** Libellés du clavier Amiga à l'écran en AZERTY (clavier français de l'Amiga) */
    var azerty: Boolean get() = p.getBoolean("azerty", false); set(v) = flag("azerty", v)
    var dh0: String get() = str("dh0", ""); set(v) = put("dh0", v)
    var dh0Name: String get() = str("dh0Name", ""); set(v) = put("dh0Name", v)
    /** Disques durs DH0: à DH7: (l'ancien réglage DH0: seul est repris) */
    var hds: List<Hd>
        get() {
            val j = p.getString("hds", null) ?: return if (dh0.isNotEmpty()) listOf(Hd(dh0, dh0Name)) else emptyList()
            val a = try { JSONArray(j) } catch (_: Exception) { return emptyList() }
            return (0 until a.length()).map { a.getJSONObject(it).let { o -> Hd(o.getString("uri"), o.getString("name")) } }
        }
        set(v) {
            val a = JSONArray()
            for (h in v) a.put(JSONObject().put("uri", h.uri).put("name", h.name))
            put("hds", a.toString())
        }
    /** Volume SHARED: (dossier de l'application) avec les disques durs, pour AGS et les sauvegardes WHDLoad */
    var shared: Boolean get() = p.getBoolean("shared", true); set(v) = flag("shared", v)
    /** Carte graphique RTG d'UAE (uaegfx, Picasso96) : écrans haute résolution d'AGS et du Workbench */
    var rtg: Boolean get() = p.getBoolean("rtg", true); set(v) { flag("rtg", v); saveProfile() }
    /** Manette tactile à l'écran (sinon l'écran tactile sert de souris) */
    var touchPad: Boolean get() = p.getBoolean("touchPad", false); set(v) = flag("touchPad", v)
    /** Image étirée sur toute la largeur (sinon 4:3) */
    var fill: Boolean get() = p.getBoolean("fill", false); set(v) = flag("fill", v)
    var showSpeed: Boolean get() = p.getBoolean("showSpeed", true); set(v) = flag("showSpeed", v)

    // ---------- image ----------
    /** Filtre : 0 lisse, 1 net (pixels francs), 2 écran cathodique (lignes de balayage) */
    var filter: Int get() = p.getInt("filter", 0); set(v) { p.edit().putInt("filter", v).commit() }
    /** Agrandissement en multiple entier des pixels de l'Amiga (pixels tous de la même taille) */
    var integer: Boolean get() = p.getBoolean("integer", false); set(v) = flag("integer", v)
    /** Cadrage d'un jeu : zoom (1 = image entière) et décalage vertical (fraction de la hauteur), pour les jeux qui
     *  n'occupent pas tout l'écran */
    fun zoom(game: String) = p.getFloat("zoom:$game", 1f)
    fun shiftY(game: String) = p.getFloat("decalage:$game", 0f)
    fun setFraming(game: String, zoom: Float, shiftY: Float) { p.edit().putFloat("zoom:$game", zoom).putFloat("decalage:$game", shiftY).apply() }

    // ---------- son et MIDI ----------
    /** Bruits des lecteurs de disquettes (clic de la tête, rotation), comme sur un vrai Amiga */
    var floppySound: Boolean get() = p.getBoolean("floppySound", true); set(v) = flag("floppySound", v)
    /** MIDI sur le port série de l'Amiga (Bars & Pipes, Music-X, OctaMED, jeux qui parlent MIDI) */
    var midi: Boolean get() = p.getBoolean("midi", false); set(v) = flag("midi", v)
    /** Appareil qui reçoit ce que joue l'Amiga (vide : le premier trouvé) */
    var midiOut: String get() = str("midiOut", ""); set(v) = put("midiOut", v)
    /** Clavier MIDI qui joue sur l'Amiga (vide : aucun) */
    var midiIn: String get() = str("midiIn", ""); set(v) = put("midiIn", v)

    // ---------- commandes ----------
    /** Vitesse du pointeur de la souris tactile (1 = le pointeur suit le doigt) */
    var mouseSpeed: Float get() = p.getFloat("mouseSpeed", 1f); set(v) { p.edit().putFloat("mouseSpeed", v).commit() }
    /** Taille de la manette tactile */
    var padSize: Float get() = p.getFloat("padSize", 1f); set(v) { p.edit().putFloat("padSize", v).commit() }
    /** Boutons gauche et droit de la souris à l'écran (souris tactile) */
    var mouseKeys: Boolean get() = p.getBoolean("mouseKeys", false); set(v) = flag("mouseKeys", v)
    /** Manette : tir 1 et tir 2 échangés */
    var swapFire: Boolean get() = p.getBoolean("swapFire", false); set(v) = flag("swapFire", v)
    /** Touche de sortie des jeux WHDLoad (bouton ⏏) : F10, * du pavé ou Échap */
    var quitKey: Int get() = p.getInt("quitKey", 291); set(v) { p.edit().putInt("quitKey", v).commit() }
    /** Manette : bouton B = haut (sauter dans les jeux de plate-forme) */
    var jumpB: Boolean get() = p.getBoolean("jumpB", false); set(v) = flag("jumpB", v)

    // ---------- réglages retenus par disque de démarrage ----------
    /** Disque qui démarre : la disquette DF0:, sinon le premier disque dur */
    fun bootKey() = if (Config.cdConsole(model)) cdName else df0Name.ifEmpty { hds.firstOrNull()?.name ?: "" }
    private fun saveProfile() {
        val k = bootKey()
        if (k.isNotEmpty()) p.edit().putString("profil:$k", "${str("model", "A1200")};${p.getBoolean("jit", true)};${p.getBoolean("rtg", true)};" +
            "${p.getInt("chip", 0)};${p.getInt("slow", -1)};${p.getInt("fast", 0)};${str("accel", "none")}").commit()
    }
    /** Reprend la machine retenue pour le disque de démarrage ; vrai si elle existait */
    fun applyProfile(): Boolean {
        val v = p.getString("profil:${bootKey()}", null)?.split(";") ?: return false
        if (v.size < 3) return false
        // Mémoire et processeur : retenus depuis cette version (les anciens profils reprennent ceux du modèle)
        p.edit().putString("model", v[0]).putBoolean("jit", v[1] == "true").putBoolean("rtg", v[2] == "true")
            .putInt("chip", v.getOrNull(3)?.toIntOrNull() ?: 0).putInt("slow", v.getOrNull(4)?.toIntOrNull() ?: -1)
            .putInt("fast", v.getOrNull(5)?.toIntOrNull() ?: 0).putString("accel", v.getOrNull(6) ?: "none").commit()
        return true
    }

    // ---------- configurations enregistrées ----------
    /** Machine et disques, pour une configuration enregistrée sous un nom */
    fun snapshot(): JSONObject {
        val o = JSONObject().put("model", model).put("jit", jit).put("rtg", rtg).put("ntsc", ntsc).put("shared", shared)
            .put("cd", cd).put("cdName", cdName)
        for (n in 0..3) { val (u, nm) = floppy(n); o.put("df$n", u).put("df${n}Name", nm) }
        val a = JSONArray(); for (h in hds) a.put(JSONObject().put("uri", h.uri).put("name", h.name))
        return o.put("hds", a)
    }
    fun restore(o: JSONObject) {
        p.edit().putString("model", o.optString("model", "A1200")).putBoolean("jit", o.optBoolean("jit", true))
            .putBoolean("rtg", o.optBoolean("rtg", true)).putBoolean("ntsc", o.optBoolean("ntsc", false))
            .putBoolean("shared", o.optBoolean("shared", true)).commit()
        cd = o.optString("cd"); cdName = o.optString("cdName")
        for (n in 0..3) setFloppy(n, o.optString("df$n"), o.optString("df${n}Name"))
        val a = o.optJSONArray("hds") ?: JSONArray()
        hds = (0 until a.length()).map { a.getJSONObject(it).let { h -> Hd(h.getString("uri"), h.getString("name")) } }
    }
    fun savedConfigs(): JSONObject = try { JSONObject(str("configs", "{}")) } catch (_: Exception) { JSONObject() }
    fun saveConfig(name: String) = put("configs", savedConfigs().put(name, snapshot()).toString())
    fun deleteConfig(name: String) = put("configs", savedConfigs().apply { remove(name) }.toString())

    // ---------- reprise où l'on en était ----------
    /** Signature de la configuration : la reprise n'est proposée qu'avec la même machine et les mêmes disques */
    fun signature() = listOf(model, jit, rtg, ntsc, rom, romName, df0, df1, df2, df3, cd, hds.joinToString("|") { it.uri },
        chip, slow, fast, accel).joinToString("#")
    var resumeSig: String get() = str("resumeSig", ""); set(v) = put("resumeSig", v)
    /** Jeu lancé directement dont l'état est gardé : « whd » ou « hdf », son document et son nom (lignes) ; vide pour
     *  la machine de l'accueil */
    var resumeGame: String get() = str("resumeGame", ""); set(v) = put("resumeGame", v)
}

/** Fichiers choisis par l'utilisateur (Storage Access Framework). */
object Disks {
    /** Descripteurs gardés ouverts tant que le processus vit : le cœur lit et écrit les disques sur place */
    private val open = mutableListOf<ParcelFileDescriptor>()
    /** Lien → document d'origine, pour relire un disque sans passer par le lien */
    val uriOf = HashMap<String, String>()

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "disque"
    }

    /** Nom sans caractère gênant pour la configuration (virgules des lignes hardfile2…) */
    fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._ +()-]"), "_").ifEmpty { "disque" }

    /** Document lu et écrit sur place, sans copie (un disque AGS de plusieurs Go comme une disquette) : un lien
     *  symbolique porte son vrai nom (le cœur reconnaît les formats à l'extension) et mène à son descripteur. */
    fun link(ctx: Context, uriText: String, name: String): String? {
        if (uriText.isEmpty()) return null
        val uri = Uri.parse(uriText)
        var mode = "rw"
        val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "rw") } catch (e: Exception) { Native.log("$name : écriture refusée ($e)"); null }
            ?: try { mode = "r"; ctx.contentResolver.openFileDescriptor(uri, "r") } catch (e: Exception) { Native.log("$name : lecture refusée ($e)"); null }
            ?: return null
        open += pfd
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val f = File(dir, safe(name))
        f.delete()
        val target = try { Os.readlink("/proc/self/fd/${pfd.fd}") } catch (_: Exception) { "?" }
        Native.log("$name : ouvert en $mode, ${pfd.statSize} octets, descripteur ${pfd.fd} → $target")
        uriOf[f.path] = uriText
        return try { Os.symlink("/proc/self/fd/${pfd.fd}", f.path); f.path } catch (e: Exception) { Native.log("$name : lien impossible ($e)"); null }
    }

    fun copy(ctx: Context, uri: Uri, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        ctx.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        true
    } catch (_: Exception) { false }
}

/** Configuration WinUAE écrite pour Amiberry (même principe que amberryLines() de la version web). */
object Config {
    fun sysDir(ctx: Context) = File(ctx.filesDir, "sys").apply { mkdirs() }
    fun saveDir(ctx: Context) = File(ctx.filesDir, "save").apply { mkdirs() }
    /** État gardé à l'extinction (« Reprendre où j'en étais »), utilisé une seule fois */
    fun resumeFile(ctx: Context) = File(saveDir(ctx), "reprise.uss")

    /** ROM AROS libres, copiées une fois depuis l'APK */
    private fun aros(ctx: Context) {
        for (n in listOf("aros-rom.bin", "aros-ext.bin")) {
            val f = File(sysDir(ctx), n)
            if (!f.exists()) ctx.assets.open("aros/$n").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    /** Dossier du volume SHARED: (SaveGames créé d'avance, comme dans la version web) : dans le dossier de
     *  l'application visible depuis un ordinateur (Android/data/…/files/SHARED) */
    fun sharedDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "SHARED")
        File(d, "SaveGames").mkdirs()
        return d
    }

    /** Noms des images de Kickstart pour WHDLoad (paquet skick), d'après la somme CRC32 */
    private val whdKickNames = mapOf(
        0xA6CE1636L to "kick33180.A500", 0xC4F0F55FL to "kick34005.A500", 0xC3BDB240L to "kick37175.A500",
        0xFC24AE0DL to "kick40063.A600", 0x6C9B07D2L to "kick39106.A1200", 0x1483A091L to "kick40068.A1200",
        0xD6BAE334L to "kick40068.A4000",
    )

    /** Dossier WHDCommon/Kickstarts : vos Kickstart et les tables de relogement (.RTB) d'Amiberry ; null sans Kickstart */
    fun whdCommon(ctx: Context): File? {
        val roms = Kickstarts.list(ctx).filter { whdKickNames.containsKey(it.crc) }
        if (roms.isEmpty()) return null
        val root = File(ctx.filesDir, "whdcommon"); val dir = File(root, "Kickstarts").apply { mkdirs() }
        for (r in roms) { val f = File(dir, whdKickNames.getValue(r.crc)); if (!f.exists() || f.length() != r.file.length()) r.file.copyTo(f, overwrite = true) }
        WhdBoot.ensure(ctx)
        File(saveDir(ctx), "WHDBoot/save-data/Kickstarts").listFiles { f -> f.name.endsWith(".RTB", true) }?.forEach { rtb ->
            val f = File(dir, rtb.name); if (!f.exists()) rtb.copyTo(f)
        }
        return root
    }

    /** Machines : A1200, A3000 et A4000 acceptent le JIT et la carte RTG ; la CD32 et le CDTV démarrent sur leur CD */
    fun bigBox(model: String) = model == "A1200" || model == "A3000" || model == "A4000"
    fun cdConsole(model: String) = model == "CD32" || model == "CDTV"

    /** Disque dur avec table de partitions (RDB, comme AGS) ; sinon l'image d'une seule partition, qui demande sa
     *  géométrie (32 secteurs, 1 tête, 2 blocs réservés, comme le cœur pour un .hdf lancé seul) */
    fun hasRdb(ctx: Context, path: String): Boolean {
        fun scan(i: java.io.InputStream): Boolean {
            val b = ByteArray(16 * 512); var n = 0
            while (n < b.size) { val r = i.read(b, n, b.size - n); if (r <= 0) break; n += r }
            return (0 until n / 512).any { k -> String(b, k * 512, 4, Charsets.ISO_8859_1) == "RDSK" }
        }
        // Lu par son document d'origine (le lien vers le descripteur ne se rouvre pas toujours), sinon par le lien
        val byUri: Boolean? = try { Disks.uriOf[path]?.let { u -> ctx.contentResolver.openInputStream(Uri.parse(u))?.use { scan(it) } } }
            catch (e: Exception) { Native.log("$path : lecture du début impossible ($e)"); null }
        val rdb: Boolean? = byUri ?: try { File(path).inputStream().use { scan(it) } }
            catch (e: Exception) { Native.log("$path : lecture du début impossible ($e)"); null }
        Native.log("${File(path).name} : " + when (rdb) { true -> "table de partitions (RDB)"; false -> "partition seule (32 secteurs, 1 tête, 2 blocs réservés)"; null -> "illisible, traité comme un disque RDB" })
        return rdb ?: true
    }

    /** [whdHdf] : jeu installé sur un .hdf qui démarre WHDLoad tout seul. Machine de WHDLoad, quelle que soit celle de
     *  l'accueil : A1200 (68020) avec son Kickstart 3.x et 8 Mo de Fast pour le Preload, sans JIT ni carte RTG */
    fun write(ctx: Context, s: Settings, floppies: List<String?>, hds: List<String>, shared: File?, cd: String?, whdHdf: Boolean = false): File {
        val sys = sysDir(ctx)
        val model = if (whdHdf) "A1200" else s.model
        val l = mutableListOf("quickstart=$model,0")
        if (model == "CD32") {
            // CD32 : la console telle qu'elle est, avec ses propres ROM (512 Ko + extension, ou ROM complète de 1 Mo)
            l += "kickstart_rom_file=${File(sys, "cd32.rom").path}"
            File(sys, "cd32ext.rom").takeIf { it.exists() }?.let { l += "kickstart_ext_rom_file=${it.path}" }
            if (cd != null) l += "cdimage0=$cd,image"
            l += listOf("ntsc=${s.ntsc}", "gfx_resolution=hires")
        } else if (model == "CDTV") {
            // CDTV : la console telle qu'elle est (68000, ECS, 1 Mo de Chip, sans lecteur de disquettes), son Kickstart
            // (1.3 de préférence) et son extension
            val kick = Kickstarts.best(ctx, "CDTV")
            s.romName = kick?.label ?: ""
            kick?.let { l += "kickstart_rom_file=${it.file.path}" }
            Kickstarts.cdtvExt(ctx)?.let { l += "kickstart_ext_rom_file=${it.file.path}" }
            if (cd != null) l += "cdimage0=$cd,image"
            l += listOf("ntsc=${s.ntsc}", "gfx_resolution=hires")
        } else {
            // Le Kickstart qui convient à cette machine parmi ceux installés ; sinon AROS
            val kick = if (s.rom == "user" || whdHdf) Kickstarts.best(ctx, model) else null
            if (!whdHdf) s.romName = kick?.label ?: ""
            if (kick != null) l += "kickstart_rom_file=${kick.file.path}"
            else {
                aros(ctx)
                l += "kickstart_rom_file=${File(sys, "aros-rom.bin").path}"
                l += "kickstart_ext_rom_file=${File(sys, "aros-ext.bin").path}"
                // AROS ne tient pas dans la mémoire d'un A500 ou d'un A600
                if (model == "A500") l += "fastmem_size=2"
                if (model == "A600") l += "fastmem_size=4"
            }
            // Quatre lecteurs si DF2: ou DF3: sont utilisés
            val n = if (floppies.drop(2).any { it != null }) 4 else 2
            l += "nr_floppies=$n"
            for (i in 1 until n) l += "floppy${i}type=0"
            // Bruits des lecteurs (échantillons d'Amiberry copiés dans files/data/floppy_sounds)
            if (s.floppySound) for (i in 0 until n) l += "floppy${i}sound=1"
            floppies.forEachIndexed { i, f -> if (f != null && i < n) l += "floppy$i=$f" }
            if (whdHdf) l += "fastmem_size=8"
            else {
                // Mémoire et processeur choisis (retenus pour le disque de démarrage)
                if (s.chip > 0) l += "chipmem_size=${s.chip}"
                if (s.slow >= 0) l += "bogomem_size=${s.slow}"
                // (AROS demande déjà 2 ou 4 Mo à l'A500 et à l'A600 : on garde la plus grande des deux)
                val have = l.filter { it.startsWith("fastmem_size=") }.maxOfOrNull { it.substringAfter('=').toIntOrNull() ?: 0 } ?: 0
                if (s.fast > have && !(s.jit && bigBox(model))) { l.removeAll { it.startsWith("fastmem_size=") }; l += "fastmem_size=${s.fast}" }
            }
            val accel = if (!whdHdf && bigBox(model) && !s.jit) s.accel else "none"
            when (accel) {
                "030" -> l += listOf("cpu_model=68030", "fpu_model=68882", "cpu_24bit_addressing=false", "cpu_multiplier=8")
                "040" -> l += listOf("cpu_model=68040", "fpu_model=68040", "cpu_24bit_addressing=false", "cpu_multiplier=8")
            }
            if (s.jit && bigBox(model) && !whdHdf) {
                // JIT : 68040 traduit en code ARM64, à pleine vitesse, avec 64 Mo de Fast Zorro III
                l += listOf(
                    "cpu_model=68040", "fpu_model=68040", "cpu_24bit_addressing=false",
                    "cpu_compatible=false", "cycle_exact=false", "cpu_memory_cycle_exact=false", "cpu_speed=max",
                    "cachesize=16384", "compfpu=true", "comp_constjump=true", "comp_flushmode=soft", "comp_nf=true",
                    "comp_trustbyte=direct", "comp_trustword=direct", "comp_trustlong=direct", "comp_trustnaddr=direct",
                    "z3mem_size=64"
                )
            } else l += listOf("cycle_exact=false", "cpu_compatible=false")
            l += "ntsc=${s.ntsc}"
            l += "gfx_resolution=hires"
            // Carte RTG sur le Zorro III : il lui faut un processeur 32 bits (le 68EC020 de l'A1200 n'adresse que 16 Mo)
            if (s.rtg && bigBox(model) && !whdHdf) {
                l += listOf("gfxcard_type=ZorroIII", "gfxcard_size=32")
                if (!s.jit && model == "A1200" && accel == "none") l += listOf("cpu_model=68020", "cpu_24bit_addressing=false")
            }
            // Disques durs sur le pilote d'UAE (uaehf.device) : démarrent AGS et les Workbench préinstallés.
            // Les disques RDB gardent les noms et priorités de démarrage de leurs partitions.
            hds.forEachIndexed { i, path ->
                val geo = if (hasRdb(ctx, path)) "0,0,0,512" else "32,1,2,512"
                l += "hardfile2=rw,DH$i:$path,$geo,${if (i == 0) 0 else -1},,uae$i"
            }
            // SHARED: : jamais un disque de démarrage
            if (shared != null) l += "filesystem2=rw,DH12:SHARED:${shared.path},-128"
            // Jeu WHDLoad sur .hdf : vos Kickstart sous les noms qu'attend WHDLoad, dans WHDCommon:Kickstarts (WHDLoad y
            // cherche après Devs:Kickstarts), pour les jeux qui démarrent sur une image de Kickstart
            if (whdHdf) whdCommon(ctx)?.let { l += "filesystem2=ro,DH11:WHDCommon:${it.path},-128" }
            // Sans disque dur, ni ROM de démarrage ni carte d'UAE, comme la version web : la machine d'origine, toute sa mémoire au jeu
            if (hds.isEmpty() && shared == null) l += listOf("boot_rom_uae=disabled", "uaeboard=disabled")
        }
        // MIDI du port série vers l'application (android.media.midi), comme la version web
        if (s.midi) {
            l += "amiberry.midiout_device_name=Libretro MIDI"
            if (s.midiIn.isNotEmpty()) l += "amiberry.midiin_device_name=Libretro MIDI"
        }
        val f = File(ctx.filesDir, "cfg/amiga.uae")
        f.parentFile?.mkdirs()
        f.writeText(l.joinToString("\n") + "\n")
        Native.log("Configuration :\n" + l.joinToString("\n"))
        return f
    }
}
