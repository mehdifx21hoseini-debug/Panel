package app.fitos.core.data.backup

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Version information the app passes in, written into every backup. */
data class AppInfo(val versionName: String, val versionCode: Int)

internal object BackupFormat {
    const val FORMAT = "fitos-backup"
    /** Bump when the file layout changes; older versions must stay readable. */
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val DATA_DIR = "data/"
    const val PHOTOS_DIR = "photos/"

    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    /** Only these names may appear in a backup; anything else (or any path trick) is rejected. */
    private val ALLOWED = Regex("""^(manifest\.json|settings\.json|data/[a-z_]{1,64}\.json|photos/[A-Za-z0-9._-]{1,128})$""")

    fun isAllowedEntry(name: String): Boolean = ALLOWED.matches(name) && ".." !in name

    const val MAX_ENTRIES = 20_000
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
}

@Serializable
internal data class Manifest(
    val format: String,
    val formatVersion: Int,
    val createdAt: Long,
    val zone: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val schemaVersion: Int,
    val tables: Map<String, TableInfo>,
    /** SHA-256 (hex) of every other file in the archive, by path. */
    val files: Map<String, String>,
    val photoCount: Int,
)

@Serializable
internal data class TableInfo(val rows: Int, val file: String)

@Serializable
internal data class SettingsSnapshot(val themeMode: String? = null)

/** The exported Room schema of one version, bundled as an asset, as plain SQL. */
internal class SchemaSql(val version: Int, val tables: List<String>, val statements: List<String>)

internal object SchemaAssets {
    private const val DIR = "app.fitos.core.data.db.FitDatabase"

    /** Null when this app does not know the version (the backup is from a newer app). */
    fun load(context: Context, version: Int): SchemaSql? {
        val text = runCatching { context.assets.open("$DIR/$version.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return null
        val database = Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
        val tables = mutableListOf<String>()
        val statements = mutableListOf<String>()
        database.getValue("entities").jsonArray.forEach { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            tables += table
            statements += entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            entity["indices"]?.jsonArray?.forEach { index ->
                statements += index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
        }
        database["setupQueries"]?.jsonArray?.forEach { statements += it.jsonPrimitive.content }
        return SchemaSql(version, tables, statements)
    }
}
