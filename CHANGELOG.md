# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0]

Initial release.

### Added

- `sqlite-wasm-kt` (Maven: `io.github.kormium:sqlite-wasm-kt`): Kotlin/Wasm bindings for the
  official `@sqlite.org/sqlite-wasm` package. `openSqliteWasm(storage): SqliteWasmConnection`
  with suspend `execute`/`query`/`close`, typed `SqliteWasmRow` reads (long/double/string/bytes),
  `SqliteWasmException`. Storage: `InMemory` or `Opfs(path)` — the latter multi-connection-capable
  via the `opfs-wl` VFS (SQLite 3.53.0+), Worker-only and requires cross-origin isolation
  (COOP/COEP).
- `sqlite-wasm-kt-worker` (npm: `@kormium/sqlite-wasm-worker`): a standalone Worker entry point
  answering a `postMessage` RPC protocol (open/execute/query/close) over one connection, shipped
  as raw ESM so the consumer's bundler traces and emits `@sqlite.org/sqlite-wasm`'s assets itself.
  Includes a ready handshake so the first request cannot race Worker startup.
- OPFS databases open with flags `'c'` (not the upstream demo's `'ct'` — `t` enables
  per-statement SQL tracing to `console.log`, a real measured drag), and the public ABI of both
  modules is tracked with the binary-compatibility-validator.
