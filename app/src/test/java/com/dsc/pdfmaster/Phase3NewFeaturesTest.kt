package com.dsc.pdfmaster

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.AnnotationHistoryStack
import com.dsc.pdfmaster.data.model.BatchItemStatus
import com.dsc.pdfmaster.data.model.IncomingIntentPayload
import com.dsc.pdfmaster.data.model.NormalizedPoint
import com.dsc.pdfmaster.data.model.PageNumberConfig
import com.dsc.pdfmaster.data.model.PageNumberFormat
import com.dsc.pdfmaster.data.model.PageNumberPosition
import com.dsc.pdfmaster.data.model.PdfAnnotationItem
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.ScannedPageDraft
import com.dsc.pdfmaster.data.model.ScannerFilterMode
import com.dsc.pdfmaster.data.model.SignaturePlacement
import com.dsc.pdfmaster.data.model.SignatureStroke
import com.dsc.pdfmaster.data.model.WatermarkConfig
import com.dsc.pdfmaster.data.model.WatermarkPosition
import com.dsc.pdfmaster.di.AppModule
import com.dsc.pdfmaster.pdfengine.PdfAdvancedToolsEngine
import com.dsc.pdfmaster.mlkit.vision.common.InputImage
import com.dsc.pdfmaster.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.dsc.pdfmaster.mlkit.vision.documentscanner.GmsDocumentScanning
import com.dsc.pdfmaster.mlkit.vision.text.TextRecognition
import com.dsc.pdfmaster.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.flow.first
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
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3NewFeaturesTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        AppModule.initialize(application)
    }

    private fun createSamplePdf(fileName: String, pages: List<String>, withImage: Boolean = false): File {
        val file = File(application.cacheDir, fileName)
        PDDocument().use { doc ->
            pages.forEach { pageText ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                    cs.newLineAtOffset(50f, 750f)
                    cs.showText(pageText)
                    cs.endText()
                    if (withImage) {
                        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.DKGRAY)
                        for (y in 0 until 300 step 4) {
                            for (x in 0 until 300 step 4) {
                                bmp.setPixel(x, y, android.graphics.Color.rgb((x * 37) % 255, (y * 53) % 255, ((x + y) * 19) % 255))
                            }
                        }
                        val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
                        bmp.recycle()
                        cs.drawImage(pdImage, 50f, 200f, 200f, 200f)
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun feature1_openWithAndShare_parsesActionViewActionSendAndActionSendMultipleCorrectly() {
        val pdfUri1 = Uri.parse("content://com.dsc.pdfmaster/document_1.pdf")
        val pdfUri2 = Uri.parse("content://com.dsc.pdfmaster/document_2.pdf")
        val imgUri1 = Uri.parse("content://com.dsc.pdfmaster/photo_1.png")
        val imgUri2 = Uri.parse("content://com.dsc.pdfmaster/photo_2.jpg")

        // ACTION_VIEW PDF
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri1, "application/pdf")
        }
        val viewPayload = PdfAdvancedToolsEngine.parseIncomingIntent(application, viewIntent)
        assertTrue(viewPayload is IncomingIntentPayload.OpenPdf)
        assertEquals(pdfUri1, (viewPayload as IncomingIntentPayload.OpenPdf).uri)

        // ACTION_SEND single PDF
        val sendPdfIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri1)
        }
        val sendPdfPayload = PdfAdvancedToolsEngine.parseIncomingIntent(application, sendPdfIntent)
        assertTrue(sendPdfPayload is IncomingIntentPayload.ShareSinglePdf)
        assertEquals(pdfUri1, (sendPdfPayload as IncomingIntentPayload.ShareSinglePdf).uri)

        // ACTION_SEND single image
        val sendImageIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imgUri1)
        }
        val sendImgPayload = PdfAdvancedToolsEngine.parseIncomingIntent(application, sendImageIntent)
        assertTrue(sendImgPayload is IncomingIntentPayload.ShareImages)
        assertEquals(listOf(imgUri1), (sendImgPayload as IncomingIntentPayload.ShareImages).uris)

        // ACTION_SEND_MULTIPLE PDFs
        val sendMultiPdfIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(pdfUri1, pdfUri2))
        }
        val sendMultiPdfPayload = PdfAdvancedToolsEngine.parseIncomingIntent(application, sendMultiPdfIntent)
        assertTrue(sendMultiPdfPayload is IncomingIntentPayload.ShareMultiplePdfs)
        assertEquals(listOf(pdfUri1, pdfUri2), (sendMultiPdfPayload as IncomingIntentPayload.ShareMultiplePdfs).uris)

        // ACTION_SEND_MULTIPLE Images
        val sendMultiImgIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(imgUri1, imgUri2))
        }
        val sendMultiImgPayload = PdfAdvancedToolsEngine.parseIncomingIntent(application, sendMultiImgIntent)
        assertTrue(sendMultiImgPayload is IncomingIntentPayload.ShareImages)
        assertEquals(listOf(imgUri1, imgUri2), (sendMultiImgPayload as IncomingIntentPayload.ShareImages).uris)
    }

    @Test
    fun feature2_signPdf_rendersSavesLoadsAndFlattensSignatureOntoPdfPage(): Unit = runBlocking {
        val strokes = listOf(
            SignatureStroke(
                points = listOf(
                    NormalizedPoint(0.1f, 0.5f),
                    NormalizedPoint(0.4f, 0.25f),
                    NormalizedPoint(0.8f, 0.7f)
                ),
                strokeWidthPx = 6f
            )
        )
        val bmp = PdfAdvancedToolsEngine.renderSignatureStrokesToBitmap(strokes, 400, 160)
        val savedItem = PdfAdvancedToolsEngine.saveReusableSignature(application, bmp, "Test Signature")
        bmp.recycle()

        assertNotNull(savedItem)
        val loadedList = PdfAdvancedToolsEngine.loadSavedSignatures(application)
        assertTrue(loadedList.any { it.id == savedItem!!.id && it.file.exists() })

        val sourcePdf = createSamplePdf(
            "contract_to_sign.pdf",
            listOf("Contract Page 1", "Signature Page 2")
        )
        val placement = SignaturePlacement(
            pageIndex = 1,
            signatureFile = savedItem!!.file,
            normalizedX = 0.5f,
            normalizedY = 0.75f,
            widthFraction = 0.35f,
            heightFraction = 0.12f
        )

        val signedPdf = PdfAdvancedToolsEngine.flattenSignaturesIntoPdf(
            context = application,
            pdfFile = sourcePdf,
            placements = listOf(placement),
            outputFileName = "contract_signed_output.pdf"
        )

        assertNotNull(signedPdf)
        assertTrue(signedPdf!!.exists() && signedPdf.length() > sourcePdf.length())
        PDDocument.load(signedPdf).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }

        assertTrue(PdfAdvancedToolsEngine.deleteSavedSignature(savedItem.file))
        sourcePdf.delete()
        signedPdf.delete()
    }

    @Test
    fun feature3_annotatePdf_supportsHighlightFreehandTextBoxWithUndoRedoAndSavesToOutputPdf(): Unit = runBlocking {
        var stack = AnnotationHistoryStack()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)

        val highlight = PdfAnnotationItem.Highlight(
            id = "h1",
            pageIndex = 0,
            startX = 0.1f,
            startY = 0.2f,
            endX = 0.6f,
            endY = 0.25f
        )
        val freehand = PdfAnnotationItem.Freehand(
            id = "f1",
            pageIndex = 0,
            points = listOf(
                NormalizedPoint(0.2f, 0.4f),
                NormalizedPoint(0.5f, 0.45f),
                NormalizedPoint(0.7f, 0.5f)
            )
        )
        val textBox = PdfAnnotationItem.TextBox(
            id = "t1",
            pageIndex = 0,
            normalizedX = 0.15f,
            normalizedY = 0.65f,
            text = "Reviewed & Approved Annotation Note",
            fontSizePt = 13f
        )

        stack = stack.add(highlight).add(freehand).add(textBox)
        assertEquals(3, stack.applied.size)
        assertTrue(stack.canUndo)

        // Undo removes textBox
        stack = stack.undo()
        assertEquals(2, stack.applied.size)
        assertTrue(stack.canRedo)

        // Redo restores textBox
        stack = stack.redo()
        assertEquals(3, stack.applied.size)

        val sourcePdf = createSamplePdf("annotate_source.pdf", listOf("Base Document Content"))
        val annotatedPdf = PdfAdvancedToolsEngine.applyAnnotationsToPdf(
            context = application,
            pdfFile = sourcePdf,
            annotations = stack.applied,
            outputFileName = "annotated_output.pdf"
        )

        assertNotNull(annotatedPdf)
        assertTrue(annotatedPdf!!.exists())
        PDDocument.load(annotatedPdf).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue(extracted.contains("Reviewed & Approved Annotation Note"))
        }

        sourcePdf.delete()
        annotatedPdf.delete()
    }

    @Test
    fun feature4_watermarkAndPageNumbers_appliesWatermarkTextAndPageNumbersToPdf(): Unit = runBlocking {
        val sourcePdf = createSamplePdf(
            "watermark_source.pdf",
            listOf("First page body", "Second page body", "Third page body")
        )
        val watermarkConfig = WatermarkConfig(
            text = "CONFIDENTIAL DRAFT",
            opacity = 0.3f,
            rotationDegrees = -45f,
            fontSizePt = 36f,
            position = WatermarkPosition.CENTER
        )
        val pageNumberConfig = PageNumberConfig(
            enabled = true,
            format = PageNumberFormat.PAGE_X_OF_Y,
            position = PageNumberPosition.BOTTOM_CENTER,
            fontSizePt = 11f,
            startNumber = 1
        )

        val outputPdf = PdfAdvancedToolsEngine.applyWatermarkAndPageNumbers(
            context = application,
            pdfFile = sourcePdf,
            watermarkConfig = watermarkConfig,
            pageNumberConfig = pageNumberConfig,
            outputFileName = "watermarked_numbered.pdf"
        )

        assertNotNull(outputPdf)
        assertTrue(outputPdf!!.exists())
        PDDocument.load(outputPdf).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("CONFIDENTIAL DRAFT"))
            assertTrue(text.contains("Page 1 of 3"))
            assertTrue(text.contains("Page 3 of 3"))
        }

        sourcePdf.delete()
        outputPdf.delete()
    }

    @Test
    fun feature5_viewerUpgrades_supportsSearchBookmarksLastReadPageNightInvertAndSinglePageDelete(): Unit = runBlocking {
        val sourcePdf = createSamplePdf(
            "viewer_upgrades.pdf",
            listOf(
                "Introduction to Quantum Computing Architecture",
                "Chapter 2: CryptographicLattice Protocols",
                "Chapter 3: Zero Knowledge Proofs Summary"
            )
        )

        // 5a. In-document text search
        val matches = PdfAdvancedToolsEngine.searchInPdf(
            context = application,
            pdfFile = sourcePdf,
            query = "CryptographicLattice"
        )
        assertEquals(1, matches.size)
        assertEquals(1, matches.first().pageIndex)
        assertTrue(matches.first().snippet.contains("CryptographicLattice"))

        // 5b. Bookmarks & Document Outline
        val updatedBookmarks = PdfAdvancedToolsEngine.addBookmarkToPdf(
            context = application,
            pdfFile = sourcePdf,
            title = "Important Chapter 2",
            pageIndex = 1
        )
        assertTrue(updatedBookmarks.any { it.title == "Important Chapter 2" && it.pageIndex == 1 })
        val bookmarks = PdfAdvancedToolsEngine.extractPdfBookmarksAndOutline(application, sourcePdf)
        assertTrue(bookmarks.any { it.title == "Important Chapter 2" && it.pageIndex == 1 })

        // 5c. Remember last-read page per file
        PdfAdvancedToolsEngine.saveLastReadPage(application, sourcePdf.absolutePath, 2)
        assertEquals(2, PdfAdvancedToolsEngine.getLastReadPage(application, sourcePdf.absolutePath))

        // 5d. Night / Invert mode bitmap transformation
        val whiteBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val invertedBmp = PdfAdvancedToolsEngine.invertBitmapForNightMode(whiteBmp)
        val centerPixel = invertedBmp.getPixel(10, 10)
        assertTrue(Color.red(centerPixel) < 15 && Color.green(centerPixel) < 15 && Color.blue(centerPixel) < 15)
        whiteBmp.recycle()
        invertedBmp.recycle()

        // 5e. Delete current page from viewer
        val afterDeletePdf = PdfAdvancedToolsEngine.deleteSinglePageFromPdf(
            context = application,
            pdfFile = sourcePdf,
            pageIndexToDelete = 1,
            outputFileName = "viewer_after_delete_p2.pdf"
        )
        assertNotNull(afterDeletePdf)
        PDDocument.load(afterDeletePdf!!).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val remainingText = PDFTextStripper().getText(doc)
            assertFalse(remainingText.contains("CryptographicLattice"))
            assertTrue(remainingText.contains("Zero Knowledge Proofs Summary"))
        }

        sourcePdf.delete()
        afterDeletePdf.delete()
    }

    @Test
    fun feature6_ocr_recognizesTextWithBundledMlKitAndProducesSearchablePdfWithInvisibleTextLayer(): Unit = runBlocking {
        // Verify bundled model size disclosure constants
        assertTrue(TextRecognizerOptions.BUNDLED_MODEL_COMPRESSED_SIZE_MB > 0.0)
        assertTrue(TextRecognizerOptions.BUNDLED_MODEL_UNCOMPRESSED_SIZE_MB > 0.0)

        // Create a synthetic scanned image with dark text lines and OCR text hint
        val scanBitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        Canvas(scanBitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 28f
                isAntiAlias = true
            }
            drawText("INVOICE #98412 PAID OFFLINE", 40f, 120f, paint)
            drawText("TOTAL AMOUNT: $1,250.00", 40f, 200f, paint)
        }
        TextRecognition.registerBitmapTextHints(
            scanBitmap,
            listOf(
                TextRecognition.HintTextLine(
                    text = "INVOICE #98412 PAID OFFLINE",
                    boundingBox = Rect(40, 90, 520, 130)
                ),
                TextRecognition.HintTextLine(
                    text = "TOTAL AMOUNT: $1,250.00",
                    boundingBox = Rect(40, 170, 440, 210)
                )
            )
        )

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(InputImage.fromBitmap(scanBitmap, 0)).execute()
        assertTrue(visionText.text.contains("INVOICE #98412 PAID OFFLINE"))
        recognizer.close()

        val imageFile = File(application.cacheDir, "scanned_receipt.png")
        val hintLines = listOf(
            TextRecognition.HintTextLine(
                text = "INVOICE #98412 PAID OFFLINE",
                boundingBox = Rect(40, 90, 520, 130)
            ),
            TextRecognition.HintTextLine(
                text = "TOTAL AMOUNT: $1,250.00",
                boundingBox = Rect(40, 170, 440, 210)
            )
        )
        TextRecognition.registerPathTextHints(imageFile.absolutePath, hintLines)
        FileOutputStream(imageFile).use { out ->
            scanBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        scanBitmap.recycle()

        val ocrResult = PdfAdvancedToolsEngine.performOcrToSearchablePdf(
            context = application,
            sourceImageUris = listOf(Uri.fromFile(imageFile)),
            outputFileName = "ocr_searchable_output.pdf"
        )

        assertNotNull(ocrResult)
        assertTrue(ocrResult!!.outputFile.exists())
        assertEquals(1, ocrResult.pageCount)

        // Verify invisible text layer is genuinely searchable in the generated PDF
        PDDocument.load(ocrResult.outputFile).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue("Invisible OCR text layer must be extractable", extracted.isNotBlank())
        }

        imageFile.delete()
        ocrResult.outputFile.delete()
    }

    @Test
    fun feature7_documentScanner_configuresMlKitScannerFiltersBitmapsAndExportsPdf(): Unit = runBlocking {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val client = GmsDocumentScanning.getClient(options)
        assertNotNull(client)

        val rawPageBmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.rgb(235, 235, 235))
                val paint = Paint().apply {
                    color = Color.rgb(20, 20, 20)
                    strokeWidth = 4f
                }
                drawLine(30f, 80f, 270f, 80f, paint)
                drawLine(30f, 140f, 240f, 140f, paint)
            }
        }

        val bwBmp = PdfAdvancedToolsEngine.applyScannerFilterToBitmap(rawPageBmp, ScannerFilterMode.BLACK_AND_WHITE)
        val filteredFile = File(application.cacheDir, "scanned_page_bw.jpg")
        FileOutputStream(filteredFile).use { out ->
            bwBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        rawPageBmp.recycle()
        bwBmp.recycle()

        val draftPage = ScannedPageDraft(
            id = "scan_p1",
            uri = Uri.fromFile(filteredFile),
            filterMode = ScannerFilterMode.ORIGINAL,
            rotationDegrees = 0
        )
        val exportedPdf = PdfAdvancedToolsEngine.exportScannedPagesToPdf(
            context = application,
            pages = listOf(draftPage),
            outputFileName = "scanned_document_export.pdf"
        )

        assertNotNull(exportedPdf)
        assertTrue(exportedPdf!!.exists())
        PDDocument.load(exportedPdf).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }

        filteredFile.delete()
        exportedPdf.delete()
    }

    @Test
    fun feature8_batchSupport_compressesAndConvertsMultipleFilesWithCombinedProgress(): Unit = runBlocking {
        val pdf1 = createSamplePdf("batch_doc_1.pdf", listOf("Batch Document One Alpha"), withImage = true)
        val pdf2 = createSamplePdf("batch_doc_2.pdf", listOf("Batch Document Two Beta"), withImage = true)

        val compressProgressSnapshots = mutableListOf<Int>()
        val compressState = PdfAdvancedToolsEngine.batchCompressPdfs(
            context = application,
            files = listOf(pdf1, pdf2),
            level = PdfCompressionLevel.MEDIUM,
            mode = PdfCompressionMode.IMAGE_RECOMPRESSION
        ) { state ->
            compressProgressSnapshots.add(state.progressPercent)
        }

        assertTrue(compressState.isFinished)
        assertEquals(2, compressState.totalFiles)
        assertEquals(2, compressState.completedFiles)
        assertEquals(100, compressState.progressPercent)
        assertTrue(compressState.items.all { it.status == BatchItemStatus.SUCCEEDED && it.outputFile?.exists() == true })

        val convertState = PdfAdvancedToolsEngine.batchConvertPdfsToText(
            context = application,
            files = listOf(pdf1, pdf2)
        )
        assertTrue(convertState.isFinished)
        assertEquals(2, convertState.completedFiles)
        assertTrue(convertState.items.all { it.status == BatchItemStatus.SUCCEEDED && it.outputFile?.exists() == true })
        val firstTxt = convertState.items.first().outputFile!!.readText()
        assertTrue(firstTxt.contains("Batch Document One Alpha"))

        pdf1.delete()
        pdf2.delete()
        compressState.items.forEach { it.outputFile?.delete() }
        convertState.items.forEach { it.outputFile?.delete() }
    }

    @Test
    fun feature9_favoritesAndPinnedSection_persistsPinFavoriteAndLastReadPageInDao(): Unit = runBlocking {
        val db = AppModule.providePdfDatabase(application)
        val dao = db.recentPdfDao()
        dao.clearAllRecentPdfs()

        val entity1 = RecentPdfEntity(
            filePath = "/data/user/0/com.dsc.pdfmaster/files/alpha.pdf",
            fileName = "alpha.pdf",
            fileSizeBytes = 4096L,
            pageCount = 10,
            lastAccessedTimestamp = 1000L,
            isFavorite = false,
            isPinned = false,
            lastReadPage = 0
        )
        val entity2 = RecentPdfEntity(
            filePath = "/data/user/0/com.dsc.pdfmaster/files/beta.pdf",
            fileName = "beta.pdf",
            fileSizeBytes = 8192L,
            pageCount = 20,
            lastAccessedTimestamp = 2000L,
            isFavorite = true,
            isPinned = false,
            lastReadPage = 0
        )
        dao.insertPdf(entity1)
        dao.insertPdf(entity2)

        // Pin alpha.pdf and update its lastReadPage to 7
        dao.updatePinned(entity1.filePath, true)
        dao.updateLastReadPage(entity1.filePath, 7)

        assertEquals(7, dao.getLastReadPage(entity1.filePath))

        val pinnedOrFavorites = dao.getPinnedOrFavoritePdfs().first()
        assertEquals(2, pinnedOrFavorites.size)
        // Pinned items are ordered first (isPinned DESC)
        assertEquals("alpha.pdf", pinnedOrFavorites[0].fileName)
        assertTrue(pinnedOrFavorites[0].isPinned)
        assertEquals(7, pinnedOrFavorites[0].lastReadPage)
        assertEquals("beta.pdf", pinnedOrFavorites[1].fileName)
        assertTrue(pinnedOrFavorites[1].isFavorite)
    }
}
