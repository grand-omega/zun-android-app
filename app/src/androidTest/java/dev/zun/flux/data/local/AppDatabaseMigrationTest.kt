package dev.zun.flux.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To5_preservesJobRowsAndCreatesPendingDeletes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        // Schema JSONs for versions 1–3 were never exported, so build the v1
        // database by hand instead of MigrationTestHelper.createDatabase().
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.version = 1
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO jobs (id, createdAt) VALUES ('job-1', 100)")
        }

        // Opening through Room runs MIGRATION_1_2..4_5 and then validates the
        // migrated schema against the generated Room model — a schema mismatch
        // throws IllegalStateException before any query runs.
        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()
        try {
            runBlocking {
                val dao = database.jobDao()
                val migrated = dao.getJobById("job-1")
                assertNotNull(migrated)
                assertEquals("done", migrated!!.status)
                assertEquals(100L, migrated.createdAt)
                assertEquals(emptyList<String>(), dao.getPendingDeleteIds())
                // Lineage columns are new in v5 and are never backfilled for
                // pre-existing rows (per FR-006 — no retroactive tracking).
                assertEquals(null, migrated.sourceSha256)
                assertEquals(null, migrated.resultSha256)
                assertEquals(null, migrated.lineageRootId)
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
