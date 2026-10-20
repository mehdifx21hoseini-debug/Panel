package app.fitos.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.Migrations
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupException
import app.fitos.core.domain.backup.BackupService
import app.fitos.core.domain.backup.BackupSummary
import app.fitos.core.domain.backup.SafetyBackup
import app.fitos.core.domain.backup.StagedBackup
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Backup as a ZIP of one JSON file per table plus a manifest with checksums (docs/backup-format.md).
 *
 * Restore never writes into the live database until the backup has been fully checked:
 * the rows are loaded into a separate staging database created from the bundled schema of the
 * backup's version, then opened with Room so the app's own migrations bring it up to date, and
 * only then copied into the live database in one transaction. A safety backup of the current
 * data is written first.
 */
internal class RoomBackupService(
    private val context: Context,
    private val db: FitDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val app: AppInfo,
    private val iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
) : BackupService {

    private val stagingRoot get() = File(context.cacheDir, "restore")
    private val safetyDir get() = File(context.filesDir, "safety-backups")
    private val photosDir get() = File(context.filesDir, "photos")

    override suspend fun export(output: OutputStream, password: CharArray?): BackupSummary = withContext(Dispatchers.IO) {
        val theme = settings.themeMode.first()
        val summary = io { writeBackup(output, password, theme) }
        settings.setLastBackupAt(summary.createdAt)
        summary
    }

    override suspend fun isEncrypted(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        input.use { BackupCrypto.startsWithMagic(it.readNBytesCompat(BackupCrypto.MAGIC.size)) }
    }

    override suspend fun stage(input: InputStream, password: CharArray?): StagedBackup = withContext(Dispatchers.IO) {
        io { stageBackup(input, password) }
    }

    override suspend fun restore(staged: StagedBackup) = withContext(Dispatchers.IO) {
        io {
            val dir = File(stagingRoot, staged.token)
            val stagingDb = File(dir, STAGING_DB)
            if (!stagingDb.exists()) throw BackupException(BackupError.IO)
            writeSafetyBackupIfNeeded(settings.themeMode.first())
            copyIntoLive(stagingDb)
            replacePhotos(File(dir, "photos"))
            applySettings(File(dir, BackupFormat.SETTINGS))
            dir.deleteRecursively()
            Unit
        }
    }

    override suspend fun safetyBackups(): List<SafetyBackup> = withContext(Dispatchers.IO) {
        (safetyDir.listFiles { f -> f.name.startsWith(SAFETY_PREFIX) } ?: emptyArray())
            .mapNotNull { f ->
                val millis = f.name.removePrefix(SAFETY_PREFIX).removeSuffix(SAFETY_SUFFIX).toLongOrNull() ?: return@mapNotNull null
                SafetyBackup(f.name, Instant.ofEpochMilli(millis), f.length())
            }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun stageSafetyBackup(id: String): StagedBackup = withContext(Dispatchers.IO) {
        val file = File(safetyDir, id)
        if (!id.startsWith(SAFETY_PREFIX) || "/" in id || !file.exists()) throw BackupException(BackupError.IO)
        io { stageBackup(file.inputStream(), password = null) }
    }

    // ---- export ----

    private fun writeBackup(output: OutputStream, password: CharArray?, theme: ThemeMode): BackupSummary {
        val schema = SchemaAssets.load(context, FitDatabase.VERSION) ?: error("schema asset missing")
        val createdAt = clock.instant()
        val target = if (password != null) EncryptingOutputStream(output, password, iterations) else output
        val zip = ZipOutputStream(BufferedOutputStream(target))
        val files = linkedMapOf<String, String>()
        val tables = linkedMapOf<String, TableInfo>()

        fun entry(name: String, write: (OutputStream) -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            val digest = MessageDigest.getInstance("SHA-256")
            val out = DigestOutputStream(KeepOpen(zip), digest)
            write(out)
            out.flush()
            zip.closeEntry()
            files[name] = digest.digest().toHex()
        }

        // One transaction = one consistent snapshot of every table.
        db.runInTransaction {
            val sdb = db.openHelper.writableDatabase
            schema.tables.forEach { table ->
                val file = "${BackupFormat.DATA_DIR}$table.json"
                var rows = 0
                entry(file) { out -> rows = writeTable(sdb, table, out) }
                tables[table] = TableInfo(rows, file)
            }
        }
        entry(BackupFormat.SETTINGS) { out ->
            out.write(BackupFormat.json.encodeToString(SettingsSnapshot.serializer(), SettingsSnapshot(theme.name)).toByteArray())
        }
        val photos = photosDir.listFiles()?.filter { it.isFile }.orEmpty()
        photos.forEach { photo -> entry(BackupFormat.PHOTOS_DIR + photo.name) { out -> photo.inputStream().use { it.copyTo(out) } } }

        val manifest = Manifest(
            format = BackupFormat.FORMAT,
            formatVersion = BackupFormat.FORMAT_VERSION,
            createdAt = createdAt.toEpochMilli(),
            zone = clock.zone.id,
            appVersionName = app.versionName,
            appVersionCode = app.versionCode,
            schemaVersion = FitDatabase.VERSION,
            tables = tables,
            files = files,
            photoCount = photos.size,
        )
        zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
        zip.write(BackupFormat.json.encodeToString(Manifest.serializer(), manifest).toByteArray())
        zip.closeEntry()
        zip.close() // also seals the last encrypted chunk and closes [output]
        return manifest.toSummary(encrypted = password != null)
    }

    private fun writeTable(sdb: SupportSQLiteDatabase, table: String, out: OutputStream): Int {
        val writer = JsonWriter(OutputStreamWriter(out, Charsets.UTF_8))
        var rows = 0
        sdb.query("SELECT * FROM `$table`").use { c ->
            writer.beginObject()
            writer.name("table").value(table)
            writer.name("columns").beginArray()
            c.columnNames.forEach { writer.value(it) }
            writer.endArray()
            writer.name("rows").beginArray()
            while (c.moveToNext()) {
                writer.beginArray()
                for (i in 0 until c.columnCount) {
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                        Cursor.FIELD_TYPE_INTEGER -> writer.value(c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> writer.value(c.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> writer.value(BLOB_PREFIX + Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                        else -> writer.value(c.getString(i))
                    }
                }
                writer.endArray()
                rows++
            }
            writer.endArray()
            writer.endObject()
        }
        writer.flush()
        return rows
    }

    // ---- staging ----

    private fun stageBackup(input: InputStream, password: CharArray?): StagedBackup {
        val buffered = BufferedInputStream(input)
        buffered.mark(16)
        val head = buffered.readNBytesCompat(8)
        buffered.reset()
        val encrypted = BackupCrypto.startsWithMagic(head)
        if (!encrypted && !(head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte())) {
            throw BackupException(BackupError.NOT_A_BACKUP)
        }
        if (encrypted && password == null) throw BackupException(BackupError.PASSWORD_NEEDED)

        stagingRoot.deleteRecursively()
        val token = UUID.randomUUID().toString()
        val dir = File(stagingRoot, token).apply { mkdirs() }
        try {
            val source = if (encrypted) DecryptingInputStream(buffered, password!!) else buffered
            val hashes = source.use { unzip(it, dir) }
            val manifestFile = File(dir, BackupFormat.MANIFEST)
            if (!manifestFile.exists()) throw BackupException(BackupError.NOT_A_BACKUP)
            val manifest = try {
                BackupFormat.json.decodeFromString(Manifest.serializer(), manifestFile.readText())
            } catch (e: SerializationException) {
                throw BackupException(BackupError.NOT_A_BACKUP, e)
            } catch (e: IllegalArgumentException) {
                throw BackupException(BackupError.NOT_A_BACKUP, e)
            }
            if (manifest.format != BackupFormat.FORMAT) throw BackupException(BackupError.NOT_A_BACKUP)
            if (manifest.formatVersion > BackupFormat.FORMAT_VERSION || manifest.schemaVersion > FitDatabase.VERSION) {
                throw BackupException(BackupError.NEWER_APP)
            }
            manifest.files.forEach { (name, sha) ->
                if (hashes[name] != sha) throw BackupException(BackupError.DAMAGED)
            }
            manifest.tables.values.forEach { info -> if (info.file !in manifest.files) throw BackupException(BackupError.DAMAGED) }

            buildStagingDatabase(manifest, dir)
            return StagedBackup(token, manifest.toSummary(encrypted))
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Extracts allowed entries only, hashing as it goes, within size and count limits. */
    private fun unzip(input: InputStream, dir: File): Map<String, String> {
        val hashes = mutableMapOf<String, String>()
        var count = 0
        var total = 0L
        val zip = ZipInputStream(input)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            if (++count > BackupFormat.MAX_ENTRIES || !BackupFormat.isAllowedEntry(entry.name)) throw BackupException(BackupError.DAMAGED)
            val file = File(dir, entry.name)
            if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) throw BackupException(BackupError.DAMAGED)
            file.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            file.outputStream().use { out ->
                while (true) {
                    val n = zip.read(buffer)
                    if (n == -1) break
                    total += n
                    if (total > BackupFormat.MAX_TOTAL_BYTES) throw BackupException(BackupError.DAMAGED)
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
            hashes[entry.name] = digest.digest().toHex()
        }
        // A ZIP must end properly; drain anything left so a cut-off encrypted stream is noticed.
        while (input.read(buffer) != -1) Unit
        return hashes
    }

    /**
     * Creates the backup's schema version from the bundled schema, loads every row, checks the
     * counts, then lets Room open it: identity check plus any migrations up to the current version.
     */
    private fun buildStagingDatabase(manifest: Manifest, dir: File) {
        val schema = SchemaAssets.load(context, manifest.schemaVersion) ?: throw BackupException(BackupError.NEWER_APP)
        val file = File(dir, STAGING_DB)
        val staging = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            staging.beginTransaction()
            try {
                schema.statements.forEach { staging.execSQL(it) }
                manifest.tables.forEach { (table, info) ->
                    if (table !in schema.tables) throw BackupException(BackupError.DAMAGED)
                    val loaded = File(dir, info.file).inputStream().use { loadTable(staging, table, it) }
                    if (loaded != info.rows) throw BackupException(BackupError.DAMAGED)
                }
                staging.setTransactionSuccessful()
            } finally {
                staging.endTransaction()
            }
            staging.version = manifest.schemaVersion
        } catch (e: SQLiteException) {
            throw BackupException(BackupError.DAMAGED, e)
        } finally {
            staging.close()
        }
        try {
            Room.databaseBuilder(context, FitDatabase::class.java, file.absolutePath)
                .addMigrations(*Migrations.ALL)
                .build()
                .apply {
                    openHelper.writableDatabase
                    close()
                }
        } catch (e: RuntimeException) {
            throw BackupException(BackupError.DAMAGED, e)
        }
    }

    private fun loadTable(target: SQLiteDatabase, table: String, input: InputStream): Int {
        val reader = JsonReader(input.reader(Charsets.UTF_8))
        var columns = emptyList<String>()
        var rows = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "table" -> if (reader.nextString() != table) throw BackupException(BackupError.DAMAGED)
                "columns" -> {
                    columns = buildList {
                        reader.beginArray()
                        while (reader.hasNext()) add(reader.nextString())
                        reader.endArray()
                    }
                }
                "rows" -> {
                    if (columns.isEmpty()) throw BackupException(BackupError.DAMAGED)
                    val sql = "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})"
                    val statement = target.compileStatement(sql)
                    reader.beginArray()
                    while (reader.hasNext()) {
                        statement.clearBindings()
                        reader.beginArray()
                        var i = 1
                        while (reader.hasNext()) {
                            if (i > columns.size) throw BackupException(BackupError.DAMAGED)
                            when (reader.peek()) {
                                JsonToken.NULL -> reader.nextNull().also { statement.bindNull(i) }
                                JsonToken.NUMBER -> {
                                    val text = reader.nextString()
                                    val long = if (text.any { it == '.' || it == 'e' || it == 'E' }) null else text.toLongOrNull()
                                    if (long != null) statement.bindLong(i, long) else statement.bindDouble(i, text.toDouble())
                                }
                                JsonToken.STRING -> {
                                    val text = reader.nextString()
                                    if (text.startsWith(BLOB_PREFIX)) {
                                        statement.bindBlob(i, Base64.decode(text.removePrefix(BLOB_PREFIX), Base64.NO_WRAP))
                                    } else {
                                        statement.bindString(i, text)
                                    }
                                }
                                else -> throw BackupException(BackupError.DAMAGED)
                            }
                            i++
                        }
                        reader.endArray()
                        if (i - 1 != columns.size) throw BackupException(BackupError.DAMAGED)
                        statement.executeInsert()
                        rows++
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return rows
    }

    // ---- restore ----

    private fun copyIntoLive(stagingFile: File) {
        val staging = SQLiteDatabase.openDatabase(stagingFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = SchemaAssets.load(context, FitDatabase.VERSION)?.tables ?: error("schema asset missing")
            db.runInTransaction {
                val live = db.openHelper.writableDatabase
                // Foreign keys are checked once at commit, so table order does not matter.
                live.execSQL("PRAGMA defer_foreign_keys = ON")
                tables.forEach { live.execSQL("DELETE FROM `$it`") }
                tables.forEach { table ->
                    staging.rawQuery("SELECT * FROM `$table`", null).use { c ->
                        while (c.moveToNext()) {
                            val values = ContentValues(c.columnCount)
                            for (i in 0 until c.columnCount) {
                                val name = c.getColumnName(i)
                                when (c.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                                    Cursor.FIELD_TYPE_INTEGER -> values.put(name, c.getLong(i))
                                    Cursor.FIELD_TYPE_FLOAT -> values.put(name, c.getDouble(i))
                                    Cursor.FIELD_TYPE_BLOB -> values.put(name, c.getBlob(i))
                                    else -> values.put(name, c.getString(i))
                                }
                            }
                            live.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                        }
                    }
                }
            }
        } finally {
            staging.close()
        }
    }

    private fun replacePhotos(staged: File) {
        val incoming = staged.listFiles()?.filter { it.isFile }.orEmpty()
        photosDir.deleteRecursively()
        if (incoming.isEmpty()) return
        photosDir.mkdirs()
        incoming.forEach { it.copyTo(File(photosDir, it.name), overwrite = true) }
    }

    private suspend fun applySettings(file: File) {
        if (!file.exists()) return
        val snapshot = runCatching { BackupFormat.json.decodeFromString(SettingsSnapshot.serializer(), file.readText()) }.getOrNull() ?: return
        snapshot.themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let { settings.setThemeMode(it) }
    }

    /** Keeps the current data as a plain backup in private storage before it is replaced. */
    private fun writeSafetyBackupIfNeeded(theme: ThemeMode) {
        val schema = SchemaAssets.load(context, FitDatabase.VERSION) ?: return
        val hasData = schema.tables.any { table ->
            db.openHelper.readableDatabase.query("SELECT EXISTS(SELECT 1 FROM `$table`)").use { it.moveToFirst() && it.getInt(0) == 1 }
        }
        if (!hasData) return
        safetyDir.mkdirs()
        // Named by time; never overwrite an earlier copy made in the same millisecond.
        var stamp = clock.millis()
        while (File(safetyDir, "$SAFETY_PREFIX$stamp$SAFETY_SUFFIX").exists()) stamp++
        val file = File(safetyDir, "$SAFETY_PREFIX$stamp$SAFETY_SUFFIX")
        file.outputStream().use { writeBackup(it, password = null, theme = theme) }
        (safetyDir.listFiles { f -> f.name.startsWith(SAFETY_PREFIX) } ?: emptyArray())
            .sortedByDescending { it.name.removePrefix(SAFETY_PREFIX).removeSuffix(SAFETY_SUFFIX).toLongOrNull() ?: 0L }
            .drop(KEEP_SAFETY)
            .forEach { it.delete() }
    }

    // ---- helpers ----

    /** Maps low-level failures to the few errors the UI explains. */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: BackupException) {
        throw e
    } catch (e: ZipException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: EOFException) {
        throw BackupException(BackupError.DAMAGED, e) // cut short
    } catch (e: IOException) {
        throw BackupException(BackupError.IO, e)
    } catch (e: IllegalStateException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: NumberFormatException) {
        throw BackupException(BackupError.DAMAGED, e)
    }

    private fun Manifest.toSummary(encrypted: Boolean) = BackupSummary(
        createdAt = Instant.ofEpochMilli(createdAt),
        zone = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC")),
        appVersionName = appVersionName,
        formatVersion = formatVersion,
        schemaVersion = schemaVersion,
        counts = tables.mapValues { it.value.rows },
        photoCount = photoCount,
        encrypted = encrypted,
    )

    /** Lets an entry writer close its own stream without closing the ZIP. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    private companion object {
        const val STAGING_DB = "staging.db"
        const val SAFETY_PREFIX = "safety-"
        const val SAFETY_SUFFIX = ".fitbak"
        const val KEEP_SAFETY = 3
        const val BLOB_PREFIX = "base64:"
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** InputStream.readNBytes is API 33; this reads up to [n] bytes on any version. */
internal fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(out, read, n - read)
        if (r == -1) break
        read += r
    }
    return out.copyOf(read)
}
