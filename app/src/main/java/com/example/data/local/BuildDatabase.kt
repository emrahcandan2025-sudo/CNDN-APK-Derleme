package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BuildEntity::class, DownloadRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BuildDatabase : RoomDatabase() {
    abstract fun buildDao(): BuildDao

    companion object {
        @Volatile
        private var INSTANCE: BuildDatabase? = null

        fun getDatabase(context: Context): BuildDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BuildDatabase::class.java,
                    "cndn_build_center.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
