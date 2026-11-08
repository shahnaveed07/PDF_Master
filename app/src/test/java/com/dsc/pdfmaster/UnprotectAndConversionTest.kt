package com.dsc.pdfmaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.PdfDatabase
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnprotectAndConversionTest {

    private lateinit var context: Context
    private lateinit var database: PdfDatabase
    private lateinit var repository: PdfRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        database = Room.inMemoryDatabaseBuilder(context, PdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PdfRepository(context, database.recentPdfDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createPdfWithText(
        file: File,
        pagesText: List<String> = listOf("Page 1 Hello World", "Page 2 Testing PDF Master")
    ): File {
        PDDocument().use { doc ->
            for (text in pagesText) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                if (text.isNotEmpty()) {
                    PDPageContentStream(doc, page).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 12f)
                        cs.newLineAtOffset(50f, 750f)
                        cs.showText(text)
                        cs.endText()
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun createEncryptedPdf(
        file: File,
        userPassword: String,
        ownerPassword: String,
        allowModifications: Boolean = true,
        pageCount: Int = 2
    ): File {
        PDDocument().use { doc ->
            repeat(pageCount) { idx ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(50f, 750f)
                    cs.showText("Confidential Page ${idx + 1}")
                    cs.endText()
                }
            }
            val ap = AccessPermission().apply {
                setCanPrint(allowModifications)
                setCanModify(allowModifications)
                setCanExtractContent(allowModifications)
            }
            val spp = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                encryptionKeyLength = 128
                permissions = ap
            }
            doc.protect(spp)
            doc.save(file)
        }
        return file
    }

    // =========================================================================
    // TASK 1.5 — Unprotect / Remove Password Tests
    // =========================================================================

    @Test
    fun `unprotect with correct user password removes encryption and preserves pages`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "encrypted_user.pdf"),
            userPassword = "UserSecret123",
            ownerPassword = "OwnerSecret456",
            pageCount = 3
        )
        val originalBytes = src.readBytes()

        val state = PdfEngine.inspectPdfSecurity(context, src)
        assertTrue(state is PdfEngine.PdfSecurityState.UserPasswordRequired)

        val result = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "UserSecret123",
            outputName = "unlocked_report.pdf",
            inPlace = false
        )

        assertTrue("Expected Success but got $result", result is PdfEngine.UnprotectResult.Success)
        val success = result as PdfEngine.UnprotectResult.Success
        assertTrue(success.file.exists())
        assertNotEquals(src.absolutePath, success.file.absolutePath)
        assertEquals(3, success.pageCount)

        // Verify source is untouched and still encrypted
        assertArrayEquals(originalBytes, src.readBytes())
        assertTrue(PdfEngine.checkPasswordProtected(context, src))

        // Verify output reopens without password and is not encrypted
        PDDocument.load(success.file).use { unlockedDoc ->
            assertFalse(unlockedDoc.isEncrypted)
            assertEquals(3, unlockedDoc.numberOfPages)
        }

        src.delete()
        success.file.delete()
    }

    @Test
    fun `unprotect with incorrect password fails and leaves original byte-for-byte unchanged`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "encrypted_wrong_test.pdf"),
            userPassword = "CorrectPassword",
            ownerPassword = "CorrectPassword",
            pageCount = 2
        )
        val originalBytes = src.readBytes()

        val result = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "WrongPassword999",
            outputName = "should_not_exist.pdf",
            inPlace = true
        )

        assertTrue(result is PdfEngine.UnprotectResult.Error)
        val err = result as PdfEngine.UnprotectResult.Error
        assertEquals(PdfEngine.UnprotectError.INCORRECT_PASSWORD, err.error)

        // Original file must remain byte-for-byte identical
        assertTrue(src.exists())
        assertArrayEquals(originalBytes, src.readBytes())
        assertFalse(File(context.filesDir, "output_pdfs/should_not_exist.pdf").exists())

        src.delete()
    }

    @Test
    fun `owner-password restricted PDF with empty user password rejects blank password and unlocks with owner password`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "owner_restricted.pdf"),
            userPassword = "",
            ownerPassword = "OwnerOnlySecret!",
            allowModifications = false,
            pageCount = 2
        )
        val originalBytes = src.readBytes()

        val securityState = PdfEngine.inspectPdfSecurity(context, src)
        assertTrue(
            "Expected OwnerPasswordRestricted but was $securityState",
            securityState is PdfEngine.PdfSecurityState.OwnerPasswordRestricted
        )

        // Attempting with blank password must be rejected because owner password is required
        val blankAttempt = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "",
            outputName = "owner_blank_attempt.pdf",
            inPlace = false
        )
        assertTrue(blankAttempt is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.OWNER_PASSWORD_REQUIRED,
            (blankAttempt as PdfEngine.UnprotectResult.Error).error
        )
        assertArrayEquals(originalBytes, src.readBytes())

        // Attempting with wrong owner password must fail with INCORRECT_PASSWORD
        val wrongOwnerAttempt = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "NotTheOwnerPassword",
            outputName = "owner_wrong_attempt.pdf",
            inPlace = false
        )
        assertTrue(wrongOwnerAttempt is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.INCORRECT_PASSWORD,
            (wrongOwnerAttempt as PdfEngine.UnprotectResult.Error).error
        )

        // Attempting with valid owner password succeeds
        val validOwnerAttempt = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "OwnerOnlySecret!",
            outputName = "owner_unlocked.pdf",
            inPlace = false
        )
        assertTrue(validOwnerAttempt is PdfEngine.UnprotectResult.Success)
        val unlockedFile = (validOwnerAttempt as PdfEngine.UnprotectResult.Success).file
        PDDocument.load(unlockedFile).use { doc ->
            assertFalse(doc.isEncrypted)
            assertEquals(2, doc.numberOfPages)
        }

        src.delete()
        unlockedFile.delete()
    }

    @Test
    fun `blank-password encrypted PDF where both user and owner passwords are empty unlocks cleanly`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "blank_both.pdf"),
            userPassword = "",
            ownerPassword = "",
            allowModifications = true,
            pageCount = 2
        )

        val state = PdfEngine.inspectPdfSecurity(context, src)
        assertTrue(
            "Expected EncryptedWithBlankPassword but got $state",
            state is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword
        )

        val result = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "",
            outputName = "blank_unlocked.pdf",
            inPlace = false
        )
        assertTrue(result is PdfEngine.UnprotectResult.Success)
        val outFile = (result as PdfEngine.UnprotectResult.Success).file
        PDDocument.load(outFile).use { doc ->
            assertFalse(doc.isEncrypted)
            assertEquals(2, doc.numberOfPages)
        }

        src.delete()
        outFile.delete()
    }

    @Test
    fun `repository removePassword defaults to Save as New even for files inside managed filesDir`(): Unit = runBlocking {
        val managedDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
        val managedSource = createEncryptedPdf(
            File(managedDir, "managed_encrypted.pdf"),
            userPassword = "ManagedPass123",
            ownerPassword = "ManagedPass123",
            pageCount = 2
        )
        val originalBytes = managedSource.readBytes()

        // Calling repository.removePassword without replaceOriginal=true MUST NOT overwrite managedSource
        val outputFile = repository.removePassword(
            pdfFile = managedSource,
            password = "ManagedPass123",
            outputName = "managed_encrypted_unprotected.pdf"
        )

        assertNotNull(outputFile)
        assertNotEquals(managedSource.absolutePath, outputFile!!.absolutePath)

        // Original managed file is still encrypted and byte-for-byte intact
        assertTrue(managedSource.exists())
        assertArrayEquals(originalBytes, managedSource.readBytes())
        assertTrue(PdfEngine.checkPasswordProtected(context, managedSource))

        // New output file is unencrypted
        assertFalse(PdfEngine.checkPasswordProtected(context, outputFile))

        managedSource.delete()
        outputFile.delete()
    }

    @Test
    fun `explicit replaceOriginal safely replaces source only after validation and preserves source on failure`(): Unit = runBlocking {
        val managedDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
        val managedSource = createEncryptedPdf(
            File(managedDir, "replace_me.pdf"),
            userPassword = "ReplaceSecret",
            ownerPassword = "ReplaceSecret",
            pageCount = 2
        )
        val encryptedBytes = managedSource.readBytes()

        // 1. Failed attempt with replaceOriginal=true must leave source byte-for-byte unchanged
        val failedResult = repository.removePasswordDetailed(
            pdfFile = managedSource,
            password = "WrongPassword",
            outputName = "ignored.pdf",
            replaceOriginal = true
        )
        assertTrue(failedResult is PdfEngine.UnprotectResult.Error)
        assertArrayEquals(encryptedBytes, managedSource.readBytes())

        // 2. Output validation failure before replacement must NOT touch original file
        val validationFailedResult = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = managedSource,
            currentPassword = "ReplaceSecret",
            outputName = "replace_me.pdf",
            inPlace = true,
            outputValidator = { _, _, _ -> false }
        )
        assertTrue(validationFailedResult is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.OUTPUT_VALIDATION_FAILED,
            (validationFailedResult as PdfEngine.UnprotectResult.Error).error
        )
        assertArrayEquals(encryptedBytes, managedSource.readBytes())

        // 3. Successful attempt with replaceOriginal=true atomically replaces source with validated unencrypted PDF
        val successResult = repository.removePasswordDetailed(
            pdfFile = managedSource,
            password = "ReplaceSecret",
            outputName = "replace_me.pdf",
            replaceOriginal = true
        )
        assertTrue(successResult is PdfEngine.UnprotectResult.Success)
        val replacedFile = (successResult as PdfEngine.UnprotectResult.Success).file
        assertEquals(managedSource.absolutePath, replacedFile.absolutePath)
        assertFalse(PdfEngine.checkPasswordProtected(context, replacedFile))
        PDDocument.load(replacedFile).use { doc ->
            assertFalse(doc.isEncrypted)
            assertEquals(2, doc.numberOfPages)
        }

        managedSource.delete()
    }

    @Test
    fun `unprotect handles temp write failure and destination save failure cleanly without corrupting source`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "io_failure_test.pdf"),
            userPassword = "PassIO",
            ownerPassword = "PassIO",
            pageCount = 2
        )
        val originalBytes = src.readBytes()

        // Create a regular file where a directory is expected so tempDir/outputDir creation fails
        val blockedTempDir = File(context.cacheDir, "blocked_temp_file").apply { writeText("not_a_dir") }
        val tempWriteFailure = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "PassIO",
            outputName = "out_temp_fail.pdf",
            inPlace = false,
            tempDir = blockedTempDir
        )
        assertTrue(tempWriteFailure is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.OUTPUT_WRITE_FAILED,
            (tempWriteFailure as PdfEngine.UnprotectResult.Error).error
        )
        assertArrayEquals(originalBytes, src.readBytes())

        val blockedOutputDir = File(context.cacheDir, "blocked_output_file").apply { writeText("not_a_dir") }
        val destSaveFailure = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = src,
            currentPassword = "PassIO",
            outputName = "out_dest_fail.pdf",
            inPlace = false,
            outputDir = blockedOutputDir
        )
        assertTrue(destSaveFailure is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.OUTPUT_WRITE_FAILED,
            (destSaveFailure as PdfEngine.UnprotectResult.Error).error
        )
        assertArrayEquals(originalBytes, src.readBytes())

        blockedTempDir.delete()
        blockedOutputDir.delete()
        src.delete()
    }

    @Test
    fun `malformed or non-PDF files and already unencrypted PDFs return specific error reasons`(): Unit = runBlocking {
        val malformedFile = File(context.cacheDir, "corrupted.pdf").apply {
            writeText("NOT_A_VALID_PDF_CONTENT_AT_ALL")
        }
        val malformedState = PdfEngine.inspectPdfSecurity(context, malformedFile)
        assertTrue(malformedState is PdfEngine.PdfSecurityState.InvalidOrCorrupted)

        val malformedResult = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = malformedFile,
            currentPassword = "any",
            outputName = "out.pdf"
        )
        assertTrue(malformedResult is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.INVALID_OR_CORRUPTED_PDF,
            (malformedResult as PdfEngine.UnprotectResult.Error).error
        )

        val unencryptedFile = createPdfWithText(File(context.cacheDir, "plain.pdf"))
        val unencryptedResult = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = unencryptedFile,
            currentPassword = "any",
            outputName = "plain_out.pdf"
        )
        assertTrue(unencryptedResult is PdfEngine.UnprotectResult.Error)
        assertEquals(
            PdfEngine.UnprotectError.NOT_ENCRYPTED,
            (unencryptedResult as PdfEngine.UnprotectResult.Error).error
        )

        malformedFile.delete()
        unencryptedFile.delete()
    }

    @Test
    fun `isInsideDirectory does not treat sibling prefix directory as child directory`() {
        val baseDir = File(context.filesDir, "managed")
        val siblingDir = File(context.filesDir, "managed_sibling")
        val insideFile = File(baseDir, "doc.pdf")
        val siblingFile = File(siblingDir, "doc.pdf")

        assertTrue(PdfEngine.isInsideDirectory(insideFile, baseDir))
        assertFalse(PdfEngine.isInsideDirectory(siblingFile, baseDir))
    }

    @Test
    fun `cancelled unprotect coroutine cleans up temp files and preserves original`(): Unit = runBlocking {
        val src = createEncryptedPdf(
            File(context.cacheDir, "cancel_test.pdf"),
            userPassword = "CancelPass",
            ownerPassword = "CancelPass",
            pageCount = 3
        )
        val originalBytes = src.readBytes()

        val cancelledJob = Job().apply { cancel(CancellationException("User cancelled")) }
        var caughtCancellation = false
        try {
            kotlinx.coroutines.withContext(cancelledJob) {
                PdfEngine.removePasswordDetailed(
                    context = context,
                    pdfFile = src,
                    currentPassword = "CancelPass",
                    outputName = "cancelled_out.pdf"
                )
            }
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue(caughtCancellation)
        assertArrayEquals(originalBytes, src.readBytes())
        assertFalse(File(context.filesDir, "output_pdfs/cancelled_out.pdf").exists())
        src.delete()
    }

    // =========================================================================
    // TASK 2 — Local Conversion Features Tests
    // =========================================================================

    @Test
    fun `convertPdfToImagesDetailed exports valid PNG and JPEG files with verified magic headers`(): Unit = runBlocking {
        val src = createPdfWithText(
            File(context.cacheDir, "convert_img_src.pdf"),
            listOf("First Page Visual", "Second Page Visual")
        )

        // Test PNG export
        val pngResult = repository.convertPdfToImagesDetailed(
            pdfFile = src,
            format = Bitmap.CompressFormat.PNG,
            outputPrefix = "export_png_test"
        )
        assertTrue("Expected PNG success but got $pngResult", pngResult is PdfEngine.PdfToImagesResult.Success)
        val pngFiles = (pngResult as PdfEngine.PdfToImagesResult.Success).files
        assertEquals(2, pngFiles.size)
        pngFiles.forEach { file ->
            assertTrue(file.exists() && file.length() > 8)
            assertTrue(file.name.endsWith(".png"))
            val header = file.inputStream().use { inp -> ByteArray(4).also { inp.read(it) } }
            assertArrayEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                header
            )
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
        }

        // Test JPEG export
        val jpgResult = repository.convertPdfToImagesDetailed(
            pdfFile = src,
            format = Bitmap.CompressFormat.JPEG,
            outputPrefix = "export_jpg_test"
        )
        assertTrue("Expected JPEG success but got $jpgResult", jpgResult is PdfEngine.PdfToImagesResult.Success)
        val jpgFiles = (jpgResult as PdfEngine.PdfToImagesResult.Success).files
        assertEquals(2, jpgFiles.size)
        jpgFiles.forEach { file ->
            assertTrue(file.exists() && file.length() > 8)
            assertTrue(file.name.endsWith(".jpg"))
            val header = file.inputStream().use { inp -> ByteArray(3).also { inp.read(it) } }
            assertArrayEquals(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                header
            )
        }

        src.delete()
        pngFiles.forEach { it.delete() }
        jpgFiles.forEach { it.delete() }
    }

    @Test
    fun `extractTextFromPdfDetailed writes UTF-8 text file and does not register txt in RecentPdfDao`(): Unit = runBlocking {
        val src = createPdfWithText(
            File(context.cacheDir, "extract_text_src.pdf"),
            listOf("Alpha paragraph content", "Beta second page content")
        )

        val result = repository.extractTextFromPdfDetailed(
            pdfFile = src,
            outputName = "extracted_notes.txt"
        )
        assertTrue("Expected TextExtractResult.Success but got $result", result is PdfEngine.TextExtractResult.Success)
        val success = result as PdfEngine.TextExtractResult.Success
        assertTrue(success.file.exists())
        assertTrue(success.file.name.endsWith(".txt"))
        val content = success.file.readText()
        assertTrue(content.contains("Alpha paragraph content"))
        assertTrue(content.contains("Beta second page content"))

        // Verify .txt file was NOT added to RecentPdfDao
        val recentList = repository.allRecentPdfs.first()
        assertTrue(recentList.none { it.filePath == success.file.absolutePath || it.fileName.endsWith(".txt") })

        src.delete()
        success.file.delete()
    }

    @Test
    fun `extractTextFromPdfDetailed returns clear error for image-only PDF with no extractable text`(): Unit = runBlocking {
        // Create a PDF with an empty page (no text streams)
        val blankPdf = File(context.cacheDir, "scanned_image_only.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(blankPdf)
        }

        val result = repository.extractTextFromPdfDetailed(
            pdfFile = blankPdf,
            outputName = "should_not_be_created.txt"
        )
        assertTrue(result is PdfEngine.TextExtractResult.Error)
        val err = result as PdfEngine.TextExtractResult.Error
        assertTrue(err.message.contains("No extractable text", ignoreCase = true))
        assertFalse(File(context.filesDir, "output_texts/should_not_be_created.txt").exists())

        blankPdf.delete()
    }

    @Test
    fun `convertImageFormatDetailed converts PNG with transparency to JPEG and JPEG back to PNG`(): Unit = runBlocking {
        // Create a transparent PNG with a colored circle
        val pngSource = File(context.cacheDir, "source_alpha.png")
        val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawCircle(60f, 40f, 30f, paint)
        FileOutputStream(pngSource).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        // 1. PNG -> JPEG
        val toJpegResult = repository.convertImageFormatDetailed(
            imageUri = Uri.fromFile(pngSource),
            targetFormat = Bitmap.CompressFormat.JPEG,
            outputName = "alpha_flattened.jpg"
        )
        assertTrue(toJpegResult is PdfEngine.ImageConversionResult.Success)
        val jpegFile = (toJpegResult as PdfEngine.ImageConversionResult.Success).file
        assertTrue(jpegFile.exists())
        assertEquals(120, toJpegResult.width)
        assertEquals(80, toJpegResult.height)
        val jpgHeader = jpegFile.inputStream().use { inp -> ByteArray(3).also { inp.read(it) } }
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), jpgHeader)

        // 2. JPEG -> PNG
        val toPngResult = repository.convertImageFormatDetailed(
            imageUri = Uri.fromFile(jpegFile),
            targetFormat = Bitmap.CompressFormat.PNG,
            outputName = "roundtrip.png"
        )
        assertTrue(toPngResult is PdfEngine.ImageConversionResult.Success)
        val roundtripPng = (toPngResult as PdfEngine.ImageConversionResult.Success).file
        assertTrue(roundtripPng.exists())
        val pngHeader = roundtripPng.inputStream().use { inp -> ByteArray(4).also { inp.read(it) } }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), pngHeader)

        pngSource.delete()
        jpegFile.delete()
        roundtripPng.delete()
    }

    @Test
    fun `convertImagesToPdf creates multi-page PDF and fails cleanly on invalid image URI`(): Unit = runBlocking {
        val img1 = File(context.cacheDir, "img1.png")
        val img2 = File(context.cacheDir, "img2.jpg")
        Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).also { b ->
            b.eraseColor(Color.BLUE)
            FileOutputStream(img1).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
        Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).also { b ->
            b.eraseColor(Color.GREEN)
            FileOutputStream(img2).use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            b.recycle()
        }

        val pdfOut = repository.convertImagesToPdf(
            imageUris = listOf(Uri.fromFile(img1), Uri.fromFile(img2)),
            outputName = "combined_photos.pdf"
        )
        assertNotNull(pdfOut)
        PDDocument.load(pdfOut!!).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }

        // Corrupted image URI must cause conversion to fail cleanly without creating a broken PDF
        val badImage = File(context.cacheDir, "corrupt.png").apply { writeText("NOT_AN_IMAGE") }
        val failedPdf = repository.convertImagesToPdf(
            imageUris = listOf(Uri.fromFile(img1), Uri.fromFile(badImage)),
            outputName = "should_fail_images.pdf"
        )
        assertNull(failedPdf)
        assertFalse(File(context.filesDir, "output_pdfs/should_fail_images.pdf").exists())

        img1.delete()
        img2.delete()
        badImage.delete()
        pdfOut.delete()
    }

    @Test
    fun `convertTextFileToPdf converts plain text file with long lines and rejects binary files`(): Unit = runBlocking {
        val longWord = "A".repeat(160)
        val txtFile = File(context.cacheDir, "sample_input.txt").apply {
            writeText("Title: Offline Text to PDF Conversion\n\nParagraph 1 with normal text.\n$longWord\nEnd of file.")
        }

        val pdfFile = repository.convertTextFileToPdf(
            textUri = Uri.fromFile(txtFile),
            outputName = "from_txt_file.pdf"
        )
        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.exists())
        PDDocument.load(pdfFile).use { doc ->
            assertTrue(doc.numberOfPages >= 1)
        }

        val extracted = PdfEngine.extractTextFromPdfDetailed(context, pdfFile, outputName = "verify_txt.txt")
        assertTrue(extracted is PdfEngine.TextExtractResult.Success)
        val extractedContent = (extracted as PdfEngine.TextExtractResult.Success).extractedText
        assertTrue(extractedContent.contains("Offline Text to PDF Conversion"))
        extracted.file.delete()

        // Binary PDF disguised as .txt must be rejected
        val disguisedPdf = repository.convertTextFileToPdf(
            textUri = Uri.fromFile(pdfFile),
            outputName = "disguised_should_fail.pdf"
        )
        assertNull(disguisedPdf)

        txtFile.delete()
        pdfFile.delete()
    }
}
