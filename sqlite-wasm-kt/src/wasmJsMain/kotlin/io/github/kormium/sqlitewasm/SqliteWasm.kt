package io.github.kormium.sqlitewasm

import kotlinx.coroutines.await

/** Where a [SqliteWasmConnection]'s data lives. */
public sealed interface SqliteWasmStorage {
    /** Transient, in-process only — gone when the connection closes. Works in Node and browsers. */
    public data object InMemory : SqliteWasmStorage

    /**
     * Persisted to the Origin Private File System at [path], via the `opfs-wl` VFS (SQLite
     * 3.53.0+): multiple connections (e.g. one per Worker) can open the same [path] concurrently.
     * Must be opened from within a Worker — OPFS access handles are Worker-only by spec. Browser
     * only (no Node OPFS).
     */
    public data class Opfs(public val path: String) : SqliteWasmStorage
}

/** One row of a [SqliteWasmConnection.query] result. Indices are 0-based, left to right. */
public interface SqliteWasmRow {
    public val columnCount: Int
    public fun columnName(index: Int): String
    public fun getString(index: Int): String?
    public fun getLong(index: Int): Long?
    public fun getDouble(index: Int): Double?
    public fun getBytes(index: Int): ByteArray?

    /**
     * Reads column [index] as whichever Kotlin type its own SQLite storage class maps to —
     * [String], [Long] (integer-valued numbers/`bigint`), [Double] (fractional numbers),
     * [ByteArray] (blobs), or `null` — without the caller needing to know the column's type ahead
     * of time. Useful for schema-less row inspection/serialization.
     */
    public fun getRaw(index: Int): Any?
}

/** Wraps a `sqlite3` JS exception (or any interop failure) with the offending SQL, where known. */
public class SqliteWasmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One open SQLite connection. Not thread/coroutine-safe for concurrent calls on the same instance
 *  — callers wanting concurrency open multiple connections (see [SqliteWasmStorage.Opfs]). */
public interface SqliteWasmConnection {
    /** Runs [sql] (INSERT/UPDATE/DELETE/DDL) with positional `?` [params]; returns rows affected. */
    public suspend fun execute(sql: String, params: List<Any?> = emptyList()): Long

    /** Runs a `SELECT`-shaped [sql] with positional `?` [params]; maps each row via [map]. */
    public suspend fun <T> query(sql: String, params: List<Any?> = emptyList(), map: (SqliteWasmRow) -> T): List<T>

    public suspend fun close()
}

/** Opens a [SqliteWasmConnection] backed by `@sqlite.org/sqlite-wasm`. */
public suspend fun openSqliteWasm(storage: SqliteWasmStorage = SqliteWasmStorage.InMemory): SqliteWasmConnection {
    val sqlite3 = runCatchingSqlite { sqlite3InitModule().await<JsAny>() }
    val db = when (storage) {
        is SqliteWasmStorage.InMemory -> newInMemoryDb(sqlite3)
        is SqliteWasmStorage.Opfs -> newOpfsWlDb(sqlite3, storage.path)
    }
    return NativeSqliteWasmConnection(db)
}

private class NativeSqliteWasmConnection(private val db: NativeDb) : SqliteWasmConnection {
    override suspend fun execute(sql: String, params: List<Any?>): Long {
        runCatchingSqlite { db.exec(writeExecOptions(sql, encodeSqliteWasmParams(params))) }
        return changesCount(db).toLong()
    }

    override suspend fun <T> query(sql: String, params: List<Any?>, map: (SqliteWasmRow) -> T): List<T> {
        val columnNames = newJsArray()
        val rows = runCatchingSqlite { db.exec(queryExecOptions(sql, encodeSqliteWasmParams(params), columnNames)) }
            ?: return emptyList()
        val names = List(jsArrayLength(columnNames)) { i -> jsArrayGet(columnNames, i)?.let(::jsCellToKString) ?: "" }
        val rowCount = jsArrayLength(rows)
        return List(rowCount) { i ->
            val row = jsArrayGet(rows, i) ?: throw SqliteWasmException("Null result row at index $i [sql: $sql]")
            map(NativeSqliteWasmRow(row, names))
        }
    }

    override suspend fun close() {
        runCatchingSqlite { db.close() }
    }
}

private class NativeSqliteWasmRow(private val row: JsAny, private val names: List<String>) : SqliteWasmRow {
    override val columnCount: Int get() = names.size
    override fun columnName(index: Int): String = names[index]

