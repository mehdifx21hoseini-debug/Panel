package app.fitos.core.data.backup

import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupException
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Password encryption for backup files (docs/backup-format.md):
 * PBKDF2-HMAC-SHA256 key, AES-256-GCM in 64 KiB chunks so large files never sit in memory.
 * Each chunk is authenticated together with the header, its index and a "last chunk" flag, so
 * any change, reordering or truncation is detected. A lost password cannot be recovered.
 */
internal object BackupCrypto {
    val MAGIC: ByteArray = "FITOSENC".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 1
    const val KDF_PBKDF2_SHA256: Byte = 1
    const val DEFAULT_ITERATIONS = 310_000
    const val CHUNK_SIZE = 64 * 1024
    const val SALT_SIZE = 16
    const val NONCE_PREFIX_SIZE = 4
    const val TAG_BITS = 128
    const val TAG_SIZE = TAG_BITS / 8
    const val LAST_CHUNK = 0x80000000.toInt()

    /** magic 8 · version 1 · kdf 1 · iterations 4 · salt 16 · nonce prefix 4 · chunk size 4 */
    const val HEADER_SIZE = 8 + 1 + 1 + 4 + SALT_SIZE + NONCE_PREFIX_SIZE + 4

    private val ITERATION_RANGE = 10_000..10_000_000

    fun startsWithMagic(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun nonce(prefix: ByteArray, index: Long): ByteArray = ByteBuffer.allocate(12).put(prefix).putLong(index).array()

    fun aad(header: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 9).put(header).putLong(index).put(if (last) 1 else 0).array()

    fun validIterations(n: Int) = n in ITERATION_RANGE
}

/** Encrypts everything written to it; [close] writes the authenticated last chunk. */
internal class EncryptingOutputStream(
    private val out: OutputStream,
    password: CharArray,
    iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    random: SecureRandom = SecureRandom(),
) : OutputStream() {
    private val prefix = ByteArray(BackupCrypto.NONCE_PREFIX_SIZE).also(random::nextBytes)
    private val header: ByteArray
    private val key: SecretKey
    private val buffer = ByteArray(BackupCrypto.CHUNK_SIZE)
    private var filled = 0
    private var index = 0L
    private var closed = false

    init {
        val salt = ByteArray(BackupCrypto.SALT_SIZE).also(random::nextBytes)
        header = ByteBuffer.allocate(BackupCrypto.HEADER_SIZE)
            .put(BackupCrypto.MAGIC)
            .put(BackupCrypto.VERSION)
            .put(BackupCrypto.KDF_PBKDF2_SHA256)
            .putInt(iterations)
            .put(salt)
            .put(prefix)
            .putInt(BackupCrypto.CHUNK_SIZE)
            .array()
        key = BackupCrypto.deriveKey(password, salt, iterations)
        out.write(header)
    }

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "stream closed" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is only sealed once more data arrives, so the true last chunk gets the flag.
            if (filled == buffer.size) seal(last = false)
            val n = min(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, n)
            filled += n
            offset += n
            remaining -= n
        }
    }

    private fun seal(last: Boolean) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupCrypto.TAG_BITS, BackupCrypto.nonce(prefix, index)))
        cipher.updateAAD(BackupCrypto.aad(header, index, last))
        val sealed = cipher.doFinal(buffer, 0, filled)
        out.write(ByteBuffer.allocate(4).putInt(if (last) sealed.size or BackupCrypto.LAST_CHUNK else sealed.size).array())
        out.write(sealed)
        index++
        filled = 0
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        seal(last = true)
        closed = true
        out.close()
    }
}

/** Decrypts a stream written by [EncryptingOutputStream], failing loudly on any mismatch. */
internal class DecryptingInputStream(private val input: InputStream, password: CharArray) : InputStream() {
    private val header = ByteArray(BackupCrypto.HEADER_SIZE)
    private val prefix: ByteArray
    private val chunkSize: Int
    private val key: SecretKey
    private var plain = ByteArray(0)
    private var position = 0
    private var index = 0L
    private var finished = false

    init {
        if (!readFully(header)) throw BackupException(BackupError.NOT_A_BACKUP)
        val buf = ByteBuffer.wrap(header)
        val magic = ByteArray(BackupCrypto.MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(BackupCrypto.MAGIC)) throw BackupException(BackupError.NOT_A_BACKUP)
        val version = buf.get()
        val kdf = buf.get()
        val iterations = buf.int
        val salt = ByteArray(BackupCrypto.SALT_SIZE).also { buf.get(it) }
        prefix = ByteArray(BackupCrypto.NONCE_PREFIX_SIZE).also { buf.get(it) }
        chunkSize = buf.int
        if (version > BackupCrypto.VERSION) throw BackupException(BackupError.NEWER_APP)
        if (version < 1 || kdf != BackupCrypto.KDF_PBKDF2_SHA256 || !BackupCrypto.validIterations(iterations) || chunkSize !in 1024..(4 shl 20)) {
            throw BackupException(BackupError.DAMAGED)
        }
        key = BackupCrypto.deriveKey(password, salt, iterations)
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position == plain.size) {
            if (finished) return -1
            openNextChunk()
        }
        val n = min(len, plain.size - position)
        System.arraycopy(plain, position, b, off, n)
        position += n
        return n
    }

    private fun openNextChunk() {
        val lengthField = ByteArray(4)
        if (!readFully(lengthField)) throw BackupException(BackupError.DAMAGED) // cut short before the last chunk
        val raw = ByteBuffer.wrap(lengthField).int
        val last = raw and BackupCrypto.LAST_CHUNK != 0
        val length = raw and BackupCrypto.LAST_CHUNK.inv()
        if (length < BackupCrypto.TAG_SIZE || length > chunkSize + BackupCrypto.TAG_SIZE) throw BackupException(BackupError.DAMAGED)
        val sealed = ByteArray(length)
        if (!readFully(sealed)) throw BackupException(BackupError.DAMAGED)
        plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BackupCrypto.TAG_BITS, BackupCrypto.nonce(prefix, index)))
            cipher.updateAAD(BackupCrypto.aad(header, index, last))
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            // On the first chunk the likeliest cause is the password; later it can only be damage.
            throw BackupException(if (index == 0L) BackupError.WRONG_PASSWORD_OR_DAMAGED else BackupError.DAMAGED, e)
        }
        position = 0
        index++
        if (last) {
            finished = true
            if (input.read() != -1) throw BackupException(BackupError.DAMAGED) // data after the last chunk
        }
    }

    private fun readFully(target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val n = try {
                input.read(target, read, target.size - read)
            } catch (e: EOFException) {
                -1
            }
            if (n == -1) return false
            read += n
        }
        return true
    }

    override fun close() = input.close()
}
