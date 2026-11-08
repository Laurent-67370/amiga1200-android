package io.github.laurent67370.amiga1200

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Base64
import com.hierynomus.mssmb2.SMBApiException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Disques d'un NAS (partage SMB) lus et écrits sur place : chaque fichier est présenté au cœur comme un fichier
 *  ordinaire (descripteur « proxy » d'Android), dont les lectures et écritures passent par le réseau (SmbCore.kt). */
object Smb {
    private val shares = HashMap<String, SmbShare>()

    init {
        // Cache des disques du NAS : au plus un quart de la mémoire de l'application
        SmbBlockFile.cacheBlocks = (Runtime.getRuntime().maxMemory() / 4 / SmbBlockFile.BLOCK).toInt().coerceIn(256, 1536)
    }

    // ---------- réglages du serveur, mot de passe chiffré par une clé du Keystore d'Android ----------
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nas", Context.MODE_PRIVATE)
    private const val KEY = "amiga1200-nas"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    private fun encrypt(text: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String = try {
        val (iv, data) = s.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(data))
    } catch (_: Exception) { "" }

    fun load(ctx: Context): SmbServer? {
        val p = prefs(ctx)
        val host = p.getString("host", "") ?: ""
        if (host.isEmpty()) return null
        return SmbServer(host, p.getString("share", "") ?: "", p.getString("user", "") ?: "",
            p.getString("pass", null)?.let { decrypt(it) } ?: "", p.getString("domain", "") ?: "")
    }

    fun save(ctx: Context, s: SmbServer) {
        prefs(ctx).edit().putString("host", s.host).putString("share", s.share).putString("user", s.user)
            .putString("pass", if (s.password.isEmpty()) null else encrypt(s.password)).putString("domain", s.domain).commit()
        synchronized(shares) { shares.values.forEach { it.reset() }; shares.clear() }
    }

    /** Connexion au partage d'un emplacement (le compte enregistré sert pour tous les partages du serveur) */
    fun share(ctx: Context, host: String, share: String): SmbShare = synchronized(shares) {
        shares.getOrPut("$host/$share".lowercase()) {
            val s = load(ctx)
            SmbShare(SmbServer(host, share, s?.user ?: "", s?.password ?: "", s?.domain ?: ""))
        }
    }

    /** Le réseau est interdit sur le fil de l'interface : le travail passe par un autre fil, attendu */
    fun <T> offMain(op: () -> T): T {
        if (Looper.myLooper() != Looper.getMainLooper()) return op()
        var r: Result<T>? = null
        val t = Thread { r = runCatching(op) }
        t.start(); t.join()
        return r!!.getOrThrow()
    }

    /** Message lisible pour une erreur du réseau */
    fun why(e: Throwable): String {
        val m = (e as? SMBApiException)?.status?.name ?: ""
        return when {
            m.contains("LOGON_FAILURE") || m.contains("ACCESS_DENIED") -> "identifiant ou mot de passe refusé"
            m.contains("BAD_NETWORK_NAME") -> "partage introuvable sur ce serveur"
            m.contains("OBJECT_NAME_NOT_FOUND") || m.contains("OBJECT_PATH_NOT_FOUND") -> "fichier ou dossier introuvable"
            m.contains("SHARING_VIOLATION") -> "fichier déjà ouvert par un autre appareil"
            e is java.net.UnknownHostException -> "serveur introuvable (adresse ?)"
            e is java.net.ConnectException || e is java.net.SocketTimeoutException || e.cause is java.net.ConnectException -> "serveur injoignable (adresse, Wi-Fi, NAS allumé ?)"
            else -> e.message?.take(160) ?: e.javaClass.simpleName
        }
    }

    // ---------- fichiers ----------
    /** Descripteur d'un fichier du NAS (null en cas d'échec, motif dans le journal) */
    fun open(ctx: Context, uri: String, write: Boolean): ParcelFileDescriptor? {
        val loc = SmbLoc.parse(uri) ?: return null
        return try {
            val f = offMain { SmbBlockFile(share(ctx, loc.host, loc.share), loc.path, write) }
            val th = HandlerThread("smb-" + loc.name).apply { start() }
            val sm = ctx.getSystemService(StorageManager::class.java)!!
            sm.openProxyFileDescriptor(if (write) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY,
                Callback(f, loc.name, th), Handler(th.looper))
        } catch (e: Exception) {
            Native.log("${loc.name} (NAS) : ${if (write) "écriture" else "lecture"} impossible : ${why(e)}")
            null
        }
    }

    private class Callback(val f: SmbBlockFile, val name: String, val th: HandlerThread) : ProxyFileDescriptorCallback() {
        override fun onGetSize() = f.size
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try { f.read(offset, size, data) }
            catch (e: Exception) { Native.log("$name (NAS) : lecture à $offset impossible : ${why(e)}"); throw ErrnoException("read", OsConstants.EIO) }
        override fun onWrite(offset: Long, size: Int, data: ByteArray): Int = try { f.write(offset, size, data) }
            catch (e: Exception) { Native.log("$name (NAS) : écriture à $offset impossible : ${why(e)}"); throw ErrnoException("write", OsConstants.EIO) }
        override fun onFsync() = try { f.flush() } catch (e: Exception) { throw ErrnoException("fsync", OsConstants.EIO) }
        override fun onRelease() { f.close(); th.quitSafely() }
    }

    /** Début d'un fichier (table de partitions d'un disque dur) */
    fun head(ctx: Context, uri: String, n: Int): ByteArray? {
        val loc = SmbLoc.parse(uri) ?: return null
        return try {
            offMain {
                val f = SmbBlockFile(share(ctx, loc.host, loc.share), loc.path, false)
                try { ByteArray(minOf(n.toLong(), f.size).toInt()).also { f.read(0, it.size, it) } } finally { f.close() }
            }
        } catch (e: Exception) { Native.log("${loc.name} (NAS) : lecture impossible : ${why(e)}"); null }
    }
}
