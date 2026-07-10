# sqlite-wasm-kt

[![CI](https://github.com/kormium/sqlite-wasm-kt/actions/workflows/ci.yml/badge.svg)](https://github.com/kormium/sqlite-wasm-kt/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Kotlin/Wasm bindings for the official
[`@sqlite.org/sqlite-wasm`](https://github.com/sqlite/sqlite-wasm) package — SQLite in the
browser with a small suspend-friendly connection API, plus a companion Worker RPC bundle so a
connection can be hosted off the main thread.

Independent of any ORM: the API surface is SQL strings, positional parameters and typed row
reads. [Kormium](https://github.com/kormium/kormium) builds its browser engines on it, but
nothing here depends on Kormium types.

```kotlin
import io.github.kormium.sqlitewasm.*

val db = openSqliteWasm()                    // SqliteWasmStorage.InMemory by default
db.execute("CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT)")
db.execute("INSERT INTO t(name) VALUES (?)", listOf("hello"))
val names = db.query("SELECT name FROM t") { row -> row.getString(0) }
db.close()
```

## Modules

| Module | Artifact | What it is |
| --- | --- | --- |
| `sqlite-wasm-kt` | `io.github.kormium:sqlite-wasm-kt` (Maven) | The bindings: `openSqliteWasm(storage): SqliteWasmConnection` with `execute`/`query`/`close`, typed `SqliteWasmRow` reads (long/double/string/bytes), `SqliteWasmException` |
| `sqlite-wasm-kt-worker` | `@kormium/sqlite-wasm-worker` (npm) | A standalone Worker entry point answering a `postMessage` RPC protocol (open/execute/query/close) over one connection |

## Storage modes

- **`SqliteWasmStorage.InMemory`** (default) — works on the main thread or in a Worker, no
  special hosting requirements.
- **`SqliteWasmStorage.Opfs(path)`** — persistent, multi-connection-capable via SQLite's
  `opfs-wl` VFS (Web Locks + `Atomics.waitAsync`, SQLite 3.53.0+). Two hard requirements,
  both by spec rather than by this library: it must be opened **from within a Worker**
  (OPFS sync access handles are Worker-only), and the page must be **cross-origin isolated**
  (`Cross-Origin-Opener-Policy: same-origin` +
  `Cross-Origin-Embedder-Policy: require-corp` response headers). Without the headers,
  `OpfsWlDb` silently fails to construct — there is no error to catch, so check
  `crossOriginIsolated` in your app if you need a diagnosable failure.

## Why the worker bundle is a separate npm package

Kotlin/Wasm produces one bundle per **executable**, not per library — a library cannot
transparently ship the Worker script a `new Worker(...)` needs. So the Worker entry point is
its own tiny executable, published as `@kormium/sqlite-wasm-worker`. A consumer references it
with webpack's built-in Worker bundling:

```js
new Worker(
  new URL('@kormium/sqlite-wasm-worker/dist/sqlite-wasm-kt-workspace-sqlite-wasm-kt-worker.mjs',
          import.meta.url),
  { type: 'module' },
)
```

and webpack emits it as its own chunk — no consumer-side webpack configuration for the Worker
delivery itself.

The package ships **raw ESM** (the Kotlin/Wasm compiler's un-webpacked `.mjs` output), not a
pre-bundled file, deliberately: a pre-bundled file is one opaque module to the consumer's
bundler, which then never discovers `@sqlite.org/sqlite-wasm`'s own `sqlite3.wasm` asset
reference inside it — the asset silently never reaches the final dist. Raw ESM lets the
consumer's bundler trace and emit those assets itself.

## Status

Pre-publication. Neither artifact is on Maven Central / npm yet; Kormium currently consumes
both via a Gradle composite build and a local npm path. The public ABI is tracked with the
binary-compatibility-validator (`./gradlew apiCheck`).

## License

[Apache 2.0](LICENSE)
