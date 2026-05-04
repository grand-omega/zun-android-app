package dev.zun.flux.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To4_preservesJobRowsAndCreatesPendingDeletes() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO jobs (id, createdAt) VALUES ('job-1', 100)")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
        ).apply {
            query("SELECT id, status, createdAt FROM jobs WHERE id = 'job-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("job-1", cursor.getString(0))
                assertEquals("done", cursor.getString(1))
                assertEquals(100L, cursor.getLong(2))
            }
            query("SELECT COUNT(*) FROM pending_deletes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
