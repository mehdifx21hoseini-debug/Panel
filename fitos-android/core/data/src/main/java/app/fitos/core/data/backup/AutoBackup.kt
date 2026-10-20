package app.fitos.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.fitos.core.domain.backup.BackupService
import app.fitos.core.domain.settings.AutoBackupError
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.time.JalaliDate
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.security.KeyStore
import java.time.Clock
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A folder the automatic backup writes into. Only its own files are ever touched. */
interface BackupFolder {
    /** File names in the folder; throws [FileNotFoundException] when the folder is gone or not allowed. */
    fun list(): List<String>

    /** Creates the file, or empties an existing one of that name. */
    fun openWrite(name: String): OutputStream

    fun delete(name: String)
}

/** Keeps one secret (the automatic backup password) so that only this app on this phone can read it. */
interface SecretStore {
    fun get(): CharArray?
    fun put(value: CharArray?)
}

/**
 * Writes the weekly automatic backup: one dated file in the chosen folder, then removes the
 * oldest automatic files beyond the number to keep. Hand-made backups and other files in the
 * folder are never removed, because only names of the form [fileName] are considered.
 */
class AutoBackupRunner(
    private val backup: BackupService,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val folderFor: (String) -> BackupFolder,
    private val secrets: SecretStore,
) {
    /** Runs when enabled (or always with [force], for "back up now"); returns the error, null on success. */
    suspend fun run(force: Boolean = false): AutoBackupError? = withContext(Dispatchers.IO) {
        val s = settings.autoBackup.first()
        if (!force && !s.enabled) return@withContext null
        val error = attempt(s.folder, s.encrypted, s.keep)
        settings.updateAutoBackup { it.copy(lastRunAt = if (error == null) clock.instant() else it.lastRunAt, lastError = error) }
        error
    }

    private suspend fun attempt(uri: String?, encrypted: Boolean, keep: Int): AutoBackupError? {
        val folder = uri?.let(folderFor) ?: return AutoBackupError.FOLDER
        val existing = try {
            folder.list()
        } catch (_: Exception) {
            return AutoBackupError.FOLDER
        }
        val password = if (encrypted) secrets.get() ?: return AutoBackupError.PASSWORD else null
        val name = fileName(LocalDate.now(clock))
        try {
            folder.openWrite(name).use { backup.export(it, password) }
        } catch (_: FileNotFoundException) {
            return AutoBackupError.FOLDER
        } catch (_: Exception) {
            runCatching { folder.delete(name) }
            return AutoBackupError.WRITE
        } finally {
            password?.fill(' ')
        }
        (existing + name).distinct().filter { PATTERN.matches(it) }.sortedDescending().drop(keep).forEach { old ->
            runCatching { folder.delete(old) }
        }
        return null
    }

    companion object {
        private val PATTERN = Regex("""FitOS-auto-\d{4}-\d{2}-\d{2}\.fitbak""")

        /** "FitOS-auto-1405-07-28.fitbak": Jalali date with Latin digits, so names sort by date. */
        fun fileName(day: LocalDate): String {
            val j = JalaliDate.of(day)
            return "FitOS-auto-%04d-%02d-%02d.fitbak".format(j.year, j.month, j.day)
        }
    }
}

/** A plain directory, for tests. */
class FileBackupFolder(private val dir: File) : BackupFolder {
    override fun list(): List<String> {
        if (!dir.isDirectory) throw FileNotFoundException(dir.path)
        return dir.list()?.toList().orEmpty()
    }

    override fun openWrite(name: String): OutputStream = File(dir, name).outputStream()

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}

/** A folder picked with the system folder picker, through the storage access framework. */
class DocumentTreeFolder(private val context: Context, private val tree: Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val treeDoc get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun children(): Map<String, Uri> {
        val listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cursor = resolver.query(listing, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: throw FileNotFoundException(tree.toString())
        return cursor.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))) }
        }
    }

    override fun list(): List<String> = try {
        children().keys.toList()
    } catch (e: SecurityException) {
        throw FileNotFoundException(e.message)
    } catch (e: IllegalArgumentException) {
        throw FileNotFoundException(e.message)
    }

    override fun openWrite(name: String): OutputStream {
        val doc = children()[name]
            ?: DocumentsContract.createDocument(resolver, treeDoc, "application/octet-stream", name)
            ?: throw FileNotFoundException(name)
        return resolver.openOutputStream(doc, "wt") ?: throw FileNotFoundException(name)
    }

    override fun delete(name: String) {
        children()[name]?.let { DocumentsContract.deleteDocument(resolver, it) }
    }
}

/**
 * The automatic backup password, encrypted with an AES key that lives in the Android Keystore
 * and cannot be taken off the phone. The encrypted value sits in the app's private storage;
 * neither is part of a backup, so after a restore on a new phone the password is asked again.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    override fun get(): CharArray? {
        val stored = prefs.getString(PREF, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8).toCharArray()
        } catch (_: Exception) {
            null
        }
    }

    override fun put(value: CharArray?) {
        if (value == null) {
            prefs.edit().remove(PREF).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(String(value).toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREF, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fitos_auto_backup"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val PREF = "auto_backup_password"
    }
}
