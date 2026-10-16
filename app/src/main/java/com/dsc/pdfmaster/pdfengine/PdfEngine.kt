package com.dsc.pdfmaster.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

object PdfEngine {

    sealed class ExtractResult {
        data class Success(
            val file: File,
            val pageCount: Int,
            val fileSizeBytes: Long
        ) : ExtractResult()

        data class Error(
            val message: String,
            val isPasswordProtected: Boolean = false
        ) : ExtractResult()
    }

    // Standard A4 dimensions in PDF points (72 dpi)
    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    data class PdfMetadata(
        val fileName: String,
        val filePath: String,
        val fileSizeBytes: Long,
        val pageCount: Int,
        val formattedSize: String
    )

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    suspend fun getPdfMetadata(context: Context, uri: Uri, password: String? = null): PdfMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = getFileFromUri(context, uri) ?: return@withContext null
            var pageCount = 0

            val isEncrypted = checkPasswordProtected(context, file)
            if (isEncrypted) {
                PDFBoxResourceLoader.init(context)
                val doc = if (!password.isNullOrBlank()) PDDocument.load(file, password) else PDDocument.load(file)
                pageCount = doc.numberOfPages
                doc.close()
            } else {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            }

            PdfMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                pageCount = pageCount,
                formattedSize = formatFileSize(file.length())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun renderPageBitmap(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 800,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!password.isNullOrBlank()) {
            return@withContext renderPageBitmapWithPdfBox(context, pdfFile, pageIndex, targetWidth, password)
        }
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                        return@withContext null
                    }

                    renderer.openPage(pageIndex).use { page ->
                        val aspectRatio = page.height.toFloat() / page.width.toFloat()
                        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return@withContext bitmap
                    }
                }
            }
        } catch (e: Exception) {
            renderPageBitmapWithPdfBox(context, pdfFile, pageIndex, targetWidth, password ?: "")
        }
    }

    private fun renderPageBitmapWithPdfBox(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int,
        password: String
    ): Bitmap? {
        try {
            PDFBoxResourceLoader.init(context)
            val doc = PDDocument.load(pdfFile, password)
            if (pageIndex < 0 || pageIndex >= doc.numberOfPages) {
                doc.close()
                return null
            }
            val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
            val originalPage = doc.getPage(pageIndex)
            val cropBox = originalPage.cropBox
            val pageWidth = cropBox.width
            val scale = (targetWidth.toFloat() / pageWidth)
            val bitmap = pdfBoxRenderer.renderImage(pageIndex, scale * 1.5f)
            doc.close()
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun loadPdfPageThumbnails(
        context: Context,
        pdfFile: File
    ): List<PdfPageItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PdfPageItem>()
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount

                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val targetWidth = 300
                            val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            list.add(PdfPageItem(pageIndex = i, bitmap = bitmap))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    // Convert List of Images to PDF
    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFileName: String = "Images_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext null
        var outputFile: File? = null
        try {
            val pdfDocument = PdfDocument()
            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            for ((index, uri) in imageUris.withIndex()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                val maxDimension = 2048
                var sampleSize = 1
                if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                    val halfWidth = options.outWidth / 2
                    val halfHeight = options.outHeight / 2
                    while (halfWidth / sampleSize >= maxDimension && halfHeight / sampleSize >= maxDimension) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val originalBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                }

                if (originalBitmap != null) {
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas

                        canvas.drawColor(Color.WHITE)

                        val margin = 20
                        val maxWidth = A4_WIDTH - (margin * 2)
                        val maxHeight = A4_HEIGHT - (margin * 2)

                        val srcWidth = originalBitmap.width
                        val srcHeight = originalBitmap.height
                        val scale = Math.min(maxWidth.toFloat() / srcWidth, maxHeight.toFloat() / srcHeight)

                        val destWidth = (srcWidth * scale).toInt()
                        val destHeight = (srcHeight * scale).toInt()

                        val left = margin + (maxWidth - destWidth) / 2
                        val top = margin + (maxHeight - destHeight) / 2

                        val destRect = Rect(left, top, left + destWidth, top + destHeight)
                        canvas.drawBitmap(originalBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                        pdfDocument.finishPage(page)
                    } finally {
                        originalBitmap.recycle()
                    }
                }
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    // Convert Raw Text to Multi-page PDF Document using PDFBox
    suspend fun createTextPdf(
        context: Context,
        textContent: String,
        fontSizeSp: Float = 14f,
        marginDp: Int = 36,
        outputFileName: String = "Text_Doc_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        if (textContent.isBlank()) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val doc = PDDocument()
            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            val fontSize = fontSizeSp
            val leading = 1.5f * fontSize

            val margin = marginDp.toFloat()
            val width = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width
            val height = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.height
            val usableWidth = width - 2 * margin

            val lines = mutableListOf<String>()
            for (para in textContent.split("\n")) {
                if (para.isBlank()) {
                    lines.add("")
                    continue
                }
                var currentLine = ""
                for (word in para.split(" ")) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val textWidth = font.getStringWidth(testLine) / 1000 * fontSize
                    if (textWidth <= usableWidth) {
                        currentLine = testLine
                    } else {
                        lines.add(currentLine)
                        currentLine = word
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
            }

            var page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
            contentStream.beginText()
            contentStream.setFont(font, fontSize)
            contentStream.newLineAtOffset(margin, height - margin - fontSize)

            var currentHeight = margin + fontSize

            for (line in lines) {
                if (currentHeight + leading > height - margin) {
                    contentStream.endText()
                    contentStream.close()

                    page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    doc.addPage(page)
                    contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(margin, height - margin - fontSize)
                    currentHeight = margin + fontSize
                }

                val safeLine = line.replace("\r", "").replace("\t", "    ")
                try {
                    contentStream.showText(safeLine)
                } catch (e: Exception) {
                    contentStream.showText(safeLine.filter { it.code in 32..126 })
                }
                contentStream.newLineAtOffset(0f, -leading)
                currentHeight += leading
            }

            contentStream.endText()
            contentStream.close()

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            doc.save(outputFile)
            doc.close()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    // Merge Multiple PDFs into One using Structural PDFBox Import
    suspend fun mergePdfs(
        context: Context,
        pdfFiles: List<File>,
        outputFileName: String = "Merged_${System.currentTimeMillis()}.pdf",
        passwords: Map<File, String> = emptyMap()
    ): File? = withContext(Dispatchers.IO) {
        if (pdfFiles.size < 2) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val mergedDoc = PDDocument()
            val loadedDocs = mutableListOf<PDDocument>()

            for (file in pdfFiles) {
                if (!file.exists() || file.length() == 0L) continue
                val password = passwords[file]
                val doc = try {
                    if (!password.isNullOrBlank()) {
                        PDDocument.load(file, password)
                    } else {
                        PDDocument.load(file)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fail the entire merge if any file fails to load
                    mergedDoc.close()
                    loadedDocs.forEach { it.close() }
                    return@withContext null
                }
                loadedDocs.add(doc)
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    mergedDoc.importPage(page)
                }
            }

            if (mergedDoc.numberOfPages == 0) {
                mergedDoc.close()
                loadedDocs.forEach { it.close() }
                return@withContext null
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            mergedDoc.save(outputFile)
            mergedDoc.close()
            loadedDocs.forEach { it.close() }

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    /**
     * Prevents overwriting files by appending a counter e.g. document_extracted (1).pdf
     */
    fun getUniqueOutputFile(dir: File, desiredName: String): File {
        val cleanName = if (desiredName.endsWith(".pdf", ignoreCase = true)) {
            desiredName.substring(0, desiredName.length - 4)
        } else {
            desiredName
        }
        var candidate = File(dir, "$cleanName.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$cleanName ($counter).pdf")
            counter++
        }
        return candidate
    }

    /**
     * Checks if a PDF document is password-protected/encrypted
     */
    suspend fun checkPasswordProtected(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context)
            val doc = PDDocument.load(file)
            val isEnc = doc.isEncrypted
            doc.close()
            isEnc
        } catch (e: InvalidPasswordException) {
            true
        } catch (e: Exception) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                renderer.close()
                pfd.close()
                false
            } catch (se: SecurityException) {
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Real PDF Page Extraction preserving text, vectors, images, page dimensions,
     * orientation, links, and annotations without rasterization.
     */
    suspend fun extractPages(
        context: Context,
        pdfFile: File,
        pageIndices: List<Int>, // 0-based page indices
        outputFileName: String,
        password: String? = null
    ): ExtractResult = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext ExtractResult.Error("Source PDF file does not exist or is empty")
        }
        if (pageIndices.isEmpty()) {
            return@withContext ExtractResult.Error("No pages selected for extraction")
        }

        try {
            PDFBoxResourceLoader.init(context)
            val sourceDoc = try {
                if (!password.isNullOrBlank()) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
            } catch (e: InvalidPasswordException) {
                return@withContext ExtractResult.Error(
                    message = "This PDF is password protected",
                    isPasswordProtected = true
                )
            }

            val totalPages = sourceDoc.numberOfPages
            val validIndices = pageIndices.filter { it in 0 until totalPages }.distinct().sorted()
            if (validIndices.isEmpty()) {
                sourceDoc.close()
                return@withContext ExtractResult.Error("Selected page numbers exceed total document pages ($totalPages)")
            }

            val newDoc = PDDocument()
            for (idx in validIndices) {
                val page = sourceDoc.getPage(idx)
                newDoc.importPage(page)
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            val outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)
            newDoc.close()
            sourceDoc.close()

            if (outputFile.exists() && outputFile.length() > 0) {
                ExtractResult.Success(
                    file = outputFile,
                    pageCount = validIndices.size,
                    fileSizeBytes = outputFile.length()
                )
            } else {
                ExtractResult.Error("Failed to generate extracted PDF file")
            }
        } catch (e: InvalidPasswordException) {
            ExtractResult.Error("This PDF is password protected", isPasswordProtected = true)
        } catch (e: Exception) {
            e.printStackTrace()
            ExtractResult.Error("Extraction failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    // Split PDF into specific page range or single pages using structural copying
    suspend fun splitPdf(
        context: Context,
        pdfFile: File,
        pageIndicesToKeep: List<Int>,
        outputFileName: String = "Split_${System.currentTimeMillis()}.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L || pageIndicesToKeep.isEmpty()) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val sourceDoc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            val totalPages = sourceDoc.numberOfPages
            val validIndices = pageIndicesToKeep.filter { it in 0 until totalPages }
            if (validIndices.isEmpty()) {
                sourceDoc.close()
                return@withContext null
            }

            val newDoc = PDDocument()
            for (idx in validIndices) {
                val page = sourceDoc.getPage(idx)
                newDoc.importPage(page)
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)
            newDoc.close()
            sourceDoc.close()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    // Compress PDF File Size structurally using PDFBox
    suspend fun compressPdf(
        context: Context,
        pdfFile: File,
        level: PdfCompressionLevel,
        outputFileName: String = "Compressed_${System.currentTimeMillis()}.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            // Compress embedded images inside the PDF document
            for (page in doc.pages) {
                val resources = page.resources ?: continue
                for (name in resources.xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val imageObj = resources.getXObject(name) as? PDImageXObject ?: continue
                        val bitmap = try { imageObj.image } catch (e: Exception) { null } ?: continue
                        
                        // Avoid converting images with transparency to JPEG as it will render a black background
                        if (!bitmap.hasAlpha()) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, level.qualityPercentage, stream)
                            val compressedBytes = stream.toByteArray()
                            val newImage = JPEGFactory.createFromByteArray(doc, compressedBytes)
                            resources.put(name, newImage)
                        }
                        bitmap.recycle()
                    }
                }
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            doc.save(outputFile)
            doc.close()

            if (outputFile.exists() && outputFile.length() > 0) {
                // Return original if compression actually increased file size
                if (outputFile.length() >= pdfFile.length()) {
                    outputFile.delete()
                    return@withContext pdfFile
                }
                outputFile 
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    // Organize Pages: Rotate, Delete, Reorder structurally using PDFBox
    suspend fun organizePages(
        context: Context,
        pdfFile: File,
        pageItems: List<PdfPageItem>,
        outputFileName: String = "Organized_${System.currentTimeMillis()}.pdf",
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val sourceDoc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            val newDoc = PDDocument()
            for (item in pageItems) {
                if (!item.isSelected) { // Not marked for deletion
                    if (item.pageIndex in 0 until sourceDoc.numberOfPages) {
                        val page = sourceDoc.getPage(item.pageIndex)
                        val importedPage = newDoc.importPage(page)
                        if (item.rotationDegrees != 0) {
                            val currentRot = importedPage.rotation
                            importedPage.rotation = (currentRot + item.rotationDegrees) % 360
                        }
                    }
                }
            }

            if (newDoc.numberOfPages == 0) {
                newDoc.close()
                sourceDoc.close()
                return@withContext null
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)
            newDoc.close()
            sourceDoc.close()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    /**
     * Real PDF Encryption using PDFBox StandardProtectionPolicy (128-bit AES / Password Protection)
     */
    suspend fun protectPdf(
        context: Context,
        pdfFile: File,
        userPassword: String,
        ownerPassword: String = userPassword,
        outputName: String = "Protected_${System.currentTimeMillis()}.pdf",
        inPlace: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L || userPassword.isBlank()) return@withContext null
        val tempEncrypted = File(context.cacheDir, "temp_encrypt_${System.currentTimeMillis()}.pdf")
        try {
            PDFBoxResourceLoader.init(context)
            val doc = PDDocument.load(pdfFile)

            val permissions = AccessPermission()
            val policy = StandardProtectionPolicy(
                if (ownerPassword.isBlank()) userPassword else ownerPassword,
                userPassword,
                permissions
            )
            policy.encryptionKeyLength = 128
            doc.protect(policy)

            doc.save(tempEncrypted)
            doc.close()

            // Verify encryption and password validity before finalizing
            val isEncrypted = checkPasswordProtected(context, tempEncrypted)
            val isPasswordValid = verifyPdfPassword(context, tempEncrypted, userPassword)

            if (!isEncrypted || !isPasswordValid) {
                tempEncrypted.delete()
                return@withContext null
            }

            if (inPlace && pdfFile.canWrite()) {
                // Safely overwrite original file in-place
                FileOutputStream(pdfFile).use { fos ->
                    tempEncrypted.inputStream().use { fis ->
                        fis.copyTo(fos)
                    }
                }
                tempEncrypted.delete()
                return@withContext pdfFile
            } else {
                val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
                val outputFile = getUniqueOutputFile(outputDir, outputName)
                if (tempEncrypted.renameTo(outputFile)) {
                    return@withContext outputFile
                } else {
                    FileOutputStream(outputFile).use { fos ->
                        tempEncrypted.inputStream().use { fis ->
                            fis.copyTo(fos)
                        }
                    }
                    tempEncrypted.delete()
                    return@withContext outputFile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempEncrypted.delete()
            null
        }
    }

    /**
     * Remove Password Protection from PDF using PDFBox with Strict Output Validation
     */
    suspend fun removePassword(
        context: Context,
        pdfFile: File,
        currentPassword: String,
        outputName: String = "Unprotected_${System.currentTimeMillis()}.pdf",
        inPlace: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L || currentPassword.isBlank()) return@withContext null
        val tempUnprotected = File(context.cacheDir, "temp_unprotect_${System.currentTimeMillis()}.pdf")
        try {
            PDFBoxResourceLoader.init(context)
            val doc = try {
                PDDocument.load(pdfFile, currentPassword)
            } catch (e: InvalidPasswordException) {
                return@withContext null
            }
            doc.isAllSecurityToBeRemoved = true

            doc.save(tempUnprotected)
            doc.close()

            // Strict Validation: Output must exist, be unencrypted, and be loadable without password
            val isStillEncrypted = checkPasswordProtected(context, tempUnprotected)
            if (isStillEncrypted || !tempUnprotected.exists() || tempUnprotected.length() == 0L) {
                tempUnprotected.delete()
                return@withContext null
            }

            var isValidDocument = false
            try {
                val testDoc = PDDocument.load(tempUnprotected)
                isValidDocument = testDoc.numberOfPages > 0 && !testDoc.isEncrypted
                testDoc.close()
            } catch (e: Exception) {
                isValidDocument = false
            }

            if (!isValidDocument) {
                tempUnprotected.delete()
                return@withContext null
            }

            if (inPlace && pdfFile.canWrite()) {
                FileOutputStream(pdfFile).use { fos ->
                    tempUnprotected.inputStream().use { fis ->
                        fis.copyTo(fos)
                    }
                }
                tempUnprotected.delete()
                return@withContext pdfFile
            } else {
                val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
                val outputFile = getUniqueOutputFile(outputDir, outputName)
                if (tempUnprotected.renameTo(outputFile)) {
                    return@withContext outputFile
                } else {
                    FileOutputStream(outputFile).use { fos ->
                        tempUnprotected.inputStream().use { fis ->
                            fis.copyTo(fos)
                        }
                    }
                    tempUnprotected.delete()
                    return@withContext outputFile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempUnprotected.delete()
            null
        }
    }

    // Convert PDF Pages to PNG/JPEG Images using PDFBox
    suspend fun convertPdfToImages(
        context: Context,
        pdfFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        password: String? = null,
        outputPrefix: String = pdfFile.nameWithoutExtension
    ): List<File> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext emptyList()
        val generatedImages = mutableListOf<File>()
        try {
            PDFBoxResourceLoader.init(context)
            val doc = if (!password.isNullOrBlank()) PDDocument.load(pdfFile, password) else PDDocument.load(pdfFile)
            val pageCount = doc.numberOfPages
            if (pageCount == 0) {
                doc.close()
                return@withContext emptyList()
            }

            val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
            val outputDir = File(context.filesDir, "output_images").apply { mkdirs() }
            val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"

            for (i in 0 until pageCount) {
                val bitmap = try {
                    pdfBoxRenderer.renderImage(i, 1.5f)
                } catch (e: Exception) {
                    null
                } ?: continue

                val imgFile = File(outputDir, "${outputPrefix}_page_${i + 1}.$ext")
                FileOutputStream(imgFile).use { fos ->
                    bitmap.compress(format, 90, fos)
                }
                bitmap.recycle()
                if (imgFile.exists() && imgFile.length() > 0) {
                    generatedImages.add(imgFile)
                }
            }
            doc.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        generatedImages
    }

    // Extract Plain Text from PDF using PDFBox
    suspend fun extractTextFromPdf(
        context: Context,
        pdfFile: File,
        password: String? = null,
        outputName: String = "${pdfFile.nameWithoutExtension}_text.txt"
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            val doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val textContent = stripper.getText(doc)
            doc.close()

            if (textContent.isBlank()) return@withContext null

            val outputDir = File(context.filesDir, "output_texts").apply { mkdirs() }
            val cleanName = if (outputName.endsWith(".txt", ignoreCase = true)) outputName else "$outputName.txt"
            outputFile = File(outputDir, cleanName)

            FileOutputStream(outputFile).use { fos ->
                fos.write(textContent.toByteArray(Charsets.UTF_8))
            }

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    // Image Format Converter (PNG <-> JPG)
    suspend fun convertImageFormat(
        context: Context,
        imageUri: Uri,
        targetFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        outputName: String = "image_converted.${if (targetFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"}"
    ): File? = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return@withContext null

            val outputDir = File(context.filesDir, "output_images").apply { mkdirs() }
            outputFile = File(outputDir, outputName)

            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(targetFormat, 90, fos)
            }
            bitmap.recycle()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile?.delete()
            null
        }
    }

    /**
     * Verifies if the given password opens the encrypted PDF document
     */
    suspend fun verifyPdfPassword(
        context: Context,
        pdfFile: File,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext false
        try {
            PDFBoxResourceLoader.init(context)
            val doc = PDDocument.load(pdfFile, password)
            val pages = doc.numberOfPages
            doc.close()
            pages > 0
        } catch (e: InvalidPasswordException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    // Generate starter sample PDF on app first launch
    suspend fun createSampleWelcomePdf(context: Context): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val sampleFile = File(outputDir, "PDF_Master_Starter_Guide.pdf")

        if (sampleFile.exists() && sampleFile.length() > 0) return@withContext sampleFile

        val pdfDocument = PdfDocument()

        val page1Info = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val canvas1 = page1.canvas

        canvas1.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0A0A0D")
            style = Paint.Style.FILL
        }
        canvas1.drawRect(0f, 0f, A4_WIDTH.toFloat(), 120f, headerPaint)

        val accentPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        canvas1.drawRect(0f, 116f, A4_WIDTH.toFloat(), 120f, accentPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas1.drawText("PDF Master - Starter Guide", 30f, 70f, titlePaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 14f
            isAntiAlias = true
        }

        val bodyLines = listOf(
            "Welcome to PDF Master by DarkSkullCorporation!",
            "",
            "Core Features Available in Version 1.0:",
            "  - Merge PDFs: Combine multiple documents into one single PDF.",
            "  - Split PDF: Extract specific page ranges quickly.",
            "  - Compress PDF: Reduce file sizes for easy sharing.",
            "  - Images to PDF: Convert photos into clean PDF documents.",
            "  - Organize Pages: Delete, rotate, or reorder pages visually.",
            "  - Password Protect: Encrypt PDFs with secure passwords.",
            "  - Fast Reader: High-resolution rendering with zoom & bookmarks.",
            "",
            "This sample file is ready to test with any PDF Master tools!"
        )

        var yPos = 170f
        for (line in bodyLines) {
            canvas1.drawText(line, 40f, yPos, textPaint)
            yPos += 24f
        }

        pdfDocument.finishPage(page1)

        val page2Info = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        val canvas2 = page2.canvas

        canvas2.drawColor(Color.WHITE)
        canvas2.drawRect(0f, 0f, A4_WIDTH.toFloat(), 60f, headerPaint)

        val header2Paint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas2.drawText("Page 2 - Sample Data Sheet", 30f, 40f, header2Paint)

        val contentLines2 = listOf(
            "Sample Document Metadata:",
            "  - Generated Date: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            "  - Engine: PDFBox & Native Android PDF Engine",
            "  - Security: Local On-Device Encryption & Private Processing",
            "",
            "Try merging this sample with another PDF or encrypting it!"
        )

        yPos = 120f
        for (line in contentLines2) {
            canvas2.drawText(line, 40f, yPos, textPaint)
            yPos += 26f
        }

        pdfDocument.finishPage(page2)

        val fos = FileOutputStream(sampleFile)
        pdfDocument.writeTo(fos)
        fos.close()
        pdfDocument.close()

        sampleFile
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val scheme = uri.scheme
        if (scheme == "file") {
            return uri.path?.let { File(it) }
        }
        if (scheme == "content") {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val tempFile = File.createTempFile("pdf_temp_", ".pdf", context.cacheDir)
                val outputStream = FileOutputStream(tempFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                return tempFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
