package io.github.laurent67370.amiga1200

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import java.io.File

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
    var dh0: String get() = str("dh0", ""); set(v) = put("dh0", v)
    var dh0Name: String get() = str("dh0Name", ""); set(v) = put("dh0Name", v)
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
        val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "rw") } catch (_: Exception) { null }
            ?: try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            ?: return null
        open += pfd
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val f = File(dir, safe(name))
        f.delete()
        return try { Os.symlink("/proc/self/fd/${pfd.fd}", f.path); f.path } catch (_: Exception) { null }
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

    fun write(ctx: Context, s: Settings, df0: String?, dh0: String?): File {
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
        // Disque dur sur le pilote d'UAE (uaehf.device) : démarre AGS et les Workbench préinstallés
        if (dh0 != null) l += "hardfile2=rw,DH0:$dh0,0,0,0,512,0,,uae0"
        val f = File(ctx.filesDir, "cfg/amiga.uae")
        f.parentFile?.mkdirs()
        f.writeText(l.joinToString("\n") + "\n")
        return f
    }
}
