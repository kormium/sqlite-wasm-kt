package io.github.kormium.sqlitewasm.worker

/** One `postMessage` event as seen inside a dedicated Worker. */
internal external interface MessageEvent : JsAny {
    val data: JsAny
}

/** The dedicated Worker global scope (`self`): receive via [addMessageListener], reply via [postMessage]. */
internal external interface WorkerScope : JsAny {
    fun postMessage(message: JsAny)
}

internal fun workerScope(): WorkerScope = js("self")

/**
 * Registers [handler] via `addEventListener`, not `self.onmessage =` — the coroutine dispatcher
 * running inside this Worker also uses `postMessage`/`onmessage` on `self` internally for task
 * scheduling; assigning `onmessage` directly clobbers (or gets clobbered by) that. `addEventListener`
 * lets both coexist.
 */
internal fun addMessageListener(handler: (MessageEvent) -> Unit) {
    js("self.addEventListener('message', (e) => handler(e))")
}
