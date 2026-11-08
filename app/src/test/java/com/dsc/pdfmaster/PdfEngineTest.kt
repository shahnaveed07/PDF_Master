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

    private fun createTestPdfWithImage(file: File): File {
        val doc = PDDocument()
        val page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
        doc.addPage(page)
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        for (y in 0 until 400 step 4) {
            for (x in 0 until 400 step 4) {
                bmp.setPixel(x, y, android.graphics.Color.rgb((x * 37) % 255, (y * 53) % 255, ((x + y) * 19) % 255))
            }
        }
        val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
        bmp.recycle()
        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImage, 50f, 200f, 300f, 300f)
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
            val source = createTestPdfWithImage(File(context.cacheDir, "compress_src.pdf"))
            val originalSize = source.length()

            val compressed = PdfEngine.compressPdf(
                context = context,
                pdfFile = source,
                level = PdfCompressionLevel.MEDIUM,
                outputFileName = "compressed_out.pdf"
            )

            assertNotNull(compressed)
            assertTrue(compressed!!.exists())
            assertTrue(compressed.length() > 0)
            assertTrue("Expected compressed (${compressed.length()}) < original ($originalSize)", compressed.length() < originalSize)

            source.delete()
            compressed.delete()
        }
    }

    @Test
    fun testCompressPdfAlreadyOptimalReturnsNoReductionAndDoesNotOverwrite() {
        runBlocking {
            val source = createTestPdf(File(context.cacheDir, "compress_optimal.pdf"), 2)

            val detailedResult = PdfEngine.compressPdfDetailed(
                context = context,
                pdfFile = source,
                level = PdfCompressionLevel.MEDIUM,
                outputFileName = "compress_optimal_out.pdf"
            )

            assertTrue("Expected NoReduction, got: $detailedResult", detailedResult is PdfEngine.CompressResult.NoReduction)

            val compressedFile = PdfEngine.compressPdf(
                context = context,
                pdfFile = source,
                level = PdfCompressionLevel.MEDIUM,
                outputFileName = "compress_optimal_out_2.pdf"
            )

            org.junit.Assert.assertNull(compressedFile)
            assertFalse(File(context.filesDir, "output_pdfs/compress_optimal_out_2.pdf").exists())

            source.delete()
        }
    }

    @Test
    fun testCompressPdfCorruptedFailsSafely() {
        runBlocking {
            val corrupted = File(context.cacheDir, "corrupted.pdf").apply { writeText("NOT_A_VALID_PDF") }
            val res = PdfEngine.compressPdfDetailed(
                context = context,
                pdfFile = corrupted,
                level = PdfCompressionLevel.MEDIUM,
                outputFileName = "corrupted_out.pdf"
            )
            assertTrue(res is PdfEngine.CompressResult.Error)
            val fileRes = PdfEngine.compressPdf(context, corrupted, PdfCompressionLevel.MEDIUM, "corrupted_out_2.pdf")
            org.junit.Assert.assertNull(fileRes)
            corrupted.delete()
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

    @Test
    fun testCalculateSafeImageSampleSizeIndependentDimensions() {
        // Extreme portrait: width fits in 2048, but height = 16384
        val sampleSizePortrait = PdfEngine.calculateSafeImageSampleSize(1000, 16384, maxDimension = 2048)
        assertTrue("Sample size should be at least 8 to downsample 16384 below 2048", sampleSizePortrait >= 8)
        assertTrue(16384 / sampleSizePortrait <= 2048)

        // Extreme landscape: height fits in 2048, but width = 16384
        val sampleSizeLandscape = PdfEngine.calculateSafeImageSampleSize(16384, 1000, maxDimension = 2048)
        assertTrue("Sample size should be at least 8 to downsample 16384 below 2048", sampleSizeLandscape >= 8)
        assertTrue(16384 / sampleSizeLandscape <= 2048)

        // Small image within budget
        val sampleSizeSmall = PdfEngine.calculateSafeImageSampleSize(800, 600, maxDimension = 2048)
        assertEquals(1, sampleSizeSmall)

        // Total pixel budget enforcement: 5000 x 5000 = 25M pixels (> 16M budget)
        val sampleSizePixelBudget = PdfEngine.calculateSafeImageSampleSize(4000, 4000, maxDimension = 4096, maxPixelBudget = 16_000_000L)
        assertTrue(sampleSizePixelBudget >= 1)
        val decodedW = 4000L / sampleSizePixelBudget
        val decodedH = 4000L / sampleSizePixelBudget
        assertTrue(decodedW * decodedH <= 16_000_000L)
    }

    @Test
    fun testIsSafeBitmapAllocation() {
        // Safe allocation (1000x1000 ARGB = 4MB)
        assertTrue(PdfEngine.isSafeBitmapAllocation(1000, 1000))

        // Negative or zero dimensions
        assertFalse(PdfEngine.isSafeBitmapAllocation(0, 1000))
        assertFalse(PdfEngine.isSafeBitmapAllocation(-10, 1000))

        // Overflow: 1,000,000 x 1,000,000 pixels
        assertFalse(PdfEngine.isSafeBitmapAllocation(1_000_000, 1_000_000))

        // Exceeds maxBytes (e.g. 5000x5000 = 25M pixels * 4 = 100MB > 64MB)
        assertFalse(PdfEngine.isSafeBitmapAllocation(5000, 5000, maxBytes = 64L * 1024 * 1024))
    }

    @Test
    fun testSplitPdfRejectsOutOfBoundsPages() {
        runBlocking {
            val testPdf = createTestPdf(File(context.cacheDir, "split_bounds_test.pdf"), 3)
            // Request page index 5 when total pages is 3 -> must return null
            val res = PdfEngine.splitPdf(context, testPdf, listOf(0, 5), "split_out_invalid.pdf")
            org.junit.Assert.assertNull("splitPdf must reject out-of-bounds indices", res)

            // Valid split: page index 0 and 2
            val validRes = PdfEngine.splitPdf(context, testPdf, listOf(0, 2), "split_out_valid.pdf")
            assertNotNull(validRes)
            assertTrue(validRes!!.exists())
            PDDocument.load(validRes).use { doc ->
                assertEquals(2, doc.numberOfPages)
            }

            testPdf.delete()
            validRes.delete()
        }
    }

    @Test
    fun testMultilingualUnicodeTextPdfGeneration() {
        runBlocking {
            val scripts = listOf(
                "English" to "The quick brown fox jumps over the lazy dog. 1234567890.",
                "Hindi" to "नमस्ते दुनिया! पीडीएफ मास्टर में आपका स्वागत है। यह एक परीक्षण दस्तावेज़ है।",
                "Urdu" to "پی ڈی ایف ماسٹر میں خوش آمدید۔ یہ ایک کثیر لسانی دستاویز ہے۔",
                "Arabic" to "مرحبا بكم في تطبيق بي دي اف ماستر. هذا مستند اختبار باللغة العربية.",
                "Mixed" to "Hello नमस्ते World दुनिया! Symbols: € £ ¥ ₹ © ® § ± × ÷",
                "LineBreaks" to "First paragraph.\n\n\nSecond paragraph with multiple blank lines.\n\nEnd."
            )

            for ((name, content) in scripts) {
                val pdf = PdfEngine.createTextPdf(
                    context = context,
                    textContent = content,
                    outputFileName = "text_test_$name.pdf"
                )
                assertNotNull("PDF creation failed for $name", pdf)
                assertTrue("Generated PDF should exist for $name", pdf!!.exists())
                assertTrue("Generated PDF should be valid for $name", PdfEngine.validateGeneratedPdfFile(context, pdf, expectedMinPages = 1))

                PDDocument.load(pdf).use { doc ->
                    assertTrue("Expected at least 1 page for $name", doc.numberOfPages >= 1)
                }
                pdf.delete()
            }

            // Multi-page document test
            val longContent = buildString {
                for (p in 1..40) {
                    appendLine("Paragraph $p: Multilingual test paragraph with Hindi नमस्ते, Urdu خوش آمدید, Arabic مرحبا, and English text repeated across multiple pages to verify proper page breaking and font layout.")
                    appendLine()
                }
            }
            val multiPagePdf = PdfEngine.createTextPdf(
                context = context,
                textContent = longContent,
                outputFileName = "text_test_multipage.pdf"
            )
            assertNotNull("Multi-page PDF creation failed", multiPagePdf)
            assertTrue(multiPagePdf!!.exists())
            assertTrue(PdfEngine.validateGeneratedPdfFile(context, multiPagePdf, expectedMinPages = 2))

            PDDocument.load(multiPagePdf).use { doc ->
                assertTrue("Expected multiple pages, got ${doc.numberOfPages}", doc.numberOfPages >= 2)
            }
            multiPagePdf.delete()
        }
    }

    @Test
    fun testMergePdfsRejectsCorruptedOrMissingFileWithoutSilentlySkipping() {
        runBlocking {
            val pdf1 = createTestPdf(File(context.cacheDir, "merge_test_1.pdf"), 2)
            val pdf2 = createTestPdf(File(context.cacheDir, "merge_test_2.pdf"), 3)
            val corruptFile = File(context.cacheDir, "corrupt_file.pdf").apply {
                writeText("Not a valid PDF header content")
            }

            // Merge with corrupted file must fail completely (not silently skip corrupt file)
            val failedMerge = PdfEngine.mergePdfs(context, listOf(pdf1, corruptFile, pdf2), "merge_failed.pdf")
            org.junit.Assert.assertNull("mergePdfs must reject merge if any input file is corrupted", failedMerge)

            // Valid merge of pdf1 (2 pages) + pdf2 (3 pages) must yield exactly 5 pages
            val successfulMerge = PdfEngine.mergePdfs(context, listOf(pdf1, pdf2), "merge_success.pdf")
            assertNotNull(successfulMerge)
            assertTrue(successfulMerge!!.exists())
            PDDocument.load(successfulMerge).use { doc ->
                assertEquals("Merged PDF page count must equal exact sum of input document page counts", 5, doc.numberOfPages)
            }

            pdf1.delete()
            pdf2.delete()
            corruptFile.delete()
            successfulMerge.delete()
        }
    }

    @Test
    fun testExtremeAspectRatiosSafeDownsampling() {
        // Extreme height (50 x 12000)
        val s1 = PdfEngine.calculateSafeImageSampleSize(50, 12000, maxDimension = 2048)
        assertTrue("Sample size should scale down extreme height", s1 >= 4)
        assertTrue(12000 / s1 <= 2048)

        // Extreme width (12000 x 50)
        val s2 = PdfEngine.calculateSafeImageSampleSize(12000, 50, maxDimension = 2048)
        assertTrue("Sample size should scale down extreme width", s2 >= 4)
        assertTrue(12000 / s2 <= 2048)

        // Gigantic dimensions (20000 x 20000)
        val s3 = PdfEngine.calculateSafeImageSampleSize(20000, 20000, maxDimension = 2048, maxPixelBudget = 16_000_000L)
        assertTrue(s3 >= 8)
        val w = 20000L / s3
        val h = 20000L / s3
        assertTrue(w * h <= 16_000_000L)
    }
}
