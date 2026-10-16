package com.dsc.pdfmaster.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecentPdfEntity::class], version = 2, exportSchema = false)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun recentPdfDao(): RecentPdfDao

    companion object {
        @Volatile
        private var INSTANCE: PdfDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recent_pdfs_new` (
                        `filePath` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `lastAccessedTimestamp` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `thumbnailPath` TEXT,
                        PRIMARY KEY(`filePath`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT OR REPLACE INTO `recent_pdfs_new` (`filePath`, `fileName`, `fileSizeBytes`, `pageCount`, `lastAccessedTimestamp`, `isFavorite`, `thumbnailPath`)
                    SELECT `filePath`, `fileName`, `fileSizeBytes`, `pageCount`, MAX(`lastAccessedTimestamp`), MAX(`isFavorite`), `thumbnailPath`
                    FROM `recent_pdfs`
                    GROUP BY `filePath`
                """.trimIndent())

                db.execSQL("DROP TABLE `recent_pdfs`")
                db.execSQL("ALTER TABLE `recent_pdfs_new` RENAME TO `recent_pdfs`")
            }
        }

        fun getDatabase(context: Context): PdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfDatabase::class.java,
                    "pdf_master_db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
