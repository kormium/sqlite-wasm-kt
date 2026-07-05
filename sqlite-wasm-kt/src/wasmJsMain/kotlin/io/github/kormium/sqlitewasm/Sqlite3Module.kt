@file:JsModule("@sqlite.org/sqlite-wasm")

package io.github.kormium.sqlitewasm

import kotlin.js.Promise

/**
 * The module's default export — an async factory resolving to the initialised `sqlite3` API
 * object (`Sqlite3Static`, kept as opaque [JsAny] here; only `oo1.DB`/`oo1.OpfsWlDb` are used, via
 * the constructors in NativeApi.kt). A declaration-level `@JsModule` binds the default export.
 */
@JsName("default")
internal external fun sqlite3InitModule(config: JsAny? = definedExternally): Promise<JsAny>
