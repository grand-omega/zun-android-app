package dev.zun.flux.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.database.sqlite.SQLiteDatabase as PlaintextSQLiteDatabase

/**
 * Verifies the one-shot plaintext→SQLCipher migration. Runs as androidTest
 * because it needs the SQLCipher native libs.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseEncryptionMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "encryption-migration-test.db"
    private val passphrase = "test-passphrase-do-not-use-in-prod"

    @Before
    @After
    fun cleanup() {
        val dbFile = context.getDatabasePath(dbName)
        listOf(
            dbFile,
            File(dbFile.absolutePath + "-journal"),
            File(dbFile.absolutePath + "-wal"),
            File(dbFile.absolutePath + "-shm"),
            File(dbFile.parentFile, "$dbName.migrated"),
        ).forEach { it.delete() }
    }

    @Test
    fun plaintextDb_isReencryptedAndRowsSurvive() {
        // Seed a plaintext DB using the platform SQLite (not SQLCipher).
        val dbFile = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        PlaintextSQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE jobs (id TEXT PRIMARY KEY, createdAt INTEGER)")
            db.execSQL("INSERT INTO jobs (id, createdAt) VALUES ('job-1', 100), ('job-2', 200)")
            db.version = 4
        }

        AppDatabase.migratePlaintextIfNeeded(context, dbName, passphrase)

        // The original plaintext DB should be replaced by an encrypted one — i.e.
        // opening with an empty key must fail.
        var openedWithEmptyKey = true
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray()).use { c -> c.moveToFirst() }
            }
        }.onFailure { openedWithEmptyKey = false }
        assertFalse("Encrypted DB unexpectedly opened with empty key", openedWithEmptyKey)

        // Opening with the real passphrase succeeds and the rows survive.
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase.toByteArray(),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null as SQLiteDatabaseHook?,
        ).use { db ->
            db.rawQuery("SELECT id, createdAt FROM jobs ORDER BY id", emptyArray()).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("job-1", c.getString(0))
                assertEquals(100L, c.getLong(1))
                assertTrue(c.moveToNext())
                assertEquals("job-2", c.getString(0))
                assertEquals(200L, c.getLong(1))
                assertFalse(c.moveToNext())
            }
            db.rawQuery("PRAGMA user_version", emptyArray()).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(4, c.getInt(0))
            }
        }
    }

    @Test
    fun migration_isNoOp_whenDbAbsent() {
        AppDatabase.migratePlaintextIfNeeded(context, dbName, passphrase)
        // No exception, no DB created.
        assertFalse(context.getDatabasePath(dbName).exists())
    }

    @Test
    fun migration_isNoOp_whenDbAlreadyEncrypted() {
        val dbFile = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        // Create an encrypted DB up front.
        SQLiteDatabase.openOrCreateDatabase(dbFile, passphrase.toByteArray(), null, null).use { db ->
            db.execSQL("CREATE TABLE marker (id INTEGER)")
            db.execSQL("INSERT INTO marker (id) VALUES (42)")
        }
        val originalSize = dbFile.length()

        AppDatabase.migratePlaintextIfNeeded(context, dbName, passphrase)

        // Still encrypted, still openable with the same passphrase, marker row intact.
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase.toByteArray(),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null as SQLiteDatabaseHook?,
        ).use { db ->
            db.rawQuery("SELECT id FROM marker", emptyArray()).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(42, c.getInt(0))
            }
        }
        assertEquals(originalSize, dbFile.length())
    }
}