    private fun cell(index: Int): JsAny? = jsArrayGet(row, index)

    override fun getString(index: Int): String? {
        val value = cell(index) ?: return null
        if (jsCellKind(value) == "bytes") throw SqliteWasmException("Column $index is a blob — use getBytes()")
        return jsCellToKString(value)
    }

    override fun getLong(index: Int): Long? {
        val value = cell(index) ?: return null
        return when (jsCellKind(value)) {
            "number", "bigint" -> jsCellToKString(value).let { it.toLongOrNull() ?: it.toDouble().toLong() }
            "string" -> jsCellToKString(value).toLong()
            else -> throw SqliteWasmException("Column $index is not numeric — use getBytes()/getString()")
        }
    }

    override fun getDouble(index: Int): Double? {
        val value = cell(index) ?: return null
        return when (jsCellKind(value)) {
            "number", "bigint", "string" -> jsCellToKDouble(value)
            else -> throw SqliteWasmException("Column $index is not numeric — use getBytes()/getString()")
        }
    }

    override fun getBytes(index: Int): ByteArray? {
        val value = cell(index) ?: return null
        if (jsCellKind(value) != "bytes") throw SqliteWasmException("Column $index is not a blob — use getString()/getLong()")
        return uint8ArrayToByteArray(value)
    }

    override fun getRaw(index: Int): Any? {
        val value = cell(index) ?: return null
        return when (jsCellKind(value)) {
            "bytes" -> uint8ArrayToByteArray(value)
            "number", "bigint" -> jsCellToKString(value).toLongOrNull() ?: jsCellToKDouble(value)
            else -> jsCellToKString(value)
        }
    }
}

private inline fun <T> runCatchingSqlite(block: () -> T): T =
    try {
        block()
    } catch (e: SqliteWasmException) {
        throw e
    } catch (e: Throwable) {
        throw SqliteWasmException(e.message ?: "sqlite3 error", e)
    }

/**
 * Decodes a raw parameter array — native JS values (`string`/`bigint`/`number`/`Uint8Array`/`null`)
 * such as one received via `postMessage` structured clone — into the [Any?] shapes
 * [SqliteWasmConnection.execute]/[SqliteWasmConnection.query] accept. The inverse of the encoding
 * [encodeSqliteWasmParams] does; same value-kind dispatch as [SqliteWasmRow.getRaw]. For RPC/worker-boundary
 * code (e.g. a connection pool proxying calls to a Worker) that already has native JS values and
 * needs to hand them back to a [SqliteWasmConnection] as ordinary parameters.
 */
public fun decodeSqliteWasmParams(raw: JsArray<JsAny?>): List<Any?> =
    List(jsArrayLength(raw)) { i ->
        val value = jsArrayGet(raw, i) ?: return@List null
        when (jsCellKind(value)) {
            "bytes" -> uint8ArrayToByteArray(value)
            "number", "bigint" -> jsCellToKString(value).toLongOrNull() ?: jsCellToKDouble(value)
            else -> jsCellToKString(value)
        }
    }

/**
 * Encodes ordinary Kotlin parameter values ([String]/[Boolean]/[Long]/[Int]/[Double]/[Float]/
 * [ByteArray]/`null`) into the native JS `BindableValue` shapes `db.exec()` binds directly — the
 * same conversion [SqliteWasmConnection.execute]/[SqliteWasmConnection.query] apply internally,
 * exposed for RPC/worker-boundary code that needs to ship already-encoded values across
 * `postMessage` (e.g. a connection pool proxying calls to a Worker). Inverse of
 * [decodeSqliteWasmParams].
 */
public fun encodeSqliteWasmParams(params: List<Any?>): JsArray<JsAny?> {
    val array = newJsArray()
    for (param in params) {
        val value: JsAny? = when (param) {
            null -> null
            is String -> param.toJsString()
            is Boolean -> param.toJsBoolean()
            is Long -> longToJsBigInt(param)
            is Int -> param.toDouble().toJsNumber()
            is Double -> param.toJsNumber()
            is Float -> param.toDouble().toJsNumber()
            is ByteArray -> byteArrayToUint8Array(param)
            else -> throw SqliteWasmException("Unsupported bind parameter type: ${param::class}")
        }
        jsArrayPush(array, value)
    }
    return array
}
