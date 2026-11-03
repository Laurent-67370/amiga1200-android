package io.github.laurent67370.amiga1200

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** Partage d'un serveur SMB (NAS, Windows, Samba) : adresse (« nas.local » ou « 192.168.1.20:445 »), partage, compte.
 *  Utilisateur vide : invité. Sans rien d'Android, pour être essayé sur un ordinateur contre un vrai serveur. */
class SmbServer(val host: String, val share: String, val user: String, val password: String, val domain: String = "") {
    val hostName get() = host.substringBefore(':')
    val port get() = host.substringAfter(':', "").toIntOrNull() ?: 445
}

/** Emplacement d'un fichier : smb://serveur/partage/dossier/fichier.hdf (séparateurs « / », noms tels quels) */
class SmbLoc(val host: String, val share: String, val path: String) {
    val uri get() = "smb://$host/$share/$path"
    val name get() = path.substringAfterLast('/')

    companion object {
        fun isSmb(uri: String) = uri.startsWith("smb://", ignoreCase = true)
        fun parse(uri: String): SmbLoc? {
            if (!isSmb(uri)) return null
            val p = uri.substring(6).split('/').filter { it.isNotEmpty() }
            return if (p.size < 3) null else SmbLoc(p[0], p[1], p.drop(2).joinToString("/"))
        }
    }
}

/** Connexion à un partage, rouverte d'elle-même après une coupure (Wi-Fi perdu, NAS en veille) */
class SmbShare(val server: SmbServer) {
    private val client = SMBClient(SmbConfig.builder().withTimeout(30, TimeUnit.SECONDS).withSoTimeout(60, TimeUnit.SECONDS).build())
    private var conn: Connection? = null
    private var session: Session? = null
    private var disk: DiskShare? = null

    @Synchronized fun disk(): DiskShare {
        disk?.let { d -> if (d.isConnected && conn?.isConnected == true) return d }
        reset()
        val c = client.connect(server.hostName, server.port)
        val auth = if (server.user.isEmpty()) AuthenticationContext.guest()
            else AuthenticationContext(server.user, server.password.toCharArray(), server.domain.ifEmpty { null })
        val s = c.authenticate(auth)
        val d = s.connectShare(server.share) as? DiskShare ?: run { c.close(); throw IllegalStateException("« ${server.share} » n'est pas un partage de fichiers") }
        conn = c; session = s; disk = d
        return d
    }

    @Synchronized fun reset() {
        try { conn?.close(true) } catch (_: Exception) {}
        conn = null; session = null; disk = null
    }

    class Entry(val name: String, val dir: Boolean, val size: Long)

    /** Contenu d'un dossier (chemin « / », vide : racine du partage), dossiers d'abord ; fichiers cachés exclus */
    fun list(path: String): List<Entry> = retry {
        it.list(path.replace('/', '\\')).filter { e -> e.fileName != "." && e.fileName != ".." && !e.fileName.startsWith(".") }
            .map { e -> Entry(e.fileName, EnumWithValue.EnumUtils.isSet(e.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY), e.endOfFile) }
            .sortedWith(compareBy({ !it.dir }, { it.name.lowercase() }))
    }

    fun open(path: String, write: Boolean): File = retry {
        it.openFile(path.replace('/', '\\'),
            if (write) EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE) else EnumSet.of(AccessMask.GENERIC_READ),
            null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
    }

    /** Une seconde chance après une coupure : connexion rouverte */
    fun <T> retry(op: (DiskShare) -> T): T = try { op(disk()) } catch (e: Exception) { reset(); op(disk()) }
}

/** Fichier du partage lu par blocs gardés en mémoire (AGS lit beaucoup de petits morceaux de ses disques) et écrit
 *  aussitôt sur le serveur (rien ne reste à écrire si le réseau ou l'application s'arrête). */
class SmbBlockFile(private val share: SmbShare, private val path: String, val writable: Boolean) {
    private var file: File = share.open(path, writable)
    val size: Long = file.fileInformation.standardInformation.endOfFile
    private val cache = object : LinkedHashMap<Long, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > CACHE_BLOCKS
    }

    /** Opération sur le fichier, rouvert (connexion comprise) en cas d'échec */
    private fun <T> io(op: (File) -> T): T = try { op(file) } catch (e: Exception) {
        try { file.close() } catch (_: Exception) {}
        share.reset()
        file = share.open(path, writable)
        op(file)
    }

    private fun block(n: Long): ByteArray = cache[n] ?: run {
        val start = n * BLOCK
        val len = minOf(BLOCK.toLong(), size - start).toInt()
        val b = ByteArray(len)
        var got = 0
        while (got < len) {
            val r = io { it.read(b, start + got, got, len - got) }
            if (r <= 0) break
            got += r
        }
        if (got < len) throw java.io.IOException("lecture incomplète ($got/$len octets à $start)")
        cache[n] = b
        b
    }

    @Synchronized fun read(off: Long, len: Int, dst: ByteArray, dstOff: Int = 0): Int {
        if (off >= size) return 0
        val n = minOf(len.toLong(), size - off).toInt()
        var done = 0
        while (done < n) {
            val p = off + done
            val b = block(p / BLOCK)
            val inBlock = (p % BLOCK).toInt()
            val k = minOf(n - done, b.size - inBlock)
            System.arraycopy(b, inBlock, dst, dstOff + done, k)
            done += k
        }
        return n
    }

    @Synchronized fun write(off: Long, len: Int, src: ByteArray, srcOff: Int = 0): Int {
        if (!writable) throw java.io.IOException("disque ouvert en lecture seule")
        if (off + len > size) throw java.io.IOException("écriture au-delà de la fin du disque")
        io { it.write(src, off, srcOff, len) }
        // Blocs gardés mis à jour
        var done = 0
        while (done < len) {
            val p = off + done
            val inBlock = (p % BLOCK).toInt()
            val k = minOf(len - done, BLOCK - inBlock)
            cache[p / BLOCK]?.let { b -> System.arraycopy(src, srcOff + done, b, inBlock, minOf(k, b.size - inBlock)) }
            done += k
        }
        return len
    }

    @Synchronized fun flush() { io { it.flush() } }

    @Synchronized fun close() {
        try { file.close() } catch (_: Exception) {}
        cache.clear()
    }

    companion object {
        const val BLOCK = 64 * 1024
        const val CACHE_BLOCKS = 512   // 32 Mo par disque
    }
}
