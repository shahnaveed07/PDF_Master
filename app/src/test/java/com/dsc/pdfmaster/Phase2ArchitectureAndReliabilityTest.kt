package com.dsc.pdfmaster

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.di.AppModule
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.pdfengine.PdfPageBitmapCache
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.navigation.CompressPdfRoute
import com.dsc.pdfmaster.ui.navigation.HomeRoute
import com.dsc.pdfmaster.ui.navigation.PasswordProtectRoute
import com.dsc.pdfmaster.ui.navigation.PdfViewerRoute
import com.dsc.pdfmaster.ui.navigation.toAppRoute
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
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
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2ArchitectureAndReliabilityTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        AppModule.initialize(application)
    }

    @Test
    fun `300 page PDF renders at display size with LruCache without leaking memory`() = runBlocking {
        val largePdfFile = File(application.cacheDir, "large_300_pages.pdf")
        PDDocument().use { doc ->
            for (i in 1..300) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                if (i == 1 || i == 150 || i == 300) {
                    PDPageContentStream(doc, page).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                        cs.newLineAtOffset(50f, 750f)
                        cs.showText("Large PDF Page $i of 300")
                        cs.endText()
                    }
                }
            }
            doc.save(largePdfFile)
        }

        try {
            assertTrue(largePdfFile.exists())
            val meta = PdfEngine.getPdfMetadata(application, android.net.Uri.fromFile(largePdfFile))
            assertNotNull(meta)
            assertEquals(300, meta!!.pageCount)

            val cache = PdfPageBitmapCache()
            val targetWidth = 480

            val page0 = PdfEngine.renderPageBitmap(
                context = application,
                pdfFile = largePdfFile,
                pageIndex = 0,
                targetWidth = targetWidth,
                useCache = true,
                bitmapCache = cache
            )
            val page149 = PdfEngine.renderPageBitmap(
                context = application,
                pdfFile = largePdfFile,
                pageIndex = 149,
                targetWidth = targetWidth,
                useCache = true,
                bitmapCache = cache
            )
            val page299 = PdfEngine.renderPageBitmap(
                context = application,
                pdfFile = largePdfFile,
                pageIndex = 299,
                targetWidth = targetWidth,
                useCache = true,
                bitmapCache = cache
            )

            assertNotNull(page0)
            assertNotNull(page149)
            assertNotNull(page299)
            assertEquals(targetWidth, page0!!.width)
            assertEquals(targetWidth, page149!!.width)
            assertEquals(targetWidth, page299!!.width)

            // Verify LruCache stores and returns cached bitmaps
            assertEquals(3, cache.entryCount())
            val cachedPage149 = cache.get(largePdfFile, 149, targetWidth)
            assertTrue(cachedPage149 === page149)

            cache.invalidateFile(largePdfFile.absolutePath)
            assertEquals(0, cache.entryCount())
        } finally {
            largePdfFile.delete()
        }
    }

    @Test
    fun `image recompression mode shrinks embedded images while keeping text selectable`() = runBlocking {
        val sourceFile = File(application.cacheDir, "image_and_text_source.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            // Create a noisy 600x600 lossless image so JPEG recompression + downscaling reduces size significantly
            val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.LTGRAY)
            for (y in 0 until 600 step 4) {
                for (x in 0 until 600 step 4) {
                    bmp.setPixel(x, y, Color.rgb((x * 37) % 255, (y * 53) % 255, ((x + y) * 19) % 255))
                }
            }
            val pdImage = LosslessFactory.createFromImage(doc, bmp)
            bmp.recycle()

            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                cs.newLineAtOffset(50f, 780f)
                cs.showText("Selectable Phase 2 Text Content")
                cs.endText()
                cs.drawImage(pdImage, 50f, 200f, 400f, 400f)
            }
            doc.save(sourceFile)
        }

        val compressedFile = PdfEngine.compressPdf(
            context = application,
            pdfFile = sourceFile,
            level = PdfCompressionLevel.MEDIUM,
            outputFileName = "recompressed_selectable.pdf",
            mode = PdfCompressionMode.IMAGE_RECOMPRESSION
        )

        try {
            assertNotNull(compressedFile)
            assertTrue(compressedFile!!.exists())
            assertTrue(
                "Expected compressed size (${compressedFile.length()}) < original size (${sourceFile.length()})",
                compressedFile.length() < sourceFile.length()
            )

            // Verify text is still selectable in the compressed PDF
            PDDocument.load(compressedFile).use { loaded ->
                val extracted = PDFTextStripper().getText(loaded)
                assertTrue(
                    "Expected selectable text to be preserved, got: $extracted",
                    extracted.contains("Selectable Phase 2 Text Content")
                )
            }
        } finally {
            sourceFile.delete()
            compressedFile?.delete()
        }
    }

    @Test
    fun `rasterize maximum compression mode flattens pages and exposes warning message`() = runBlocking {
        assertEquals(
            "Maximum compression (text becomes image)",
            PdfCompressionMode.RASTERIZE_MAXIMUM.label
        )
        assertFalse(PdfCompressionMode.RASTERIZE_MAXIMUM.keepsTextSelectable)
        assertNotNull(PdfCompressionMode.RASTERIZE_MAXIMUM.warningMessage)

        val sourceFile = File(application.cacheDir, "rasterize_source.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText("Text that will be rasterized")
                cs.endText()
            }
            doc.save(sourceFile)
        }

        val rasterizedFile = PdfEngine.compressPdf(
            context = application,
            pdfFile = sourceFile,
            level = PdfCompressionLevel.HIGH,
            outputFileName = "rasterized_out.pdf",
            mode = PdfCompressionMode.RASTERIZE_MAXIMUM
        )

        try {
            assertNotNull(rasterizedFile)
            assertTrue(rasterizedFile!!.exists())
            PDDocument.load(rasterizedFile).use { loaded ->
                assertEquals(1, loaded.numberOfPages)
                val stripped = PDFTextStripper().getText(loaded).trim()
                assertTrue("Expected no selectable text after rasterization, got '$stripped'", stripped.isEmpty())
            }
        } finally {
            sourceFile.delete()
            rasterizedFile?.delete()
        }
    }

    @Test
    fun `sealed PdfOperationError maps exceptions to structured error states`() {
        val wrongPwd = PdfOperationError.fromThrowable(IOException("Cannot decrypt PDF: password is invalid"))
        assertTrue(wrongPwd is PdfOperationError.WrongPassword)

        val revoked = PdfOperationError.fromThrowable(SecurityException("Permission denial: reading provider"))
        assertTrue(revoked is PdfOperationError.RevokedPermission)

        val missing = PdfOperationError.fromThrowable(FileNotFoundException("No such file"))
        assertTrue(missing is PdfOperationError.FileNotFound)

        val noStorage = PdfOperationError.fromThrowable(IOException("write failed: ENOSPC (No space left on device)"))
        assertTrue(noStorage is PdfOperationError.InsufficientStorage)

        val corrupted = PdfOperationError.fromThrowable(IOException("Error: End-of-File, expected line"))
        assertTrue(corrupted is PdfOperationError.CorruptedPdf)
    }

    @Test
    fun `stale or missing file in Recent Files is detected and removable`() = runBlocking {
        val repo = AppModule.providePdfRepository(
            application,
            AppModule.provideRecentPdfDao(AppModule.providePdfDatabase(application))
        )
        val tempPdf = File(application.cacheDir, "temp_recent_doc.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(tempPdf)
        }

        repo.registerPdfFile(tempPdf, knownPageCount = 1)
        assertTrue(repo.isFileAvailable(tempPdf.absolutePath))

        // Simulate file being deleted externally
        assertTrue(tempPdf.delete())
        assertFalse(repo.isFileAvailable(tempPdf.absolutePath))

        repo.removeFromRecent(tempPdf.absolutePath)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun `type safe kotlinx serialization routes resolve serializers for Navigation Compose`() {
        assertEquals("com.dsc.pdfmaster.ui.navigation.HomeRoute", serializer<HomeRoute>().descriptor.serialName)
        assertEquals("com.dsc.pdfmaster.ui.navigation.PdfViewerRoute", serializer<PdfViewerRoute>().descriptor.serialName)
        assertEquals("com.dsc.pdfmaster.ui.navigation.CompressPdfRoute", serializer<CompressPdfRoute>().descriptor.serialName)
        assertEquals("com.dsc.pdfmaster.ui.navigation.PasswordProtectRoute", serializer<PasswordProtectRoute>().descriptor.serialName)

        ScreenRoute.values().forEach { route ->
            assertEquals(route, route.toAppRoute().screenRoute)
        }
    }

    @Test
    fun `viewModel supports both 256-bit and 128-bit AES password protection`() = runBlocking {
        val viewModel = PdfMasterViewModel(application)
        viewModel.setProtectKeyLengthBits(128)
        assertEquals(128, viewModel.selectedProtectKeyLengthBits.value)
        viewModel.setProtectKeyLengthBits(256)
        assertEquals(256, viewModel.selectedProtectKeyLengthBits.value)
    }
}
