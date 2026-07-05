package io.github.kormium.sqlitewasm.worker

/**
 * One request from the main thread. `kind` is `"open"` (once, at startup — [opfsPath] `null` means
 * in-memory), `"execute"` / `"query"` ([sql] + [params], positional `?`), or `"close"`.
 */
internal external interface InMessage : JsAny {
    val id: Int
    val kind: String
    val opfsPath: String?
    val sql: String?
    val params: JsArray<JsAny?>?
}

internal fun okMessage(id: Int): JsAny = js("({ id: id, ok: true })")

internal fun executeResultMessage(id: Int, affected: JsAny): JsAny =
    js("({ id: id, ok: true, affected: affected })")

internal fun queryResultMessage(id: Int, columns: JsArray<JsAny?>, rows: JsArray<JsAny?>): JsAny =
    js("({ id: id, ok: true, columns: columns, rows: rows })")

internal fun errorMessage(id: Int, error: String): JsAny = js("({ id: id, ok: false, error: error })")
