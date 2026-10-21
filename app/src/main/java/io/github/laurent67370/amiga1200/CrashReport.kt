package io.github.laurent67370.amiga1200

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plantage du processus de l'émulation : Android en garde la trace (ApplicationExitInfo). Au retour sur l'écran des
 *  réglages, l'application la lit, y joint la fin du journal de la séance plantée et propose de la partager.
 *  Les noms de fonctions du cœur restent dans libamiga.so (symboles gardés à la compilation) : la pile d'appels du
 *  rapport dit où le cœur s'est arrêté. */
object CrashReport {
    fun check(a: Activity) {
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = a.getSharedPreferences("plantages", Context.MODE_PRIVATE)
        val seen = prefs.getLong("vu", 0L)
        val am = a.getSystemService(ActivityManager::class.java) ?: return
        val exits = try { am.getHistoricalProcessExitReasons(a.packageName, 0, 10) } catch (_: Exception) { return }
        val crash = exits.firstOrNull {
            it.processName.endsWith(":emu") && it.timestamp > seen &&
                (it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_CRASH ||
                    it.reason == ApplicationExitInfo.REASON_ANR)
        } ?: return
        prefs.edit().putLong("vu", exits.maxOf { it.timestamp }).apply()

        val sb = StringBuilder()
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(crash.timestamp))
        sb.append("Plantage de l'émulation le $date (${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL})\n")
        sb.append("Raison : ${crash.reason} ${crash.description ?: ""}\n\n")
        // Rapport natif (tombstone, format protobuf) : on n'en garde que les chaînes lisibles (signal, pile d'appels)
        try {
            crash.traceInputStream?.use { input ->
                val bytes = input.readBytes()
                val strings = Regex("[\\x20-\\x7e]{6,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }
                    .filter { !it.startsWith("/apex/") || it.contains("libc") }.take(400).toList()
                sb.append("— Rapport d'Android —\n").append(strings.joinToString("\n")).append("\n\n")
            }
        } catch (_: Exception) {}
        val log = try { File(a.filesDir, "journal.txt").readLines().takeLast(120) } catch (_: Exception) { emptyList() }
        sb.append("— Fin du journal de la séance —\n").append(log.joinToString("\n"))
        val report = sb.toString()
        File(a.filesDir, "plantage.txt").writeText(report)

        AlertDialog.Builder(a)
            .setTitle("L'émulation s'est arrêtée brutalement")
            .setMessage("Un rapport a été préparé ($date). Partagez-le pour aider à corriger le problème.")
            .setPositiveButton("Partager le rapport") { _, _ ->
                a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Plantage amiga1200-android")
                    putExtra(Intent.EXTRA_TEXT, report.takeLast(150_000))
                }, "Partager le rapport"))
            }
            .setNegativeButton("Fermer", null)
            .show()
    }
}
