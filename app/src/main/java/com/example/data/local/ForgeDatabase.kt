package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectConfigEntity::class,
        BuildRecordEntity::class,
        SigningKeyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectConfigDao
    abstract fun buildDao(): BuildRecordDao
    abstract fun signingKeyDao(): SigningKeyDao

    companion object {
        @Volatile
        private var INSTANCE: ForgeDatabase? = null

        fun getInstance(context: Context): ForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ForgeDatabase::class.java,
                    "apkforge_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
