package com.rakshak.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingMatchEntity::class, MeshAlertEntity::class, MeshSeenEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingMatchDao(): PendingMatchDao

    abstract fun meshDao(): MeshDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rakshak.db",
            )
                // Everything here is transient mesh/queue state that the flood or
                // the sync worker rebuilds — destructive migration is fine.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { instance = it }
        }
    }
}
