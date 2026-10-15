package com.dsc.pdfmaster.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentPdfEntity::class], version = 1, exportSchema = false)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun recentPdfDao(): RecentPdfDao

    companion object {
        @Volatile
        private var INSTANCE: PdfDatabase? = null

        fun getDatabase(context: Context): PdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfDatabase::class.java,
                    "pdf_master_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
