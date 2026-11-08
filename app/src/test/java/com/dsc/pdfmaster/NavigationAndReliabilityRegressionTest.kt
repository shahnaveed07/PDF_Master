package com.dsc.pdfmaster

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [36])
class NavigationAndReliabilityRegressionTest {

    private lateinit var app: Application
    private lateinit var viewModel: PdfMasterViewModel
    private lateinit var repository: PdfRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = PdfMasterViewModel(app)
        repository = viewModel.repository
        viewModel.resetProcessingState()
        viewModel.setSearchQuery("")
        runBlocking {
            repository.clearHistory()
        }
    }

    private fun createDummyPdf(name: String): File {
        val dir = File(app.filesDir, "test_regression_docs").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray())
        return file
    }

    @Test
    fun test_navigationToTopLevelTabs_updatesCurrentRouteCleanly() {
        viewModel.navigateTo(ScreenRoute.RECENT_FILES)
        assertEquals(ScreenRoute.RECENT_FILES, viewModel.currentRoute.value)

        viewModel.navigateTo(ScreenRoute.SETTINGS)
        assertEquals(ScreenRoute.SETTINGS, viewModel.currentRoute.value)

        viewModel.navigateTo(ScreenRoute.HOME)
        assertEquals(ScreenRoute.HOME, viewModel.currentRoute.value)
    }

    @Test
    fun test_navigationEvents_emitsRouteEvents() = runBlocking {
        viewModel.navigateTo(ScreenRoute.CREATE_PDF)
        assertEquals(ScreenRoute.CREATE_PDF, viewModel.currentRoute.value)

        viewModel.updateCurrentRouteFromNav(ScreenRoute.CONVERT_PDF)
        assertEquals(ScreenRoute.CONVERT_PDF, viewModel.currentRoute.value)
    }

    @Test
    fun test_cancelActiveOperation_setsNotificationAndResetsState() {
        viewModel.cancelActiveOperation()
        assertFalse(viewModel.isProcessing.value)
        assertEquals("Operation cancelled", viewModel.userNotification.value)
    }

    @Test
    fun test_clearViewerBookmarks_preventsDocumentStateBleed() {
        viewModel.clearViewerBookmarks()
        assertTrue(viewModel.viewerBookmarks.value.isEmpty())
    }

    @Test
    fun test_safeOutputFile_preventsPathTraversal() {
        val outputDir = File(app.filesDir, "test_safe_out").apply { mkdirs() }

        // Path traversal payload
        val safeFile1 = PdfEngine.getUniqueOutputFile(outputDir, "../../etc/passwd")
        assertTrue(safeFile1.canonicalPath.startsWith(outputDir.canonicalPath))
        assertFalse(safeFile1.name.contains(".."))
        assertTrue(safeFile1.name.endsWith(".pdf"))

        // Special characters and Windows/Unix reserved chars
        val safeFile2 = PdfEngine.getUniqueOutputFile(outputDir, "invoice:May*2026?.pdf")
        assertTrue(safeFile2.canonicalPath.startsWith(outputDir.canonicalPath))
        assertFalse(safeFile2.name.contains(":"))
        assertFalse(safeFile2.name.contains("*"))
        assertFalse(safeFile2.name.contains("?"))
    }

    @Test
    fun test_deletePdf_reportsTruthfulResult() = runBlocking {
        val file = createDummyPdf("to_delete.pdf")
        repository.registerPdfFile(file)

        val initialList = repository.allRecentPdfs.first()
        assertTrue(initialList.any { it.filePath == file.absolutePath })

        // 1. Successful deletion of accessible on-disk file
        val result = repository.deletePdf(file.absolutePath)
        assertTrue(result is PdfRepository.DeleteResult.Success)
        assertFalse(file.exists())

        // 2. Deleting an already non-existent file
        val missingResult = repository.deletePdf(file.absolutePath)
        assertTrue(missingResult is PdfRepository.DeleteResult.FileUnavailable)
    }

    @Test
    fun test_setSelectedPdfFile_nullSafe() {
        val file = createDummyPdf("selected_doc.pdf")
        viewModel.setSelectedPdfFile(file)
        assertEquals(file.absolutePath, viewModel.selectedPdfFile.value?.absolutePath)
        assertTrue(viewModel.isPdfExplicitlySelected.value)

        // Clear selection
        viewModel.clearSelectedPdfFile()
        assertNull(viewModel.selectedPdfFile.value)
        assertFalse(viewModel.isPdfExplicitlySelected.value)
    }

    @Test
    fun test_scannerTask_failureListenerInvokedRegardlessOfChainingOrder() {
        var failureCount1 = 0
        var successCount1 = 0
        val failingTask1 = com.dsc.pdfmaster.mlkit.vision.documentscanner.ScannerTask<String> {
            throw IllegalStateException("Module not installed")
        }
        failingTask1
            .addOnSuccessListener { successCount1++ }
            .addOnFailureListener { failureCount1++ }

        assertEquals(0, successCount1)
        assertEquals(1, failureCount1)

        // Reverse chaining order
        var failureCount2 = 0
        var successCount2 = 0
        val failingTask2 = com.dsc.pdfmaster.mlkit.vision.documentscanner.ScannerTask<String> {
            throw IllegalStateException("Module not installed")
        }
        failingTask2
            .addOnFailureListener { failureCount2++ }
            .addOnSuccessListener { successCount2++ }

        assertEquals(0, successCount2)
        assertEquals(1, failureCount2)
    }

    @Test
    fun test_ocrTask_doesNotDoubleExecuteSupplier() {
        var execCount = 0
        val task = com.dsc.pdfmaster.mlkit.vision.text.OcrTask {
            execCount++
            "Hello OCR"
        }
        var deliveredText: String? = null
        task
            .addOnSuccessListener { deliveredText = it }
            .addOnFailureListener { }

        assertEquals(1, execCount)
        assertEquals("Hello OCR", deliveredText)
        assertEquals("Hello OCR", task.execute())
        assertEquals(1, execCount)
    }

    @Test
    fun test_ocr_pageToPageIsolation_andNoSyntheticTokensForBlankPages() {
        val bitmap1 = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        val bitmap2 = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)

        com.dsc.pdfmaster.mlkit.vision.text.TextRecognition.registerBitmapTextHints(
            bitmap1,
            listOf(com.dsc.pdfmaster.mlkit.vision.text.TextRecognition.HintTextLine("Page 1 Unique Invoice", android.graphics.Rect(10, 10, 200, 40)))
        )

        val recognizer = com.dsc.pdfmaster.mlkit.vision.text.TextRecognition.getClient()

        // Page 1 gets Page 1 text
        val text1 = recognizer.processSync(com.dsc.pdfmaster.mlkit.vision.common.InputImage.fromBitmap(bitmap1, 0))
        assertEquals("Page 1 Unique Invoice", text1.text)

        // Page 2 (identical dimensions) must NOT receive Page 1 text or fake tokens
        val text2 = recognizer.processSync(com.dsc.pdfmaster.mlkit.vision.common.InputImage.fromBitmap(bitmap2, 0))
        assertEquals("", text2.text)
        assertTrue(text2.textBlocks.isEmpty())

        recognizer.close()
        bitmap1.recycle()
        bitmap2.recycle()
    }
}
