package io.github.laurent67370.amiga1200

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper

/** MIDI par android.media.midi, comme midi-puae.js de la version web avec Web MIDI.
 *  Sur l'Amiga, le MIDI passe par le port série (interface MIDI branchée dessus) : le cœur remet les octets envoyés,
 *  regroupés ici en messages complets pour un synthétiseur USB ou Bluetooth, ou une application de synthé d'Android.
 *  Dans l'autre sens, un clavier MIDI branché joue sur l'Amiga. */
class MidiLink(private val ctx: Context, private val s: Settings) {
    private val mm = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val devices = mutableListOf<MidiDevice>()
    @Volatile private var outPort: MidiInputPort? = null
    private var inPort: MidiOutputPort? = null
    @Volatile private var running = false

    /** Branche les appareils choisis ; [say] annonce le résultat */
    fun start(say: (String) -> Unit) {
        val mm = mm ?: run { say("MIDI : cet appareil ne gère pas le MIDI"); return }
        val all = list(mm)
        // Sortie : l'appareil choisi, sinon le premier qui reçoit (ports d'entrée) ; entrée : seulement si choisie
        val out = all.firstOrNull { it.inputPortCount > 0 && name(it) == s.midiOut } ?: all.firstOrNull { it.inputPortCount > 0 }
        val inp = if (s.midiIn.isEmpty()) null else all.firstOrNull { it.outputPortCount > 0 && name(it) == s.midiIn }
        if (out == null && inp == null) { say("MIDI : aucun instrument ni application MIDI trouvé"); return }
        running = true
        Native.midiEnable(out != null, inp != null)
        val ui = Handler(Looper.getMainLooper())
        out?.let { info ->
            mm.openDevice(info, { d ->
                if (d == null) { say("MIDI : ${name(info)} refuse la connexion"); return@openDevice }
                if (!running) { d.close(); return@openDevice }
                devices += d
                val port = info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.portNumber
                outPort = d.openInputPort(port)
                say("MIDI : l'Amiga joue sur ${name(info)}")
            }, ui)
        }
        inp?.let { info ->
            mm.openDevice(info, { d ->
                if (d == null || !running) { d?.close(); return@openDevice }
                devices += d
                val port = info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.portNumber
                inPort = d.openOutputPort(port)?.also { it.connect(receiver) }
            }, ui)
        }
        if (out != null) Thread({
            val buf = ByteArray(4096)
            val asm = Assembler { m, n -> try { outPort?.send(m, 0, n) } catch (_: Exception) {} }
            while (running) {
                val n = Native.midiRead(buf)
                for (i in 0 until n) asm.push(buf[i].toInt() and 0xff)
                if (n == 0) Thread.sleep(3)
            }
        }, "MIDI").start()
    }

    fun stop() {
        if (!running) return
        running = false
        // Toutes notes coupées : rien ne reste suspendu sur le synthétiseur
        try { outPort?.let { p -> for (c in 0 until 16) p.send(byteArrayOf((0xb0 or c).toByte(), 123, 0), 0, 3) } } catch (_: Exception) {}
        try { inPort?.disconnect(receiver) } catch (_: Exception) {}
        for (c in listOf(outPort, inPort)) try { c?.close() } catch (_: Exception) {}
        for (d in devices) try { d.close() } catch (_: Exception) {}
        devices.clear(); outPort = null; inPort = null
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) = Native.midiWrite(msg, offset, count)
    }

    /** Octets → messages MIDI complets (statut courant, messages système exclusifs, temps réel au milieu de tout) */
    private class Assembler(val send: (ByteArray, Int) -> Unit) {
        private var status = 0; private var need = 0
        private val data = ByteArray(2); private var n = 0
        private var sysex: java.io.ByteArrayOutputStream? = null

        private fun len(st: Int) = when {
            st < 0xc0 -> 2; st < 0xe0 -> 1; st < 0xf0 -> 2
            st == 0xf1 || st == 0xf3 -> 1; st == 0xf2 -> 2; else -> 0
        }
        private fun out(vararg b: Int) = send(ByteArray(b.size) { b[it].toByte() }, b.size)

        fun push(b: Int) {
            if (b >= 0xf8) { out(b); return }
            if (b == 0xf0) { sysex = java.io.ByteArrayOutputStream().apply { write(b) }; return }
            sysex?.let { x ->
                if (b == 0xf7) { x.write(b); val a = x.toByteArray(); send(a, a.size); sysex = null; return }
                if ((b and 0x80) == 0) { if (x.size() < 65536) x.write(b); return }
                sysex = null   // message système exclusif interrompu
            }
            if ((b and 0x80) != 0) {
                status = b; n = 0; need = len(b)
                if (need == 0) { if (b != 0xf7) out(b); if (b >= 0xf0) status = 0 }
                return
            }
            if (status == 0) return
            data[n++] = b.toByte()
            if (n == need) {
                if (need == 1) out(status, data[0].toInt() and 0xff) else out(status, data[0].toInt() and 0xff, data[1].toInt() and 0xff)
                n = 0
                if (status >= 0xf0) status = 0   // pas de statut courant pour les messages système
            }
        }
    }

    companion object {
        fun name(i: MidiDeviceInfo) = i.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI ${i.id}"

        @Suppress("DEPRECATION")
        private fun list(mm: MidiManager): List<MidiDeviceInfo> =
            if (Build.VERSION.SDK_INT >= 33) mm.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
            else mm.devices.toList()

        /** Noms des appareils qui reçoivent (sortie de l'Amiga) ou qui envoient (entrée) */
        fun names(ctx: Context, output: Boolean): List<String> {
            val mm = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return emptyList()
            return list(mm).filter { if (output) it.inputPortCount > 0 else it.outputPortCount > 0 }.map { name(it) }.distinct()
        }
    }
}
