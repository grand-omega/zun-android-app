package dev.zun.flux.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(entities = [JobEntity::class, PendingDeleteEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private const val DB_NAME = "flux_database"

        fun getDatabase(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            // Robolectric can neither load SQLCipher's native libs nor provide
            // an AndroidKeyStore, so under JVM unit tests fall through to Room's
            // default open helper. Production + androidTest use the encrypted
            // path. The encrypt/decrypt round-trip itself is exercised in
            // AppDatabaseEncryptionMigrationTest (instrumented).
            if (!isRobolectric()) {
                val passphrase = DatabasePassphrase.get(context)
                migratePlaintextIfNeeded(context, DB_NAME, passphrase)
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
            }
            return builder.build()
        }

        private fun isRobolectric(): Boolean = runCatching {
            Class.forName("org.robolectric.RuntimeEnvironment")
        }.isSuccess

        /**
         * If the on-disk database is unencrypted (i.e. predates this version of
         * the app), re-encrypt it in place using [sqlcipher_export]. No-op if the
         * file is missing or already encrypted.
         *
         * Visible to androidTest so the migration can be exercised without
         * standing up the full Room+ServiceLocator graph.
         */
        @JvmStatic
        internal fun migratePlaintextIfNeeded(context: Context, dbName: String, passphrase: String) {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) return
            if (!isPlaintextDatabase(dbFile)) return

            val migratedFile = File(dbFile.parentFile, "$dbName.migrated")
            if (migratedFile.exists()) migratedFile.delete()

            // Base64 alphabet is `[A-Za-z0-9+/=]` — safe to embed in a SQL
            // string literal without further escaping.
            val keyLiteral = "'$passphrase'"
            val attachPathLiteral = "'${migratedFile.absolutePath.replace("'", "''")}'"

            val plain = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            try {
                plain.rawExecSQL("ATTACH DATABASE $attachPathLiteral AS encrypted KEY $keyLiteral")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                val userVersion = plain.rawQuery("PRAGMA user_version", emptyArray()).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                plain.rawExecSQL("PRAGMA encrypted.user_version = $userVersion")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plain.close()
            }

            // -journal/-wal/-shm are all artifacts of the plaintext open and
            // must be removed; the encrypted DB will create its own.
            File(dbFile.absolutePath + "-journal").delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            check(dbFile.delete()) { "Failed to delete plaintext DB during encryption migration" }
            check(migratedFile.renameTo(dbFile)) { "Failed to install encrypted DB" }
        }

        /**
         * Returns true if [dbFile] can be opened with an empty key — i.e. it's
         * a stock unencrypted SQLite file. SQLCipher returns an error when the
         * page format doesn't match (wrong-key reads fail).
         */
        private fun isPlaintextDatabase(dbFile: File): Boolean = runCatching {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray())
                    .use { it.moveToFirst() }
                true
            } finally {
                db.close()
            }
        }.getOrDefault(false)

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
