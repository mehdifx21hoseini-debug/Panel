package app.fitos.core.data

import app.fitos.core.data.backup.BackupCrypto
import app.fitos.core.data.backup.DecryptingInputStream
import app.fitos.core.data.backup.EncryptingOutputStream
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {

    private val password = "رمز-خیلی-خوب-123".toCharArray()

    private fun encrypt(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptingOutputStream(out, password, iterations = 10_000).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(bytes: ByteArray, pass: CharArray = password): ByteArray =
        DecryptingInputStream(ByteArrayInputStream(bytes), pass).use { it.readBytes() }

    private fun expectError(error: BackupError, block: () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: BackupException) {
            assertEquals(error, e.error)
        }
    }

    @Test fun roundTripsAnySize() {
        for (size in listOf(0, 1, BackupCrypto.CHUNK_SIZE - 1, BackupCrypto.CHUNK_SIZE, BackupCrypto.CHUNK_SIZE * 3 + 17)) {
            val data = Random(size).nextBytes(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test fun headerIsRecognised() {
        val bytes = encrypt(byteArrayOf(1, 2, 3))
        assertEquals(true, BackupCrypto.startsWithMagic(bytes))
        assertEquals(false, BackupCrypto.startsWithMagic("PK\u0003\u0004".toByteArray()))
    }

    @Test fun wrongPasswordIsReported() {
        val bytes = encrypt(Random(1).nextBytes(1000))
        expectError(BackupError.WRONG_PASSWORD_OR_DAMAGED) { decrypt(bytes, "wrong".toCharArray()) }
    }

    @Test fun anyChangedByteIsDetected() {
        val bytes = encrypt(Random(2).nextBytes(BackupCrypto.CHUNK_SIZE * 2 + 10))
        // A byte inside the second chunk.
        val tampered = bytes.copyOf().also { it[BackupCrypto.HEADER_SIZE + 4 + BackupCrypto.CHUNK_SIZE + 16 + 4 + 100] = (it[BackupCrypto.HEADER_SIZE + 4 + BackupCrypto.CHUNK_SIZE + 16 + 4 + 100] + 1).toByte() }
        expectError(BackupError.DAMAGED) { decrypt(tampered) }
        // The header (salt, iterations…) is authenticated too.
        val badHeader = bytes.copyOf().also { it[20] = (it[20] + 1).toByte() }
        expectError(BackupError.WRONG_PASSWORD_OR_DAMAGED) { decrypt(badHeader) }
    }

    @Test fun truncationAndTrailingDataAreDetected() {
        val bytes = encrypt(Random(3).nextBytes(BackupCrypto.CHUNK_SIZE * 2 + 10))
        // Cut exactly at a chunk boundary: everything left decrypts, but the last-chunk flag is missing.
        val firstChunkEnd = BackupCrypto.HEADER_SIZE + 4 + BackupCrypto.CHUNK_SIZE + 16
        expectError(BackupError.DAMAGED) { decrypt(bytes.copyOf(firstChunkEnd)) }
        expectError(BackupError.DAMAGED) { decrypt(bytes.copyOf(bytes.size - 5)) }
        expectError(BackupError.DAMAGED) { decrypt(bytes + byteArrayOf(0)) }
    }

    @Test fun reorderedChunksAreDetected() {
        val data = Random(4).nextBytes(BackupCrypto.CHUNK_SIZE * 3)
        val bytes = encrypt(data)
        val chunk = 4 + BackupCrypto.CHUNK_SIZE + 16
        val h = BackupCrypto.HEADER_SIZE
        val swapped = bytes.copyOfRange(0, h) + bytes.copyOfRange(h + chunk, h + 2 * chunk) +
            bytes.copyOfRange(h, h + chunk) + bytes.copyOfRange(h + 2 * chunk, bytes.size)
        expectError(BackupError.WRONG_PASSWORD_OR_DAMAGED) { decrypt(swapped) }
    }
}
