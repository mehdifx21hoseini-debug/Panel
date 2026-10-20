package app.fitos.core.domain.common

/** Where a record came from. Stored with every log so imports and future integrations stay traceable. */
enum class DataSource(val code: String) {
    MANUAL("manual"),
    IMPORT("import"),
    HEALTH_CONNECT("health_connect");

    companion object {
        fun fromCode(code: String): DataSource = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}
