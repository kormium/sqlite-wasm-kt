package io.github.kormium.sqlitewasm.worker

internal fun newJsArray(): JsArray<JsAny?> = js("[]")
internal fun jsArrayPush(array: JsArray<JsAny?>, value: JsAny?) { js("array.push(value)") }
internal fun jsArrayIsEmpty(array: JsArray<JsAny?>): Boolean = js("array.length === 0")
