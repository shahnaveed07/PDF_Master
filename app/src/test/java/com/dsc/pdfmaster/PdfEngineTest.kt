package com.dsc.pdfmaster

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
    }

    private fun createTestPdf(file: File, pageCount: Int): File {
        val doc = PDDocument()
        for (i in 1..pageCount) {
            doc.addPage(PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
        }
        doc.save(file)
        doc.close()
        return file
    }

    @Test
    fun testMergePdfs() {
        runBlocking {
            val file1 = createTestPdf(File(context.cacheDir, "merge1.pdf"), 2)
            val file2 = createTestPdf(File(context.cacheDir, "merge2.pdf"), 3)

            val merged = PdfEngine.mergePdfs(context, listOf(file1, file2), "merged_out.pdf")

            assertNotNull(merged)
            assertTrue(merged!!.exists())

            val doc = PDDocument.load(merged)
            assertEquals(5, doc.numberOfPages)
            doc.close()

            file1.delete()
            file2.delete()
            merged.delete()
        }
    }

    @Test
    fun testSplitPdf() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "split_src.pdf"), 4)

            // Split pages 1 and 3 (0-indexed 0 and 2)
            val split = PdfEngine.splitPdf(context, source, listOf(0, 2), "split_out.pdf")

            assertNotNull(split)
            assertTrue(split!!.exists())

            val doc = PDDocument.load(split)
            assertEquals(2, doc.numberOfPages)
            doc.close()

            source.delete()
            split.delete()
        }
    }

    @Test
    fun testOrganizePages() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "organize_src.pdf"), 3)

            // Page 0 rotated 90 deg, Page 1 deleted (isSelected = true), Page 2 kept
            val dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val items = listOf(
                PdfPageItem(pageIndex = 0, bitmap = dummyBitmap, rotationDegrees = 90, isSelected = false),
                PdfPageItem(pageIndex = 1, bitmap = dummyBitmap, rotationDegrees = 0, isSelected = true),
                PdfPageItem(pageIndex = 2, bitmap = dummyBitmap, rotationDegrees = 0, isSelected = false)
            )

            val organized = PdfEngine.organizePages(context, source, items, "organized_out.pdf")

            assertNotNull(organized)
            assertTrue(organized!!.exists())

            val doc = PDDocument.load(organized)
            assertEquals(2, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            doc.close()

            source.delete()
            organized.delete()
            dummyBitmap.recycle()
        }
    }

    @Test
    fun testRealPasswordProtectionAndVerification() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "protect_src.pdf"), 2)
            val password = "SecretMasterPassword123"

            val protectedFile = PdfEngine.protectPdf(context, source, password, password, "protected_out.pdf")

            assertNotNull(protectedFile)
            assertTrue(protectedFile!!.exists())

            // Check if password protected
            val isProtected = PdfEngine.checkPasswordProtected(context, protectedFile)
            assertTrue(isProtected)

            // Wrong password should fail verification
            val wrongPassResult = PdfEngine.verifyPdfPassword(context, protectedFile, "WrongPassword")
            assertFalse(wrongPassResult)

            // Correct password should succeed
            val correctPassResult = PdfEngine.verifyPdfPassword(context, protectedFile, password)
            assertTrue(correctPassResult)

            source.delete()
            protectedFile.delete()
        }
    }

    @Test
    fun testInPlacePasswordProtection() {
        runBlocking {
            val managedDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
            val source = createTestPdf(File(managedDir, "managed_doc.pdf"), 2)
            val password = "InPlacePassword456"

            val resultFile = PdfEngine.protectPdf(
                context = context,
                pdfFile = source,
                userPassword = password,
                ownerPassword = password,
                inPlace = true
            )

            assertNotNull(resultFile)
            assertEquals(source.absolutePath, resultFile!!.absolutePath)
            assertTrue(PdfEngine.checkPasswordProtected(context, resultFile))
            assertTrue(PdfEngine.verifyPdfPassword(context, resultFile, password))

            source.delete()
        }
    }

    @Test
    fun testFailedEncryptionPreservesOriginal() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "unmodified.pdf"), 2)
            val originalLength = source.length()

            // Passing empty password should fail and return null
            val resultFile = PdfEngine.protectPdf(
                context = context,
                pdfFile = source,
                userPassword = "",
                ownerPassword = "",
                inPlace = true
            )

            assertTrue(resultFile == null)
            assertTrue(source.exists())
            assertEquals(originalLength, source.length())

            source.delete()
        }
    }

    @Test
    fun testCompressPdf() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "compress_src.pdf"), 2)

            val compressed = PdfEngine.compressPdf(
                context = context,
                pdfFile = source,
                level = PdfCompressionLevel.MEDIUM,
                outputFileName = "compressed_out.pdf"
            )

            assertNotNull(compressed)
            assertTrue(compressed!!.exists())
            assertTrue(compressed.length() > 0)

            source.delete()
            compressed.delete()
        }
    }

    @Test
    fun testRemovePasswordSuccess() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "unprotect_src.pdf"), 2)
            val pass = "PasswordToClear789"
            val protectedFile = PdfEngine.protectPdf(context, source, pass, pass, "to_clear.pdf")

            assertNotNull(protectedFile)
            assertTrue(PdfEngine.checkPasswordProtected(context, protectedFile!!))

            val unlockedFile = PdfEngine.removePassword(context, protectedFile, pass, "unprotected_out.pdf")

            assertNotNull(unlockedFile)
            assertTrue(unlockedFile!!.exists())
            assertFalse(PdfEngine.checkPasswordProtected(context, unlockedFile))

            source.delete()
            protectedFile.delete()
            unlockedFile.delete()
        }
    }

    @Test
    fun testRemovePasswordWrongPassword() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "unprotect_wrong.pdf"), 2)
            val pass = "RealPass123"
            val protectedFile = PdfEngine.protectPdf(context, source, pass, pass, "to_clear_wrong.pdf")

            assertNotNull(protectedFile)

            val result = PdfEngine.removePassword(context, protectedFile!!, "WrongPass456", "should_fail.pdf")

            assertTrue(result == null)
            assertTrue(protectedFile.exists())
            assertTrue(PdfEngine.checkPasswordProtected(context, protectedFile))

            source.delete()
            protectedFile.delete()
        }
    }

    @Test
    fun testConvertPdfToImages() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "pages_to_images.pdf"), 2)

            val images = PdfEngine.convertPdfToImages(context, source, outputPrefix = "test_img")

            assertEquals(2, images.size)
            assertTrue(images.all { it.exists() && it.length() > 0 })

            source.delete()
            images.forEach { it.delete() }
        }
    }

    @Test
    fun testCreateTextPdf() {
        runBlocking {
            val textContent = "Hello PDF Master!\nLine 2 of sample document.\nLine 3 of text."
            val pdfFile = PdfEngine.createTextPdf(context, textContent, outputFileName = "sample_text.pdf")

            assertNotNull(pdfFile)
            assertTrue(pdfFile!!.exists())

            val extractedText = PdfEngine.extractTextFromPdf(context, pdfFile)
            assertNotNull(extractedText)
            assertTrue(extractedText!!.exists())
            assertTrue(extractedText.readText().contains("Hello PDF Master!"))

            pdfFile.delete()
            extractedText.delete()
        }
    }
}
