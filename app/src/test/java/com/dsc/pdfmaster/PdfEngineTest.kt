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
@Config(sdk = [34])
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

    @Test
    fun testReorderAndRotatePagesPreservesContentOrder() {
        runBlocking {
            val source = File(context.cacheDir, "reorder_rotate_src.pdf")
            PDDocument().use { doc ->
                listOf("PAGE_ONE_ALPHA", "PAGE_TWO_BETA", "PAGE_THREE_GAMMA").forEach { label ->
                    val page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    doc.addPage(page)
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                        cs.beginText()
                        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 14f)
                        cs.newLineAtOffset(50f, 700f)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                doc.save(source)
            }

            // Reorder pages to [2, 0, 1] and rotate [180, 90, 270]
            val items = listOf(
                PdfPageItem(pageIndex = 2, bitmap = null, rotationDegrees = 180, isSelected = false),
                PdfPageItem(pageIndex = 0, bitmap = null, rotationDegrees = 90, isSelected = false),
                PdfPageItem(pageIndex = 1, bitmap = null, rotationDegrees = 270, isSelected = false)
            )

            val reordered = PdfEngine.organizePages(context, source, items, "reordered_rotated_out.pdf")
            assertNotNull(reordered)
            assertTrue(reordered!!.exists())

            PDDocument.load(reordered).use { doc ->
                assertEquals(3, doc.numberOfPages)
                assertEquals(180, doc.getPage(0).rotation)
                assertEquals(90, doc.getPage(1).rotation)
                assertEquals(270, doc.getPage(2).rotation)

                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = 1
                val p1Text = stripper.getText(doc).replace(Regex("\\s+"), "")
                assertTrue("Page 1 text was: $p1Text", p1Text.contains("PAGE_THREE_GAMMA"))

                stripper.startPage = 2
                stripper.endPage = 2
                val p2Text = stripper.getText(doc).replace(Regex("\\s+"), "")
                assertTrue("Page 2 text was: $p2Text", p2Text.contains("PAGE_ONE_ALPHA"))

                stripper.startPage = 3
                stripper.endPage = 3
                val p3Text = stripper.getText(doc).replace(Regex("\\s+"), "")
                assertTrue("Page 3 text was: $p3Text", p3Text.contains("PAGE_TWO_BETA"))
            }

            source.delete()
            reordered.delete()
        }
    }

    @Test
    fun testEncryptDecryptRoundTrip128And256Bit() {
        runBlocking {
            for (keyBits in listOf(128, 256)) {
                val source = PdfEngine.createTextPdf(
                    context = context,
                    textContent = "Confidential Payload KeyLength=$keyBits",
                    outputFileName = "roundtrip_src_$keyBits.pdf"
                )
                assertNotNull(source)

                val pass = "RoundTrip#$keyBits!Pass"
                val ownerPass = "Owner#$keyBits!Pass"
                val protectRes = PdfEngine.protectPdfDetailed(
                    context = context,
                    pdfFile = source!!,
                    userPassword = pass,
                    confirmPassword = pass,
                    ownerPassword = ownerPass,
                    confirmOwnerPassword = ownerPass,
                    outputName = "roundtrip_enc_$keyBits.pdf",
                    keyLengthBits = keyBits,
                    inPlace = false
                )
                assertTrue(protectRes is PdfEngine.ProtectResult.Success)
                val encryptedFile = (protectRes as PdfEngine.ProtectResult.Success).file
                assertEquals(keyBits, protectRes.keyLengthBits)
                assertTrue(PdfEngine.checkPasswordProtected(context, encryptedFile))

                val unprotectRes = PdfEngine.removePasswordDetailed(
                    context = context,
                    pdfFile = encryptedFile,
                    currentPassword = pass,
                    outputName = "roundtrip_dec_$keyBits.pdf",
                    inPlace = false
                )
                assertTrue(unprotectRes is PdfEngine.UnprotectResult.Success)
                val decryptedFile = (unprotectRes as PdfEngine.UnprotectResult.Success).file
                assertFalse(PdfEngine.checkPasswordProtected(context, decryptedFile))

                PDDocument.load(decryptedFile).use { doc ->
                    assertEquals(1, doc.numberOfPages)
                    val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                    assertTrue(text.contains("Confidential Payload KeyLength=$keyBits"))
                }

                source.delete()
                encryptedFile.delete()
                decryptedFile.delete()
            }
        }
    }
}
