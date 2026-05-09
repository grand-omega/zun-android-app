package dev.zun.flux.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JobEntity::class, PendingDeleteEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private const val DB_NAME = "flux_database"
        private const val MIGRATION_PREFS = "flux_db_migration_meta"
        private const val UNENCRYPTED_KEY = "migrated_to_plain_v1"

        fun getDatabase(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            wipeIfStillEncrypted(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        /**
         * On first launch after the SQLCipher → plain Room switch, any
         * existing on-disk DB file is encrypted and Room can't open it.
         * The data is a local cache of server state (jobs + pending deletes),
         * so wipe and let it re-sync. A SharedPref sentinel makes this
         * one-shot.
         */
        private fun wipeIfStillEncrypted(context: Context) {
            val meta = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
            if (meta.getBoolean(UNENCRYPTED_KEY, false)) return
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                dbFile.delete()
                java.io.File(dbFile.absolutePath + "-journal").delete()
                java.io.File(dbFile.absolutePath + "-wal").delete()
                java.io.File(dbFile.absolutePath + "-shm").delete()
            }
            meta.edit { putBoolean(UNENCRYPTED_KEY, true) }
        }

        val MIGRATION_1_2 = schemaMigration(1, 2)
        val MIGRATION_2_3 = schemaMigration(2, 3)
        val MIGRATION_3_4 = schemaMigration(3, 4)

        private fun schemaMigration(startVersion: Int, endVersion: Int): Migration = object : Migration(startVersion, endVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureCurrentSchema(db)
            }
        }

        private fun ensureCurrentSchema(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT NOT NULL PRIMARY KEY,
                    status TEXT NOT NULL DEFAULT 'done',
                    inputId INTEGER,
                    promptId INTEGER,
                    promptText TEXT,
                    workflow TEXT,
                    seed INTEGER,
                    progress REAL,
                    error TEXT,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    startedAt INTEGER,
                    completedAt INTEGER,
                    durationSeconds INTEGER,
                    width INTEGER,
                    height INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_deletes (
                    jobId TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            val jobColumns = columnNames(db, "jobs")
            addColumnIfMissing(db, jobColumns, "jobs", "status TEXT NOT NULL DEFAULT 'done'")
            addColumnIfMissing(db, jobColumns, "jobs", "inputId INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "promptId INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "promptText TEXT")
            addColumnIfMissing(db, jobColumns, "jobs", "workflow TEXT")
            addColumnIfMissing(db, jobColumns, "jobs", "seed INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "progress REAL")
            addColumnIfMissing(db, jobColumns, "jobs", "error TEXT")
            addColumnIfMissing(db, jobColumns, "jobs", "createdAt INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, jobColumns, "jobs", "startedAt INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "completedAt INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "durationSeconds INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "width INTEGER")
            addColumnIfMissing(db, jobColumns, "jobs", "height INTEGER")

            val pendingDeleteColumns = columnNames(db, "pending_deletes")
            addColumnIfMissing(db, pendingDeleteColumns, "pending_deletes", "createdAt INTEGER NOT NULL DEFAULT 0")
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            existingColumns: Set<String>,
            tableName: String,
            declaration: String,
        ) {
            val columnName = declaration.substringBefore(' ')
            if (columnName !in existingColumns) {
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $declaration")
            }
        }

        private fun columnNames(
            db: SupportSQLiteDatabase,
            tableName: String,
        ): Set<String> {
            val cursor = db.query("PRAGMA table_info($tableName)")
            return cursor.use {
                buildSet {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        add(it.getString(nameIndex))
                    }
                }
            }
        }
    }
}
