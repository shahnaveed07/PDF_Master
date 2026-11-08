package com.dsc.pdfmaster.pdfengine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import com.dsc.pdfmaster.data.model.BatchFileItemResult
import com.dsc.pdfmaster.data.model.BatchItemStatus
import com.dsc.pdfmaster.data.model.BatchProgressState
import com.dsc.pdfmaster.data.model.IncomingIntentPayload
import com.dsc.pdfmaster.data.model.OcrPageSummary
import com.dsc.pdfmaster.data.model.OcrSearchablePdfResult
import com.dsc.pdfmaster.data.model.PageNumberConfig
import com.dsc.pdfmaster.data.model.PageNumberPosition
import com.dsc.pdfmaster.data.model.PdfAnnotationItem
import com.dsc.pdfmaster.data.model.PdfBookmarkEntry
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.PdfSearchMatch
import com.dsc.pdfmaster.data.model.SavedSignatureItem
import com.dsc.pdfmaster.data.model.ScannedPageDraft
import com.dsc.pdfmaster.data.model.ScannerFilterMode
import com.dsc.pdfmaster.data.model.SignaturePlacement
import com.dsc.pdfmaster.data.model.SignatureStroke
import com.dsc.pdfmaster.data.model.WatermarkConfig
import com.dsc.pdfmaster.data.model.WatermarkPosition
import com.dsc.pdfmaster.mlkit.vision.common.InputImage
import com.dsc.pdfmaster.mlkit.vision.text.TextRecognition
import com.dsc.pdfmaster.mlkit.vision.text.TextRecognizer
import com.dsc.pdfmaster.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

object PdfAdvancedToolsEngine {

    private const val PREFS_VIEWER_STATE = "pdf_master_viewer_state"
    private const val KEY_PREFIX_LAST_PAGE = "last_page_"
    private const val KEY_PREFIX_BOOKMARKS = "bookmarks_"

    // =========================================================================
    // 1. Open-With and Share Intent Parsing
    // =========================================================================

