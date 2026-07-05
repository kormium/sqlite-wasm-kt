package io.github.kormium.sqlitewasm.worker

import io.github.kormium.sqlitewasm.SqliteWasmConnection
import io.github.kormium.sqlitewasm.SqliteWasmStorage
import io.github.kormium.sqlitewasm.decodeSqliteWasmParams
import io.github.kormium.sqlitewasm.encodeSqliteWasmParams
import io.github.kormium.sqlitewasm.openSqliteWasm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Posted once, right after the message listener is registered — the caller waits for this instead
 *  of racing its first real request against Worker startup/message-queueing timing. */
private const val READY_ID = -1

/**
 * Entry point of the standalone pool-worker bundle. Generic RPC over one [SqliteWasmConnection]:
 * receives `postMessage` requests (`open` once, then any number of `execute`/`query`/`close`),
 * runs them, replies with the result. No Kormium dependency — `kormium-sqlite-wasm`'s reader/writer
 * pool (or any other caller) spawns a `Worker` pointed at this bundle and speaks this protocol.
 *
 * Requests are processed one at a time, in arrival order, via [Channel] — a caller must wait for
 * the `open` reply before sending further requests (single in-flight request per connection).
 */
fun main() {
    val inbox = Channel<InMessage>(Channel.UNLIMITED)
    addMessageListener { event -> inbox.trySend(event.data as InMessage) }
    workerScope().postMessage(okMessage(READY_ID))

    CoroutineScope(Job()).launch {
        var connection: SqliteWasmConnection? = null
        for (message in inbox) {
            try {
                when (message.kind) {
                    "open" -> {
                        val storage = message.opfsPath?.let { SqliteWasmStorage.Opfs(it) } ?: SqliteWasmStorage.InMemory
                        connection = openSqliteWasm(storage)
                        workerScope().postMessage(okMessage(message.id))
                    }
                    "execute" -> {
                        val conn = requireConnection(connection)
                        val affected = conn.execute(message.sql!!, decodeSqliteWasmParams(message.params ?: newJsArray()))
                        workerScope().postMessage(executeResultMessage(message.id, affected.toDouble().toJsNumber()))
                    }
                    "query" -> {
                        val conn = requireConnection(connection)
                        var columns = newJsArray()
                        val rows = newJsArray()
                        conn.query(message.sql!!, decodeSqliteWasmParams(message.params ?: newJsArray())) { row ->
                            if (jsArrayIsEmpty(columns)) {
                                val names = newJsArray()
                                repeat(row.columnCount) { jsArrayPush(names, row.columnName(it).toJsString()) }
                                columns = names
                            }
                            jsArrayPush(rows, encodeSqliteWasmParams(List(row.columnCount) { row.getRaw(it) }))
                        }
                        workerScope().postMessage(queryResultMessage(message.id, columns, rows))
                    }
                    "close" -> {
                        connection?.close()
                        workerScope().postMessage(okMessage(message.id))
                    }
                    else -> workerScope().postMessage(errorMessage(message.id, "Unknown message kind: ${message.kind}"))
                }
            } catch (e: Throwable) {
                workerScope().postMessage(errorMessage(message.id, e.message ?: "worker error"))
            }
        }
    }
}

private fun requireConnection(connection: SqliteWasmConnection?): SqliteWasmConnection =
    connection ?: error("Connection not opened yet — send an \"open\" message first")
