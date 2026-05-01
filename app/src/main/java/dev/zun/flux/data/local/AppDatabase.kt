package dev.zun.flux.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [JobEntity::class, PendingDeleteEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = instance ?: synchronized(this) {
            val createdInstance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flux_database",
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
            instance = createdInstance
            createdInstance
        }
    }
}
