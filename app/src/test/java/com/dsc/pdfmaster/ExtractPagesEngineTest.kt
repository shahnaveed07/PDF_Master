package com.dsc.pdfmaster

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtractPagesEngineTest {

    @Test
    fun `getUniqueOutputFile prevents overwriting existing files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "test_unique_names").apply { mkdirs() }

        val file1 = File(dir, "report.pdf").apply { writeText("original") }
        val unique1 = PdfEngine.getUniqueOutputFile(dir, "report.pdf")
        assertEquals("report (1).pdf", unique1.name)

        val file2 = File(dir, "report (1).pdf").apply { writeText("copy 1") }
        val unique2 = PdfEngine.getUniqueOutputFile(dir, "report.pdf")
        assertEquals("report (2).pdf", unique2.name)

        dir.deleteRecursively()
    }

    @Test
    fun `extractPages fails gracefully on non-existent file`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fakeFile = File(context.cacheDir, "does_not_exist.pdf")
            val result = PdfEngine.extractPages(context, fakeFile, listOf(0), "out.pdf")

            assertTrue(result is PdfEngine.ExtractResult.Error)
        }
    }

    @Test
    fun `extractPages fails gracefully on empty page indices`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dummy = File(context.cacheDir, "dummy.pdf").apply { writeText("dummy") }
            val result = PdfEngine.extractPages(context, dummy, emptyList(), "out.pdf")

            assertTrue(result is PdfEngine.ExtractResult.Error)
            dummy.delete()
        }
    }

    @Test
    fun `extractPages creates a valid new PDF from source pages`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val sourceFile = File(context.cacheDir, "source_3pages.pdf")

            // Create a 3-page test PDF document using PDFBox
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
            for (p in 1..3) {
                val page = com.tom_roush.pdfbox.pdmodel.PDPage()
                doc.addPage(page)
            }
            doc.save(sourceFile)
            doc.close()

            assertTrue(sourceFile.exists() && sourceFile.length() > 0)

            // Extract pages 0 and 2 (pages 1 and 3)
            val result = PdfEngine.extractPages(
                context = context,
                pdfFile = sourceFile,
                pageIndices = listOf(0, 2),
                outputFileName = "extracted_1_and_3.pdf"
            )

            assertTrue("Expected ExtractResult.Success but was $result", result is PdfEngine.ExtractResult.Success)
            val success = result as PdfEngine.ExtractResult.Success
            assertEquals(2, success.pageCount)
            assertTrue(success.file.exists())
            assertTrue(success.file.length() > 0)

            sourceFile.delete()
            success.file.delete()
        }
    }
}
