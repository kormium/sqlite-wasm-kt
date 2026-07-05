package io.github.kormium.sqlitewasm

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteWasmInMemoryTest {

    @Test
    fun crud() = runTest {
        val db = openSqliteWasm()
        try {
            db.execute("CREATE TABLE todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)")

            val inserted = db.execute(
                "INSERT INTO todos (title, done) VALUES (?, ?)",
                listOf("write tests", 0L),
            )
            assertEquals(1L, inserted)

            db.execute("INSERT INTO todos (title, done) VALUES (?, ?)", listOf("ship it", 1L))

            val rows = db.query(
                "SELECT id, title, done FROM todos ORDER BY id",
                emptyList(),
            ) { row -> Triple(row.getLong(0), row.getString(1), row.getLong(2)) }

            assertEquals(2, rows.size)
            assertEquals(1L, rows[0].first)
            assertEquals("write tests", rows[0].second)
            assertEquals(0L, rows[0].third)
            assertEquals("ship it", rows[1].second)
            assertEquals(1L, rows[1].third)

            val updated = db.execute("UPDATE todos SET done = 1 WHERE title = ?", listOf("write tests"))
            assertEquals(1L, updated)

            val deleted = db.execute("DELETE FROM todos WHERE done = 1")
            assertEquals(2L, deleted)

            val remaining = db.query("SELECT id FROM todos") { it.getLong(0) }
            assertEquals(0, remaining.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun nullsAndBlobs() = runTest {
        val db = openSqliteWasm()
        try {
            db.execute("CREATE TABLE blobs (id INTEGER PRIMARY KEY, name TEXT, data BLOB)")
            db.execute("INSERT INTO blobs (name, data) VALUES (?, ?)", listOf(null, byteArrayOf(1, 2, 3, 4)))

            val row = db.query("SELECT name, data FROM blobs") { it }.single()
            assertNull(row.getString(0))
            assertEquals(listOf<Byte>(1, 2, 3, 4), row.getBytes(1)?.toList())
        } finally {
            db.close()
        }
    }

    @Test
    fun bigIntegersRoundTripExactly() = runTest {
        val db = openSqliteWasm()
        try {
            db.execute("CREATE TABLE big (id INTEGER PRIMARY KEY, n INTEGER)")
            val big = 9_007_199_254_740_993L // 2^53 + 1: not exactly representable as a JS number
            db.execute("INSERT INTO big (n) VALUES (?)", listOf(big))
            val n = db.query("SELECT n FROM big") { it.getLong(0) }.single()
            assertEquals(big, n)
        } finally {
            db.close()
        }
    }
}
