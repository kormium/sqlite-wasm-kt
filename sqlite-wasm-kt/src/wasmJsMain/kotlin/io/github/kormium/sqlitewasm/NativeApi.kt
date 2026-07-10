package io.github.kormium.sqlitewasm

/**
 * A `sqlite3.oo1.DB`-shaped handle (also matches `OpfsWlDb`/`OpfsSAHPoolDatabase`, which extend
 * the same class). `exec()` and `changes()` are plain synchronous C calls in this build — none of
 * the VFSes this library uses (in-memory, `opfs-wl`) need Asyncify, unlike the original async
 * `opfs` VFS.
 */
internal external interface NativeDb : JsAny {
    fun exec(opts: JsAny): JsAny?
    fun close()
}

/** `sqlite3.oo1.DB` is not a static module export — it's a property of the runtime `sqlite3`
 *  object returned by [sqlite3InitModule] — so it's constructed via `js()`, not `@JsModule`. */
internal fun newInMemoryDb(sqlite3: JsAny): NativeDb = js("new sqlite3.oo1.DB(':memory:', 'c')")

/**
 * OPFS-backed, persisted, multi-connection-capable via the `opfs-wl` VFS (Web Locks +
 * `Atomics.waitAsync`, SQLite 3.53.0+). Must be constructed from within a Worker — OPFS access
 * handles are Worker-only by spec, same as every other OPFS-backed VFS.
 *
 * Flags are `'c'` (create) only — NOT `'ct'` as in the upstream opfs-wl demo this was first
 * copied from: `t` means per-statement SQL tracing to `console.log`, which silently taxes every
 * statement (string-formatting multi-KB batch INSERTs, console I/O) — a real, measured drag, not
 * just noise.
 */
internal fun newOpfsWlDb(sqlite3: JsAny, path: String): NativeDb = js("new sqlite3.oo1.OpfsWlDb(path, 'c')")

/** `db.exec()` options for a write (`INSERT`/`UPDATE`/`DELETE`/DDL): no result rows expected. */
internal fun writeExecOptions(sql: String, bind: JsArray<JsAny?>): JsAny =
    js("({ sql: sql, bind: bind, returnValue: 'this' })")

/**
 * `db.exec()` options for a query: `rowMode: 'array'` + `returnValue: 'resultRows'` makes `exec`
 * return `SqlValue[][]` directly; `columnNames` is filled in place with the result's column names.
 */
internal fun queryExecOptions(sql: String, bind: JsArray<JsAny?>, columnNames: JsArray<JsAny?>): JsAny =
    js("({ sql: sql, bind: bind, rowMode: 'array', returnValue: 'resultRows', columnNames: columnNames })")

/** `sqlite3_changes()` via the OO#1 wrapper: rows affected by the most recent statement. */
internal fun changesCount(db: NativeDb): Int = js("db.changes()")

// ---- JS array / value plumbing ----

internal fun newJsArray(): JsArray<JsAny?> = js("[]")
internal fun jsArrayPush(array: JsArray<JsAny?>, value: JsAny?) { js("array.push(value)") }
internal fun jsArrayLength(value: JsAny): Int = js("value.length")
internal fun jsArrayGet(value: JsAny, index: Int): JsAny? = js("value[index]")

/**
 * A `BindableValue`-safe representation of a 64-bit Kotlin [Long]: JS `number` loses precision
 * beyond 2^53, so this round-trips through an exact decimal string into a JS `bigint` (a valid
 * `SqlValue`), matching how [longFromCell] reads integers back out.
 */
internal fun longToJsBigInt(value: Long): JsAny = js("BigInt(value.toString())")

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")
private fun setByte(array: JsAny, index: Int, value: Int) { js("array[index] = value") }
private fun byteAt(value: JsAny, index: Int): Int = js("value[index]")

internal fun byteArrayToUint8Array(bytes: ByteArray): JsAny {
    val array = newUint8Array(bytes.size)
    for (i in bytes.indices) setByte(array, i, bytes[i].toInt() and 0xFF)
    return array
}

internal fun uint8ArrayToByteArray(value: JsAny): ByteArray {
    val len = jsArrayLength(value)
    return ByteArray(len) { byteAt(value, it).toByte() }
}

/** Tags a query result cell's JS runtime type so [SqliteWasmRow] getters know how to read it. */
internal fun jsCellKind(value: JsAny?): String = js(
    "value === null || value === undefined ? 'null' : " +
        "typeof value === 'string' ? 'string' : " +
        "typeof value === 'bigint' ? 'bigint' : " +
        "typeof value === 'number' ? 'number' : " +
        "(value instanceof Uint8Array) ? 'bytes' : 'other'",
)

internal fun jsCellToKString(value: JsAny): String = js("String(value)")
internal fun jsCellToKDouble(value: JsAny): Double = js("Number(value)")