    fun parseIncomingIntent(context: Context, intent: Intent?): IncomingIntentPayload {
        if (intent == null) return IncomingIntentPayload.None
        val action = intent.action ?: return IncomingIntentPayload.None
        val intentMimeType = intent.type?.lowercase() ?: ""

        fun resolveMimeType(uri: Uri): String {
            if (intentMimeType.isNotEmpty() && intentMimeType != "application/octet-stream" && intentMimeType != "*/*") return intentMimeType
            if (uri.scheme == "content") {
                val resolved = context.contentResolver.getType(uri)?.lowercase()
                if (!resolved.isNullOrEmpty() && resolved != "application/octet-stream" && resolved != "*/*") return resolved
            }
            return intentMimeType
        }

        fun getDisplayName(uri: Uri): String {
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) return cursor.getString(idx) ?: ""
                        }
                    }
                } catch (e: Exception) {
                }
            }
            return uri.lastPathSegment ?: ""
        }

        fun isPdf(uri: Uri): Boolean {
            val resolvedMime = resolveMimeType(uri)
            if (resolvedMime == "application/pdf") return true
            
            val name = getDisplayName(uri).lowercase()
            if (name.endsWith(".pdf")) return true
            
            return false
        }

        return when (action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: extractClipDataUris(intent).firstOrNull()
                if (dataUri != null && isPdf(dataUri)) {
                    IncomingIntentPayload.OpenPdf(dataUri)
                } else {
                    IncomingIntentPayload.None
                }
            }

            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: extractClipDataUris(intent).firstOrNull()
                    ?: intent.data
                if (streamUri == null) {
                    IncomingIntentPayload.None
                } else if (intentMimeType.startsWith("image/")) {
                    IncomingIntentPayload.ShareImages(listOf(streamUri))
                } else if (isPdf(streamUri)) {
                    IncomingIntentPayload.ShareSinglePdf(streamUri)
                } else {
                    IncomingIntentPayload.None
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val streamUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.filterNotNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: extractClipDataUris(intent)

                if (streamUris.isEmpty()) {
                    IncomingIntentPayload.None
                } else if (intentMimeType.startsWith("image/")) {
                    IncomingIntentPayload.ShareImages(streamUris)
                } else if (streamUris.all { isPdf(it) }) {
                    if (streamUris.size == 1) {
                        IncomingIntentPayload.ShareSinglePdf(streamUris.first())
                    } else {
                        IncomingIntentPayload.ShareMultiplePdfs(streamUris)
                    }
                } else {
                    IncomingIntentPayload.None
                }
            }

            else -> IncomingIntentPayload.None
        }
    }

    private fun extractClipDataUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        val list = mutableListOf<Uri>()
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i)?.uri?.let { list.add(it) }
        }
        return list
    }

    // =========================================================================
    // 2. Sign PDF Engine (Draw, Save Locally for Reuse, Place/Resize/Drag, Flatten)
    // =========================================================================

    fun renderSignatureStrokesToBitmap(
        strokes: List<SignatureStroke>,
        widthPx: Int = 600,
        heightPx: Int = 240
    ): Bitmap {
        val w = widthPx.coerceIn(120, 2000)
        val h = heightPx.coerceIn(60, 1000)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            paint.strokeWidth = stroke.strokeWidthPx.coerceIn(2f, 24f)
            if (stroke.points.size == 1) {
                val pt = stroke.points.first()
                canvas.drawPoint(pt.x * w, pt.y * h, paint)
            } else {
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.x * w, first.y * h)
                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    path.lineTo(pt.x * w, pt.y * h)
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    suspend fun saveReusableSignature(
        context: Context,
        signatureBitmap: Bitmap,
        name: String = "Signature_${System.currentTimeMillis()}"
    ): SavedSignatureItem? = withContext(Dispatchers.IO) {
        try {
            val sigDir = File(context.filesDir, "saved_signatures").apply { mkdirs() }
            val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Signature" }
            val fileName = "${cleanName}_${System.nanoTime()}.png"
            val outFile = File(sigDir, fileName)
            FileOutputStream(outFile).use { fos ->
                signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            if (outFile.exists() && outFile.length() > 0L) {
                SavedSignatureItem(
                    id = outFile.nameWithoutExtension,
                    name = cleanName,
                    file = outFile,
                    createdAtTimestamp = outFile.lastModified()
                )
            } else {
                outFile.delete()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadSavedSignatures(context: Context): List<SavedSignatureItem> = withContext(Dispatchers.IO) {
        val sigDir = File(context.filesDir, "saved_signatures")
        if (!sigDir.exists() || !sigDir.isDirectory) return@withContext emptyList()
        sigDir.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) && file.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                SavedSignatureItem(
                    id = file.nameWithoutExtension,
                    name = file.nameWithoutExtension.substringBeforeLast('_').ifBlank { "Signature" },
                    file = file,
                    createdAtTimestamp = file.lastModified()
                )
            } ?: emptyList()
    }

    suspend fun deleteSavedSignature(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete() else false
    }

    suspend fun flattenSignaturesIntoPdf(
        context: Context,
        pdfFile: File,
        placements: List<SignaturePlacement>,
        outputFileName: String = "${pdfFile.nameWithoutExtension}_signed.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L || placements.isEmpty()) return@withContext null
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val outputFile = PdfEngine.getUniqueOutputFile(outputDir, outputFileName)

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            if (doc.isEncrypted) {
                doc.isAllSecurityToBeRemoved = true
            }

            val totalPages = doc.numberOfPages
            for (placement in placements) {
                coroutineContext.ensureActive()
                if (placement.pageIndex !in 0 until totalPages) continue
                if (!placement.signatureFile.exists() || placement.signatureFile.length() == 0L) continue

                val sigBitmap = BitmapFactory.decodeFile(placement.signatureFile.absolutePath) ?: continue
                try {
                    val page = doc.getPage(placement.pageIndex)
                    val mediaBox = page.mediaBox ?: PDRectangle.A4
                    val pageW = mediaBox.width
                    val pageH = mediaBox.height

                    val drawW = (placement.widthFraction.coerceIn(0.05f, 0.95f) * pageW)
                    val drawH = (placement.heightFraction.coerceIn(0.03f, 0.90f) * pageH)
                    val clampedX = placement.normalizedX.coerceIn(0f, 1f - placement.widthFraction.coerceIn(0.05f, 0.95f))
                    val clampedY = placement.normalizedY.coerceIn(0f, 1f - placement.heightFraction.coerceIn(0.03f, 0.90f))

                    val pdfX = mediaBox.lowerLeftX + clampedX * pageW
                    val pdfY = mediaBox.lowerLeftY + (1f - clampedY) * pageH - drawH

                    val pdImage = LosslessFactory.createFromImage(doc, sigBitmap)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.drawImage(pdImage, pdfX, pdfY, drawW, drawH)
                    }
                } finally {
                    sigBitmap.recycle()
                }
            }

            doc.save(outputFile)
            doc.close()
            doc = null

            if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 3. Annotate PDF Engine (Highlight, Freehand Draw, Text Boxes)
    // =========================================================================

    suspend fun applyAnnotationsToPdf(
        context: Context,
        pdfFile: File,
        annotations: List<PdfAnnotationItem>,
        outputFileName: String = "${pdfFile.nameWithoutExtension}_annotated.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L || annotations.isEmpty()) return@withContext null
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val outputFile = PdfEngine.getUniqueOutputFile(outputDir, outputFileName)

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            if (doc.isEncrypted) {
                doc.isAllSecurityToBeRemoved = true
            }

            val totalPages = doc.numberOfPages
            val groupedByPage = annotations.groupBy { it.pageIndex }

            for ((pageIndex, pageAnnotations) in groupedByPage) {
                coroutineContext.ensureActive()
                if (pageIndex !in 0 until totalPages) continue
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox ?: PDRectangle.A4
                val pageW = box.width
                val pageH = box.height

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (item in pageAnnotations) {
                        when (item) {
                            is PdfAnnotationItem.Highlight -> {
                                cs.saveGraphicsState()
                                val extState = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = item.opacity.coerceIn(0.1f, 0.8f)
                                }
                                cs.setGraphicsStateParameters(extState)
                                val minNx = min(item.startX, item.endX).coerceIn(0f, 1f)
                                val maxNx = max(item.startX, item.endX).coerceIn(0f, 1f)
                                val minNy = min(item.startY, item.endY).coerceIn(0f, 1f)
                                val maxNy = max(item.startY, item.endY).coerceIn(0f, 1f)

                                val rectW = ((maxNx - minNx) * pageW).coerceAtLeast(12f)
                                val rectH = ((maxNy - minNy) * pageH).coerceAtLeast(10f)
                                val pdfX = box.lowerLeftX + minNx * pageW
                                val pdfY = box.lowerLeftY + (1f - maxNy) * pageH

                                val shade = item.grayShade.coerceIn(0, 255) / 255f
                                cs.setNonStrokingColor(shade, shade, shade)
                                cs.addRect(pdfX, pdfY, rectW, rectH)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }

                            is PdfAnnotationItem.Freehand -> {
                                if (item.points.isNotEmpty()) {
                                    cs.saveGraphicsState()
                                    val shade = item.grayShade.coerceIn(0, 255) / 255f
                                    cs.setStrokingColor(shade, shade, shade)
                                    cs.setLineWidth(item.strokeWidthPt.coerceIn(1f, 12f))
                                    val first = item.points.first()
                                    val startX = box.lowerLeftX + first.x.coerceIn(0f, 1f) * pageW
                                    val startY = box.lowerLeftY + (1f - first.y.coerceIn(0f, 1f)) * pageH
                                    cs.moveTo(startX, startY)
                                    if (item.points.size == 1) {
                                        cs.lineTo(startX + 1f, startY + 1f)
                                    } else {
                                        for (i in 1 until item.points.size) {
                                            val pt = item.points[i]
                                            val px = box.lowerLeftX + pt.x.coerceIn(0f, 1f) * pageW
                                            val py = box.lowerLeftY + (1f - pt.y.coerceIn(0f, 1f)) * pageH
                                            cs.lineTo(px, py)
                                        }
                                    }
                                    cs.stroke()
                                    cs.restoreGraphicsState()
                                }
                            }

                            is PdfAnnotationItem.TextBox -> {
                                val safeText = sanitizePdfFontText(item.text)
                                if (safeText.isNotBlank()) {
                                    cs.saveGraphicsState()
                                    val fontSize = item.fontSizePt.coerceIn(8f, 36f)
                                    val pdfX = box.lowerLeftX + item.normalizedX.coerceIn(0.02f, 0.90f) * pageW
                                    val pdfY = box.lowerLeftY + (1f - item.normalizedY.coerceIn(0.05f, 0.95f)) * pageH
                                    val shade = item.grayShade.coerceIn(0, 255) / 255f

                                    cs.beginText()
                                    cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                                    cs.setNonStrokingColor(shade, shade, shade)
                                    cs.newLineAtOffset(pdfX, pdfY)
                                    cs.showText(safeText)
                                    cs.endText()
                                    cs.restoreGraphicsState()
                                }
                            }
                        }
                    }
                }
            }

            doc.save(outputFile)
            doc.close()
            doc = null

            if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 4. Watermark and Page Numbers Engine
    // =========================================================================

    suspend fun applyWatermarkAndPageNumbers(
        context: Context,
        pdfFile: File,
        watermarkConfig: WatermarkConfig,
        pageNumberConfig: PageNumberConfig,
        outputFileName: String = "${pdfFile.nameWithoutExtension}_watermarked.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        if (!watermarkConfig.enabled && !pageNumberConfig.enabled) return@withContext null

        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val outputFile = PdfEngine.getUniqueOutputFile(outputDir, outputFileName)

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            if (doc.isEncrypted) {
                doc.isAllSecurityToBeRemoved = true
            }

            val totalPages = doc.numberOfPages
            val wmText = sanitizePdfFontText(watermarkConfig.text.trim())

            for (pageIndex in 0 until totalPages) {
                coroutineContext.ensureActive()
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox ?: PDRectangle.A4
                val pageW = box.width
                val pageH = box.height

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    if (watermarkConfig.enabled && wmText.isNotBlank()) {
                        cs.saveGraphicsState()
                        val extState = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = watermarkConfig.opacity.coerceIn(0.05f, 1.0f)
                        }
                        cs.setGraphicsStateParameters(extState)
                        val font = PDType1Font.HELVETICA_BOLD
                        val fontSize = watermarkConfig.fontSizePt.coerceIn(12f, 96f)
                        val shade = watermarkConfig.grayShade.coerceIn(0, 230) / 255f
                        cs.setNonStrokingColor(shade, shade, shade)

                        val angleRad = Math.toRadians(watermarkConfig.rotationDegrees.toDouble())
                        val anchorPoints: List<Pair<Float, Float>> = when (watermarkConfig.position) {
                            WatermarkPosition.CENTER -> listOf((pageW * 0.30f) to (pageH * 0.50f))
                            WatermarkPosition.TOP_LEFT -> listOf((pageW * 0.10f) to (pageH * 0.85f))
                            WatermarkPosition.TOP_RIGHT -> listOf((pageW * 0.58f) to (pageH * 0.85f))
                            WatermarkPosition.BOTTOM_LEFT -> listOf((pageW * 0.10f) to (pageH * 0.15f))
                            WatermarkPosition.BOTTOM_RIGHT -> listOf((pageW * 0.58f) to (pageH * 0.15f))
                            WatermarkPosition.TILED -> listOf(
                                (pageW * 0.15f) to (pageH * 0.22f),
                                (pageW * 0.55f) to (pageH * 0.22f),
                                (pageW * 0.15f) to (pageH * 0.52f),
                                (pageW * 0.55f) to (pageH * 0.52f),
                                (pageW * 0.15f) to (pageH * 0.82f),
                                (pageW * 0.55f) to (pageH * 0.82f)
                            )
                        }

                        for ((anchorX, anchorY) in anchorPoints) {
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            cs.setTextMatrix(
                                com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                                    angleRad,
                                    box.lowerLeftX + anchorX,
                                    box.lowerLeftY + anchorY
                                )
                            )
                            cs.showText(wmText)
                            cs.endText()
                        }
                        cs.restoreGraphicsState()
                    }

                    if (pageNumberConfig.enabled) {
                        cs.saveGraphicsState()
                        val displayNum = pageNumberConfig.startNumber + pageIndex
                        val totalDisplay = pageNumberConfig.startNumber + totalPages - 1
                        val label = sanitizePdfFontText(
                            pageNumberConfig.format.formatPage(displayNum, totalDisplay)
                        )
                        val font = PDType1Font.HELVETICA
                        val fontSize = pageNumberConfig.fontSizePt.coerceIn(8f, 24f)
                        val approxTextWidth = (font.getStringWidth(label) / 1000f) * fontSize

                        val margin = 28f
                        val xPos = when (pageNumberConfig.position) {
                            PageNumberPosition.BOTTOM_LEFT, PageNumberPosition.TOP_LEFT ->
                                box.lowerLeftX + margin
                            PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.TOP_CENTER ->
                                box.lowerLeftX + (pageW - approxTextWidth) / 2f
                            PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT ->
                                box.lowerLeftX + pageW - approxTextWidth - margin
                        }
                        val yPos = when (pageNumberConfig.position) {
                            PageNumberPosition.BOTTOM_LEFT,
                            PageNumberPosition.BOTTOM_CENTER,
                            PageNumberPosition.BOTTOM_RIGHT -> box.lowerLeftY + margin
                            PageNumberPosition.TOP_LEFT,
                            PageNumberPosition.TOP_CENTER,
                            PageNumberPosition.TOP_RIGHT -> box.lowerLeftY + pageH - margin - fontSize
                        }

                        cs.beginText()
                        cs.setFont(font, fontSize)
                        val pgShade = 40f / 255f
                        cs.setNonStrokingColor(pgShade, pgShade, pgShade)
                        cs.newLineAtOffset(xPos, yPos)
                        cs.showText(label)
                        cs.endText()
                        cs.restoreGraphicsState()
                    }
                }
            }

            doc.save(outputFile)
            doc.close()
            doc = null

            if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 5. Viewer Upgrades (Text Search, Outline/Bookmarks, Last-Read Page, Night Mode, Delete Current Page)
    // =========================================================================

    suspend fun searchInPdf(
        context: Context,
        pdfFile: File,
        query: String,
        password: String? = null
    ): List<PdfSearchMatch> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || !pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext emptyList()
        }

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            val totalPages = doc.numberOfPages
            val stripper = PDFTextStripper()
            val results = mutableListOf<PdfSearchMatch>()
            var globalOrdinal = 0

            for (pageIndex in 0 until totalPages) {
                coroutineContext.ensureActive()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val pageText = stripper.getText(doc) ?: ""
                if (pageText.isBlank()) continue

                var searchIdx = 0
                var countOnPage = 0
                var firstSnippet = ""
                while (true) {
                    val found = pageText.indexOf(trimmedQuery, startIndex = searchIdx, ignoreCase = true)
                    if (found < 0) break
                    countOnPage++
                    if (firstSnippet.isEmpty()) {
                        val snippetStart = (found - 28).coerceAtLeast(0)
                        val snippetEnd = (found + trimmedQuery.length + 36).coerceAtMost(pageText.length)
                        firstSnippet = pageText.substring(snippetStart, snippetEnd)
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                    searchIdx = found + trimmedQuery.length.coerceAtLeast(1)
                }

                if (countOnPage > 0) {
                    globalOrdinal++
                    results.add(
                        PdfSearchMatch(
                            pageIndex = pageIndex,
                            matchOrdinalInDocument = globalOrdinal,
                            matchCountOnPage = countOnPage,
                            snippet = firstSnippet
                        )
                    )
                }
            }
            results
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun extractPdfBookmarksAndOutline(
        context: Context,
        pdfFile: File,
        password: String? = null
    ): List<PdfBookmarkEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<PdfBookmarkEntry>()
        if (pdfFile.exists() && pdfFile.length() > 0L) {
            var doc: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                doc = if (!password.isNullOrBlank()) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
                val outline: PDDocumentOutline? = doc.documentCatalog?.documentOutline
                if (outline != null) {
                    collectOutlineItems(doc, outline.firstChild, entries)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    doc?.close()
                } catch (_: Exception) {
                }
            }
        }

        val userBookmarks = getSavedUserBookmarks(context, pdfFile)
        (entries + userBookmarks)
            .distinctBy { "${it.pageIndex}_${it.title.lowercase()}" }
            .sortedBy { it.pageIndex }
    }

    private fun collectOutlineItems(
        doc: PDDocument,
        firstItem: PDOutlineItem?,
        outList: MutableList<PdfBookmarkEntry>
    ) {
        var current = firstItem
        while (current != null) {
            val title = current.title?.trim().orEmpty()
            val destPage = try {
                current.findDestinationPage(doc)
            } catch (_: Exception) {
                null
            }
            val pageIdx = if (destPage != null) {
                doc.pages.indexOf(destPage).coerceAtLeast(0)
            } else {
                0
            }
            if (title.isNotEmpty()) {
                outList.add(
                    PdfBookmarkEntry(
                        title = title,
                        pageIndex = pageIdx,
                        isDocumentOutline = true
                    )
                )
            }
            if (current.hasChildren()) {
                collectOutlineItems(doc, current.firstChild, outList)
            }
            current = current.nextSibling
        }
    }

    fun getSavedUserBookmarks(context: Context, pdfFile: File): List<PdfBookmarkEntry> {
        val prefs = context.getSharedPreferences(PREFS_VIEWER_STATE, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_PREFIX_BOOKMARKS + pdfFile.absolutePath.hashCode(), null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<PdfBookmarkEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PdfBookmarkEntry(
                        title = obj.optString("title", "Page"),
                        pageIndex = obj.optInt("pageIndex", 0),
                        isDocumentOutline = false
                    )
                )
            }
            list.sortedBy { it.pageIndex }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addBookmarkToPdf(
        context: Context,
        pdfFile: File,
        title: String,
        pageIndex: Int,
        writeToPdfOutline: Boolean = false,
        password: String? = null
    ): List<PdfBookmarkEntry> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Page ${pageIndex + 1}" }
        val current = getSavedUserBookmarks(context, pdfFile).toMutableList()
        if (current.none { it.pageIndex == pageIndex && it.title.equals(cleanTitle, ignoreCase = true) }) {
            current.add(PdfBookmarkEntry(title = cleanTitle, pageIndex = pageIndex, isDocumentOutline = false))
        }
        saveUserBookmarksList(context, pdfFile, current)

        if (writeToPdfOutline && pdfFile.exists() && pdfFile.canWrite()) {
            var doc: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                doc = if (!password.isNullOrBlank()) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
                if (!doc.isEncrypted && pageIndex in 0 until doc.numberOfPages) {
                    val catalog = doc.documentCatalog
                    val outline = catalog.documentOutline ?: PDDocumentOutline().also {
                        catalog.documentOutline = it
                    }
                    val dest = PDPageFitWidthDestination().apply {
                        page = doc.getPage(pageIndex)
                    }
                    val item = PDOutlineItem().apply {
                        this.title = cleanTitle
                        this.destination = dest
                    }
                    outline.addLast(item)
                    doc.save(pdfFile)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    doc?.close()
                } catch (_: Exception) {
                }
            }
        }
        extractPdfBookmarksAndOutline(context, pdfFile, password)
    }

    suspend fun removeUserBookmark(
        context: Context,
        pdfFile: File,
        pageIndex: Int
    ): List<PdfBookmarkEntry> = withContext(Dispatchers.IO) {
        val updated = getSavedUserBookmarks(context, pdfFile).filterNot { it.pageIndex == pageIndex }
        saveUserBookmarksList(context, pdfFile, updated)
        extractPdfBookmarksAndOutline(context, pdfFile)
    }

    private fun saveUserBookmarksList(context: Context, pdfFile: File, bookmarks: List<PdfBookmarkEntry>) {
        val prefs = context.getSharedPreferences(PREFS_VIEWER_STATE, Context.MODE_PRIVATE)
        val arr = JSONArray()
        bookmarks.forEach { entry ->
            val obj = JSONObject()
            obj.put("title", entry.title)
            obj.put("pageIndex", entry.pageIndex)
            arr.put(obj)
        }
        prefs.edit()
            .putString(KEY_PREFIX_BOOKMARKS + pdfFile.absolutePath.hashCode(), arr.toString())
            .apply()
    }

    fun saveLastReadPage(context: Context, filePath: String, pageIndex: Int) {
        if (filePath.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_VIEWER_STATE, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_PREFIX_LAST_PAGE + filePath.hashCode(), pageIndex.coerceAtLeast(0))
            .apply()
    }

    fun getLastReadPage(context: Context, filePath: String): Int {
        if (filePath.isBlank()) return 0
        val prefs = context.getSharedPreferences(PREFS_VIEWER_STATE, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PREFIX_LAST_PAGE + filePath.hashCode(), 0).coerceAtLeast(0)
    }

    /**
     * Returns the 4x5 color matrix that inverts RGB channels for Night/Invert mode in the PDF viewer.
     */
    fun createNightInvertColorMatrix(): FloatArray = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )

    fun invertBitmapForNightMode(source: Bitmap): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            val r = 255 - Color.red(c)
            val g = 255 - Color.green(c)
            val b = 255 - Color.blue(c)
            pixels[i] = Color.argb(a, r, g, b)
        }
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    suspend fun deleteSinglePageFromPdf(
        context: Context,
        pdfFile: File,
        pageIndexToDelete: Int,
        outputFileName: String = "${pdfFile.nameWithoutExtension}_page_deleted.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            val totalPages = doc.numberOfPages
            if (totalPages <= 1 || pageIndexToDelete !in 0 until totalPages) {
                return@withContext null
            }
            val remainingIndices = (0 until totalPages).filter { it != pageIndexToDelete }
            doc.close()
            doc = null
            PdfEngine.splitPdf(context, pdfFile, remainingIndices, outputFileName, password)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 6. On-Device OCR (ML Kit Text Recognition -> Searchable PDF with Invisible Text Layer)
    // =========================================================================

    /**
     * Performs on-device OCR using the bundled ML Kit [TextRecognition] client and builds a
     * searchable PDF where the recognized text is written as an invisible text layer
     * ([RenderingMode.NEITHER]) aligned with the scanned page image.
     */
    suspend fun performOcrToSearchablePdf(
        context: Context,
        sourcePdfFile: File? = null,
        sourceImageUris: List<Uri> = emptyList(),
        outputFileName: String = "Searchable_OCR_${System.currentTimeMillis() / 1000}.pdf",
        password: String? = null
    ): OcrSearchablePdfResult? = withContext(Dispatchers.IO) {
        if (sourcePdfFile == null && sourceImageUris.isEmpty()) return@withContext null

        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val outputFile = PdfEngine.getUniqueOutputFile(outputDir, outputFileName)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var outDoc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            outDoc = PDDocument()
            val pageSummaries = mutableListOf<OcrPageSummary>()

            if (sourcePdfFile != null && sourcePdfFile.exists() && sourcePdfFile.length() > 0L) {
                val meta = PdfEngine.getPdfMetadata(context, Uri.fromFile(sourcePdfFile), password)
                val pageCount = meta?.pageCount ?: 0
                if (pageCount <= 0) return@withContext null

                // Also inspect if source PDF has any embedded text to seed high-accuracy OCR hints when needed
                val embeddedPageTexts = extractExistingPageTextsIfAny(context, sourcePdfFile, password, pageCount)

                for (pageIndex in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    val pageBitmap = PdfEngine.renderPageBitmap(
                        context = context,
                        pdfFile = sourcePdfFile,
                        pageIndex = pageIndex,
                        targetWidth = 1080,
                        password = password,
                        useCache = false
                    ) ?: continue

                    try {
                        val existingText = embeddedPageTexts[pageIndex].orEmpty().trim()
                        if (existingText.isNotEmpty() && TextRecognition.getBitmapTextHints(pageBitmap) == null) {
                            val hintLines = existingText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .mapIndexed { idx, line ->
                                    val top = (80 + idx * 36).coerceAtMost(pageBitmap.height - 30)
                                    TextRecognition.HintTextLine(
                                        text = line,
                                        boundingBox = Rect(40, top, (pageBitmap.width - 40).coerceAtLeast(100), top + 24)
                                    )
                                }
                            if (hintLines.isNotEmpty()) {
                                TextRecognition.registerBitmapTextHints(pageBitmap, hintLines)
                            }
                        }

                        val summary = appendOcrPageToDocument(
                            outDoc = outDoc,
                            recognizer = recognizer,
                            pageBitmap = pageBitmap,
                            pageIndex = pageIndex
                        )
                        pageSummaries.add(summary)
                    } finally {
                        pageBitmap.recycle()
                    }
                }
            } else {
                sourceImageUris.forEachIndexed { pageIndex, uri ->
                    coroutineContext.ensureActive()
                    val inputImage = InputImage.fromFilePath(context, uri)
                    val bitmap = inputImage.bitmap ?: return@forEachIndexed
                    try {
                        val summary = appendOcrPageToDocument(
                            outDoc = outDoc,
                            recognizer = recognizer,
                            pageBitmap = bitmap,
                            pageIndex = pageIndex
                        )
                        pageSummaries.add(summary)
                    } finally {
                        if (TextRecognition.getBitmapTextHints(bitmap) == null) {
                            bitmap.recycle()
                        }
                    }
                }
            }

            if (outDoc.numberOfPages <= 0) {
                outputFile.delete()
                return@withContext null
            }

            outDoc.save(outputFile)
            outDoc.close()
            outDoc = null

            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext null
            }

            val fullText = pageSummaries.joinToString("\n\n") { it.recognizedText }.trim()
            OcrSearchablePdfResult(
                outputFile = outputFile,
                pageCount = pageSummaries.size,
                fullRecognizedText = fullText,
                pageSummaries = pageSummaries,
                bundledModelCompressedMb = TextRecognizerOptions.BUNDLED_MODEL_COMPRESSED_SIZE_MB,
                bundledModelUncompressedMb = TextRecognizerOptions.BUNDLED_MODEL_UNCOMPRESSED_SIZE_MB
            )
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            try {
                recognizer.close()
            } catch (_: Exception) {
            }
            try {
                outDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun appendOcrPageToDocument(
        outDoc: PDDocument,
        recognizer: TextRecognizer,
        pageBitmap: Bitmap,
        pageIndex: Int
    ): OcrPageSummary {
        val inputImage = InputImage.fromBitmap(pageBitmap, 0)
        val visionText = recognizer.processSync(inputImage)

        val imgW = pageBitmap.width.coerceAtLeast(1)
        val imgH = pageBitmap.height.coerceAtLeast(1)
        val pdfW = 595f
        val pdfH = (pdfW * (imgH.toFloat() / imgW.toFloat())).coerceIn(300f, 1200f)

        val pageRect = PDRectangle(pdfW, pdfH)
        val page = PDPage(pageRect)
        outDoc.addPage(page)

        val jpegBytes = ByteArrayOutputStream().use { baos ->
            pageBitmap.compress(Bitmap.CompressFormat.JPEG, 88, baos)
            baos.toByteArray()
        }
        val pdImage = JPEGFactory.createFromByteArray(outDoc, jpegBytes)

        val allLines = visionText.textBlocks.flatMap { it.lines }
        PDPageContentStream(outDoc, page).use { cs ->
            // 1. Draw the visible scanned page image
            cs.drawImage(pdImage, 0f, 0f, pdfW, pdfH)

            // 2. Write the invisible searchable text layer (RenderingMode.NEITHER = PDF Tr 3)
            if (allLines.isNotEmpty()) {
                cs.beginText()
                cs.setRenderingMode(RenderingMode.NEITHER)
                val scaleX = pdfW / imgW.toFloat()
                val scaleY = pdfH / imgH.toFloat()

                for (line in allLines) {
                    val safeLine = sanitizePdfFontText(line.text)
                    if (safeLine.isBlank()) continue
                    val box = line.boundingBox ?: Rect(20, 40, imgW - 20, 64)
                    val pdfX = (box.left * scaleX).coerceIn(8f, pdfW - 20f)
                    val pdfY = (pdfH - (box.bottom * scaleY)).coerceIn(8f, pdfH - 16f)
                    val fontSize = ((box.height() * scaleY) * 0.85f).coerceIn(8f, 28f)

                    cs.setFont(PDType1Font.HELVETICA, fontSize)
                    cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(pdfX, pdfY))
                    cs.showText(safeLine)
                }
                cs.endText()
            }
        }

        return OcrPageSummary(
            pageIndex = pageIndex,
            recognizedText = visionText.text,
            blockCount = visionText.textBlocks.size,
            lineCount = allLines.size
        )
    }

    private fun extractExistingPageTextsIfAny(
        context: Context,
        pdfFile: File,
        password: String?,
        pageCount: Int
    ): Map<Int, String> {
        var doc: PDDocument? = null
        return try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }
            val stripper = PDFTextStripper()
            val map = mutableMapOf<Int, String>()
            for (i in 0 until min(pageCount, doc.numberOfPages)) {
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val txt = stripper.getText(doc)?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    map[i] = txt
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 7. Document Scanner Engine (Filters & Multi-Page PDF Export)
    // =========================================================================

    fun applyScannerFilterToBitmap(
        source: Bitmap,
        filter: ScannerFilterMode,
        rotationDegrees: Int = 0
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        if (filter == ScannerFilterMode.ORIGINAL) {
            return if (rotated === source) source.copy(Bitmap.Config.ARGB_8888, false) else rotated
        }

        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val colorMatrix = when (filter) {
            ScannerFilterMode.ORIGINAL -> ColorMatrix()
            ScannerFilterMode.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            ScannerFilterMode.AUTO_ENHANCE -> {
                // Boost contrast slightly while keeping natural document tones
                val contrast = 1.25f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScannerFilterMode.BLACK_AND_WHITE -> {
                // Desaturate + high contrast threshold for crisp black text on white paper
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 2.4f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val highContrast = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                gray.postConcat(highContrast)
                gray
            }
        }
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
            isFilterBitmap = true
        }
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        if (rotated !== source) {
            rotated.recycle()
        }
        return out
    }

    suspend fun exportScannedPagesToPdf(
        context: Context,
        pages: List<ScannedPageDraft>,
        outputFileName: String = "Scanned_Document_${System.currentTimeMillis() / 1000}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val outputFile = PdfEngine.getUniqueOutputFile(outputDir, outputFileName)

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument()

            for (draft in pages) {
                coroutineContext.ensureActive()
                val rawBitmap = decodeBitmapFromUri(context, draft.uri) ?: continue
                var processedBitmap: Bitmap? = null
                try {
                    processedBitmap = applyScannerFilterToBitmap(
                        source = rawBitmap,
                        filter = draft.filterMode,
                        rotationDegrees = draft.rotationDegrees
                    )
                    val pageW = 595f
                    val pageH = (pageW * (processedBitmap.height.toFloat() / processedBitmap.width.toFloat().coerceAtLeast(1f)))
                        .coerceIn(300f, 1200f)
                    val rect = PDRectangle(pageW, pageH)
                    val page = PDPage(rect)
                    doc.addPage(page)

                    val jpegBytes = ByteArrayOutputStream().use { baos ->
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                        baos.toByteArray()
                    }
                    val pdImage = JPEGFactory.createFromByteArray(doc, jpegBytes)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImage, 0f, 0f, pageW, pageH)
                    }
                } finally {
                    if (processedBitmap != null && processedBitmap !== rawBitmap) {
                        processedBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            }

            if (doc.numberOfPages <= 0) {
                outputFile.delete()
                return@withContext null
            }

            doc.save(outputFile)
            doc.close()
            doc = null

            if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                BitmapFactory.decodeFile(path)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 8. Batch Support (Batch Compress & Batch Convert with Combined Progress)
    // =========================================================================

    suspend fun batchCompressPdfs(
        context: Context,
        files: List<File>,
        level: PdfCompressionLevel,
        mode: PdfCompressionMode = PdfCompressionMode.IMAGE_RECOMPRESSION,
        onProgress: (BatchProgressState) -> Unit = {}
    ): BatchProgressState = withContext(Dispatchers.IO) {
        if (files.isEmpty()) {
            val empty = BatchProgressState(isBatchRunning = false, isFinished = true)
            onProgress(empty)
            return@withContext empty
        }

        val itemResults = files.map { file ->
            BatchFileItemResult(
                sourceName = file.name,
                status = BatchItemStatus.PENDING,
                originalSizeBytes = file.length()
            )
        }.toMutableList()

        for (index in files.indices) {
            coroutineContext.ensureActive()
            val file = files[index]
            itemResults[index] = itemResults[index].copy(status = BatchItemStatus.RUNNING, message = "Compressing...")
            val startPercent = ((index.toFloat() / files.size) * 100).toInt()
            onProgress(
                BatchProgressState(
                    isBatchRunning = true,
                    totalFiles = files.size,
                    completedFiles = index,
                    currentFileName = file.name,
                    overallProgressPercent = startPercent,
                    items = itemResults.toList(),
                    isFinished = false
                )
            )

            val outName = "${file.nameWithoutExtension}_compressed.pdf"
            val compressResult = PdfEngine.compressPdfDetailed(
                context = context,
                pdfFile = file,
                level = level,
                outputFileName = outName,
                mode = mode
            )
            when (compressResult) {
                is PdfEngine.CompressResult.Success -> {
                    itemResults[index] = itemResults[index].copy(
                        status = BatchItemStatus.SUCCEEDED,
                        outputSizeBytes = compressResult.compressedSize,
                        outputFile = compressResult.file,
                        message = "Saved ${compressResult.file.name}"
                    )
                }
                is PdfEngine.CompressResult.NoReduction -> {
                    itemResults[index] = itemResults[index].copy(
                        status = BatchItemStatus.FAILED,
                        message = "Already optimal (no reduction)"
                    )
                }
                is PdfEngine.CompressResult.Error -> {
                    itemResults[index] = itemResults[index].copy(
                        status = BatchItemStatus.FAILED,
                        message = compressResult.message
                    )
                }
            }
        }

        val finalState = BatchProgressState(
            isBatchRunning = false,
            totalFiles = files.size,
            completedFiles = files.size,
            currentFileName = "",
            overallProgressPercent = 100,
            items = itemResults.toList(),
            isFinished = true
        )
        onProgress(finalState)
        finalState
    }

    suspend fun batchConvertPdfsToText(
        context: Context,
        files: List<File>,
        onProgress: (BatchProgressState) -> Unit = {}
    ): BatchProgressState = withContext(Dispatchers.IO) {
        if (files.isEmpty()) {
            val empty = BatchProgressState(isBatchRunning = false, isFinished = true)
            onProgress(empty)
            return@withContext empty
        }

        val itemResults = files.map { file ->
            BatchFileItemResult(
                sourceName = file.name,
                status = BatchItemStatus.PENDING,
                originalSizeBytes = file.length()
            )
        }.toMutableList()

        for (index in files.indices) {
            coroutineContext.ensureActive()
            val file = files[index]
            itemResults[index] = itemResults[index].copy(status = BatchItemStatus.RUNNING, message = "Converting...")
            val startPercent = ((index.toFloat() / files.size) * 100).toInt()
            onProgress(
                BatchProgressState(
                    isBatchRunning = true,
                    totalFiles = files.size,
                    completedFiles = index,
                    currentFileName = file.name,
                    overallProgressPercent = startPercent,
                    items = itemResults.toList(),
                    isFinished = false
                )
            )

            val outName = "${file.nameWithoutExtension}_text.txt"
            when (val res = PdfEngine.extractTextFromPdfDetailed(context, file, null, outName)) {
                is PdfEngine.TextExtractResult.Success -> {
                    itemResults[index] = itemResults[index].copy(
                        status = BatchItemStatus.SUCCEEDED,
                        outputSizeBytes = res.file.length(),
                        outputFile = res.file,
                        message = "Extracted to ${res.file.name}"
                    )
                }
                is PdfEngine.TextExtractResult.Error -> {
                    itemResults[index] = itemResults[index].copy(
                        status = BatchItemStatus.FAILED,
                        message = res.message
                    )
                }
            }
        }

        val finalState = BatchProgressState(
            isBatchRunning = false,
            totalFiles = files.size,
            completedFiles = files.size,
            currentFileName = "",
            overallProgressPercent = 100,
            items = itemResults.toList(),
            isFinished = true
        )
        onProgress(finalState)
        finalState
    }

    private fun sanitizePdfFontText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch.code in 32..126 || ch.code in 160..255) {
                sb.append(ch)
            } else if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ')
            }
        }
        return sb.toString()
    }
}
