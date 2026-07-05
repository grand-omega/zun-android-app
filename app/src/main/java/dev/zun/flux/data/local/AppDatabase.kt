package dev.zun.flux.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JobEntity::class, PendingDeleteEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private const val DB_NAME = "flux_database"

        fun getDatabase(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

        val MIGRATION_1_2 = schemaMigration(1, 2)
        val MIGRATION_2_3 = schemaMigration(2, 3)
        val MIGRATION_3_4 = schemaMigration(3, 4)
        val MIGRATION_4_5 = schemaMigration(4, 5)

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
                    height INTEGER,
                    sourceSha256 TEXT,
                    resultSha256 TEXT,
                    lineageRootId TEXT
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
            addColumnIfMissing(db, jobColumns, "jobs", "sourceSha256 TEXT")
            addColumnIfMissing(db, jobColumns, "jobs", "resultSha256 TEXT")
            addColumnIfMissing(db, jobColumns, "jobs", "lineageRootId TEXT")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_sourceSha256 ON jobs(sourceSha256)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_resultSha256 ON jobs(resultSha256)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_lineageRootId ON jobs(lineageRootId)")

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
