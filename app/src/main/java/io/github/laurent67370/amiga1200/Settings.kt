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

    /** A1200 ou A500 */
    var model: String get() = str("model", "A1200"); set(v) = put("model", v)
    /** JIT 68040 (A1200 seulement) */
    var jit: Boolean get() = p.getBoolean("jit", true); set(v) = flag("jit", v)
    var ntsc: Boolean get() = p.getBoolean("ntsc", false); set(v) = flag("ntsc", v)
    /** « aros » (ROM libre fournie) ou « user » (Kickstart copié dans files/sys/kick.rom) */
    var rom: String get() = str("rom", "aros"); set(v) = put("rom", v)
    var romName: String get() = str("romName", ""); set(v) = put("romName", v)
    /** Disquette DF0: et disque dur DH0: : adresse du document choisi (lu sur place) et son nom */
    var df0: String get() = str("df0", ""); set(v) = put("df0", v)
    var df0Name: String get() = str("df0Name", ""); set(v) = put("df0Name", v)
    var df1: String get() = str("df1", ""); set(v) = put("df1", v)
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
    var rtg: Boolean get() = p.getBoolean("rtg", true); set(v) = flag("rtg", v)
    /** Manette tactile à l'écran (sinon l'écran tactile sert de souris) */
    var touchPad: Boolean get() = p.getBoolean("touchPad", false); set(v) = flag("touchPad", v)
    /** Image étirée sur toute la largeur (sinon 4:3) */
    var fill: Boolean get() = p.getBoolean("fill", false); set(v) = flag("fill", v)
    var showSpeed: Boolean get() = p.getBoolean("showSpeed", true); set(v) = flag("showSpeed", v)
}

/** Fichiers choisis par l'utilisateur (Storage Access Framework). */
object Disks {
    /** Descripteurs gardés ouverts tant que le processus vit : le cœur lit et écrit les disques sur place */
    private val open = mutableListOf<ParcelFileDescriptor>()

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

    fun write(ctx: Context, s: Settings, df0: String?, df1: String?, hds: List<String>, shared: File?): File {
        val sys = sysDir(ctx)
        val a500 = s.model == "A500"
        val l = mutableListOf("quickstart=${s.model},0")
        val kick = File(sys, "kick.rom")
        if (s.rom == "user" && kick.exists()) l += "kickstart_rom_file=${kick.path}"
        else {
            aros(ctx)
            l += "kickstart_rom_file=${File(sys, "aros-rom.bin").path}"
            l += "kickstart_ext_rom_file=${File(sys, "aros-ext.bin").path}"
            if (a500) l += "fastmem_size=2" // AROS ne tient pas dans la mémoire d'un A500
        }
        l += listOf("nr_floppies=2", "floppy1type=0")
        if (df0 != null) l += "floppy0=$df0"
        if (df1 != null) l += "floppy1=$df1"
        if (s.jit && !a500) {
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
        if (s.rtg && !a500) {
            l += listOf("gfxcard_type=ZorroIII", "gfxcard_size=32")
            if (!s.jit) l += listOf("cpu_model=68020", "cpu_24bit_addressing=false")
        }
        // Disques durs sur le pilote d'UAE (uaehf.device) : démarrent AGS et les Workbench préinstallés.
        // Les disques RDB gardent les noms et priorités de démarrage de leurs partitions.
        hds.forEachIndexed { i, path -> l += "hardfile2=rw,DH$i:$path,0,0,0,512,${if (i == 0) 0 else -1},,uae$i" }
        // SHARED: : jamais un disque de démarrage
        if (shared != null) l += "filesystem2=rw,DH12:SHARED:${shared.path},-128"
        val f = File(ctx.filesDir, "cfg/amiga.uae")
        f.parentFile?.mkdirs()
        f.writeText(l.joinToString("\n") + "\n")
        Native.log("Configuration :\n" + l.joinToString("\n"))
        return f
    }
}
