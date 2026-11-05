package com.dsc.pdfmaster

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.PdfDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private lateinit var context: Context
    private val testDbName = "pdf_master_migration_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
    }

    private fun createVersion1Database(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_pdfs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `lastAccessedTimestamp` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `thumbnailPath` TEXT
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Handled by Room MIGRATION_1_2 under test
            }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `migrate1To2 deduplicates filePaths preserving max timestamp and favorite status and passes Room v2 validation`() = runBlocking {
        // 1. Create a v1 database and insert rows with duplicate filePath entries
        val helper = createVersion1Database()
        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO `recent_pdfs` (`filePath`, `fileName`, `fileSizeBytes`, `pageCount`, `lastAccessedTimestamp`, `isFavorite`, `thumbnailPath`)
                VALUES ('/data/user/0/com.dsc.pdfmaster/files/report.pdf', 'report.pdf', 12000, 4, 1000, 1, NULL)
                """.trimIndent()
            )
            // Duplicate filePath with newer timestamp (2500) and isFavorite = 0 -> MAX(lastAccessedTimestamp)=2500, MAX(isFavorite)=1
            db.execSQL(
                """
                INSERT INTO `recent_pdfs` (`filePath`, `fileName`, `fileSizeBytes`, `pageCount`, `lastAccessedTimestamp`, `isFavorite`, `thumbnailPath`)
                VALUES ('/data/user/0/com.dsc.pdfmaster/files/report.pdf', 'report.pdf', 12000, 4, 2500, 0, '/cache/thumb1.png')
                """.trimIndent()
            )
            // Second distinct document
            db.execSQL(
                """
                INSERT INTO `recent_pdfs` (`filePath`, `fileName`, `fileSizeBytes`, `pageCount`, `lastAccessedTimestamp`, `isFavorite`, `thumbnailPath`)
                VALUES ('/data/user/0/com.dsc.pdfmaster/files/invoice.pdf', 'invoice.pdf', 8400, 2, 1800, 0, NULL)
                """.trimIndent()
            )
            assertEquals(1, db.version)
        }
        helper.close()

        // 2. Open database with Room at version 3 using MIGRATION_1_2 and MIGRATION_2_3
        val migratedDb = Room.databaseBuilder(
            context,
            PdfDatabase::class.java,
            testDbName
        )
            .addMigrations(PdfDatabase.MIGRATION_1_2, PdfDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDb.recentPdfDao()
            val allItems = dao.getAllRecentPdfs().first()

            // Duplicate '/data/user/0/com.dsc.pdfmaster/files/report.pdf' rows must be collapsed into 1 row
            assertEquals(2, allItems.size)

            // Ordered by lastAccessedTimestamp DESC -> report.pdf (2500) first, invoice.pdf (1800) second
            val report = allItems[0]
            assertEquals("/data/user/0/com.dsc.pdfmaster/files/report.pdf", report.filePath)
            assertEquals("report.pdf", report.fileName)
            assertEquals(12000L, report.fileSizeBytes)
            assertEquals(4, report.pageCount)
            assertEquals(2500L, report.lastAccessedTimestamp)
            assertTrue("MAX(isFavorite) must preserve favorite status across duplicate rows", report.isFavorite)
            assertEquals(false, report.isPinned)
            assertEquals(0, report.lastReadPage)

            val invoice = allItems[1]
            assertEquals("/data/user/0/com.dsc.pdfmaster/files/invoice.pdf", invoice.filePath)
            assertEquals(1800L, invoice.lastAccessedTimestamp)
            assertEquals(false, invoice.isFavorite)
            assertNull(invoice.thumbnailPath)
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `exported Room schema files exist for version 1 and version 2`() {
        val schemaDir = File("schemas/com.dsc.pdfmaster.data.database.PdfDatabase")
            .takeIf { it.exists() }
            ?: File("app/schemas/com.dsc.pdfmaster.data.database.PdfDatabase")

        assertTrue("Schema directory must exist at ${schemaDir.absolutePath}", schemaDir.exists() && schemaDir.isDirectory)
        val v1Schema = File(schemaDir, "1.json")
        val v2Schema = File(schemaDir, "2.json")
        assertTrue("Version 1 schema JSON must exist", v1Schema.exists() && v1Schema.length() > 0)
        assertTrue("Version 2 schema JSON must be exported by Room KSP", v2Schema.exists() && v2Schema.length() > 0)
        val v2Content = v2Schema.readText()
        assertTrue(v2Content.contains("\"version\": 2"))
        assertTrue(v2Content.contains("\"tableName\": \"recent_pdfs\""))
    }
}
