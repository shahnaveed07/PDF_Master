package com.dsc.pdfmaster.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

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

    enum class UnprotectError {
        FILE_NOT_FOUND_OR_EMPTY,
        INVALID_OR_CORRUPTED_PDF,
        NOT_ENCRYPTED,
        PASSWORD_REQUIRED,
        OWNER_PASSWORD_REQUIRED,
        INCORRECT_PASSWORD,
        UNSUPPORTED_ENCRYPTION,
        OUTPUT_WRITE_FAILED,
        OUTPUT_VALIDATION_FAILED,
        REPLACE_NOT_PERMITTED,
        CANCELLED
    }

    sealed class UnprotectResult {
        data class Success(
            val file: File,
            val pageCount: Int,
            val fileSizeBytes: Long,
            val replacedOriginal: Boolean = false
        ) : UnprotectResult()

        data class Error(
            val error: UnprotectError,
            val message: String
        ) : UnprotectResult()
    }

    sealed class PdfSecurityState {
        data class Unencrypted(val pageCount: Int) : PdfSecurityState()
        object UserPasswordRequired : PdfSecurityState()
        data class OwnerPasswordRestricted(val pageCount: Int) : PdfSecurityState()
        data class EncryptedWithBlankPassword(val pageCount: Int) : PdfSecurityState()
        data class UnsupportedEncryption(val message: String) : PdfSecurityState()
        data class InvalidOrCorrupted(val message: String) : PdfSecurityState()
    }

    sealed class PdfToImagesResult {
        data class Success(
            val files: List<File>,
            val format: Bitmap.CompressFormat,
            val pageCount: Int
        ) : PdfToImagesResult()

        data class Error(
            val message: String,
            val isPasswordProtected: Boolean = false
        ) : PdfToImagesResult()
    }

    sealed class TextExtractResult {
        data class Success(
            val file: File,
            val extractedText: String,
            val charCount: Int
        ) : TextExtractResult()

        data class Error(
            val message: String,
            val isPasswordProtected: Boolean = false
        ) : TextExtractResult()
    }

    sealed class ImageConversionResult {
        data class Success(
            val file: File,
            val width: Int,
            val height: Int,
            val format: Bitmap.CompressFormat
        ) : ImageConversionResult()

        data class Error(
            val message: String
        ) : ImageConversionResult()
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

    /**
     * Checks whether [file] is strictly inside [directory] using canonical path component matching,
     * avoiding prefix collisions on similarly named sibling directories.
     */
    fun isInsideDirectory(file: File, directory: File): Boolean {
        return try {
            val filePath = file.canonicalFile.toPath()
            val dirPath = directory.canonicalFile.toPath()
            filePath.startsWith(dirPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies whether a file starts with a valid `%PDF-` signature within the first 1024 bytes.
     */
    fun hasPdfHeader(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 5L) return false
        return try {
            val buffer = ByteArray(minOf(file.length().toInt(), 1024))
            FileInputStream(file).use { fis ->
                val read = fis.read(buffer)
                if (read < 5) return false
                val headerText = String(buffer, 0, read, Charsets.ISO_8859_1)
                headerText.contains("%PDF-")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasUnsupportedEncryptFilterInRawBytes(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L) return false
        return try {
            val maxBytes = minOf(file.length().toInt(), 65536)
            val buffer = ByteArray(maxBytes)
            FileInputStream(file).use { fis ->
                val read = fis.read(buffer)
                if (read <= 0) return false
                val content = String(buffer, 0, read, Charsets.ISO_8859_1)
                if (!content.contains("/Encrypt")) return false
                if (content.contains("/PubSec") ||
                    content.contains("/adbe.pkcs7") ||
                    content.contains("/Unsupported") ||
                    content.contains("/CustomSecurity") ||
                    Regex("/V\\s+[5-9]").containsMatchIn(content) ||
                    Regex("/R\\s+[6-9]").containsMatchIn(content)
                ) {
                    return true
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isUnsupportedEncryptionException(t: Throwable, file: File): Boolean {
        if (hasUnsupportedEncryptFilterInRawBytes(file)) return true
        var current: Throwable? = t
        while (current != null) {
            if (current is UnsupportedOperationException ||
                current is java.security.GeneralSecurityException
            ) {
                return true
            }
            val msg = current.message?.lowercase(Locale.US) ?: ""
            if (msg.contains("security handler") ||
                msg.contains("unsupported") ||
                msg.contains("encryption") ||
                msg.contains("cipher") ||
                msg.contains("algorithm") ||
                msg.contains("crypt filter") ||
                msg.contains("publickey") ||
                msg.contains("certificate") ||
                msg.contains("key length") ||
                msg.contains("revision")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Inspects a PDF file's security state to distinguish unencrypted documents,
     * user/open-password protected documents, owner-password permission-restricted documents,
     * blank-password encrypted documents, unsupported encryption, and malformed files.
     */
    suspend fun inspectPdfSecurity(context: Context, file: File): PdfSecurityState = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return@withContext PdfSecurityState.InvalidOrCorrupted("Selected PDF file does not exist or is empty.")
        }
        if (!hasPdfHeader(file)) {
            return@withContext PdfSecurityState.InvalidOrCorrupted("The selected file is not a valid PDF document.")
        }
        if (hasUnsupportedEncryptFilterInRawBytes(file)) {
            return@withContext PdfSecurityState.UnsupportedEncryption(
                "This PDF uses an encryption algorithm or security handler that is not supported locally."
            )
        }

        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(file)
            val pageCount = doc.numberOfPages
            if (pageCount <= 0) {
                return@withContext PdfSecurityState.InvalidOrCorrupted("PDF document contains no pages.")
            }
            // Verify first page is accessible
            doc.getPage(0)

            if (!doc.isEncrypted) {
                PdfSecurityState.Unencrypted(pageCount)
            } else {
                val ap = doc.currentAccessPermission
                if (ap != null && ap.isOwnerPermission) {
                    PdfSecurityState.EncryptedWithBlankPassword(pageCount)
                } else {
                    PdfSecurityState.OwnerPasswordRestricted(pageCount)
                }
            }
        } catch (e: InvalidPasswordException) {
            PdfSecurityState.UserPasswordRequired
        } catch (e: Throwable) {
            if (isUnsupportedEncryptionException(e, file)) {
                PdfSecurityState.UnsupportedEncryption(
                    "Unsupported PDF encryption configuration: ${e.localizedMessage ?: "Unsupported security handler"}"
                )
            } else {
                PdfSecurityState.InvalidOrCorrupted(
                    "Invalid or corrupted PDF document: ${e.localizedMessage ?: "Unable to read PDF structure"}"
                )
            }
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun getPdfMetadata(context: Context, uri: Uri, password: String? = null): PdfMetadata? = withContext(Dispatchers.IO) {
        var tempCopy: File? = null
        try {
            val isContentUri = uri.scheme == "content"
            val file = getFileFromUri(context, uri)?.also {
                if (isContentUri) tempCopy = it
            } ?: return@withContext null

            if (!file.exists() || file.length() == 0L || !hasPdfHeader(file)) {
                return@withContext null
            }

            var pageCount = 0
            val isEncrypted = checkPasswordProtected(context, file)
            if (isEncrypted) {
                PDFBoxResourceLoader.init(context)
                val doc = if (!password.isNullOrEmpty()) PDDocument.load(file, password) else PDDocument.load(file)
                try {
                    pageCount = doc.numberOfPages
                } finally {
                    doc.close()
                }
            } else {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pageCount = renderer.pageCount
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    PDFBoxResourceLoader.init(context)
                    PDDocument.load(file).use { doc ->
                        pageCount = doc.numberOfPages
                    }
                }
            }

            if (pageCount <= 0) return@withContext null

            PdfMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                pageCount = pageCount,
                formattedSize = formatFileSize(file.length())
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        } finally {
            tempCopy?.delete()
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
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
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
        var doc: PDDocument? = null
        return try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(pdfFile, password)
            if (pageIndex < 0 || pageIndex >= doc.numberOfPages) {
                return null
            }
            val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
            val originalPage = doc.getPage(pageIndex)
            val cropBox = originalPage.cropBox
            val pageWidth = cropBox.width.coerceAtLeast(1f)
            val scale = (targetWidth.toFloat() / pageWidth).coerceIn(0.2f, 4.0f)
            pdfBoxRenderer.renderImage(pageIndex, scale * 1.5f)
        } catch (e: Exception) {
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
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
                        coroutineContext.ensureActive()
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Fallback to PDFBox if PdfRenderer fails
            var doc: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                doc = PDDocument.load(pdfFile)
                val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
                for (i in 0 until doc.numberOfPages) {
                    coroutineContext.ensureActive()
                    val bmp = pdfBoxRenderer.renderImage(i, 0.5f)
                    list.add(PdfPageItem(pageIndex = i, bitmap = bmp))
                }
            } catch (_: Exception) {
            } finally {
                try {
                    doc?.close()
                } catch (_: Exception) {
                }
            }
        }
        list
    }

    private fun openUriInputStream(context: Context, uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun readExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            openUriInputStream(context, uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    private fun applyRotationIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Throwable) {
            bitmap
        }
    }

    /**
     * Validates that a generated PDF file exists, is non-empty, can be parsed by PDFBox,
     * and contains the expected positive number of pages.
     */
    fun validateGeneratedPdfFile(context: Context, file: File, expectedMinPages: Int = 1): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L || !hasPdfHeader(file)) return false
        var doc: PDDocument? = null
        return try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(file)
            val count = doc.numberOfPages
            if (count < expectedMinPages) return false
            for (i in 0 until count) {
                doc.getPage(i)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Validates that an unprotected PDF file exists, is not encrypted, opens without any password,
     * and preserves the exact expected page count.
     */
    fun validateUnprotectedPdfFile(context: Context, file: File, expectedPageCount: Int): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L || !hasPdfHeader(file)) return false
        if (checkPasswordProtectedSync(context, file)) return false
        var doc: PDDocument? = null
        return try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(file)
            if (doc.isEncrypted) return false
            if (doc.numberOfPages != expectedPageCount || doc.numberOfPages <= 0) return false
            for (i in 0 until doc.numberOfPages) {
                doc.getPage(i)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Checks whether the byte array begins with a recognized raster image signature (PNG, JPEG, WEBP, GIF, BMP).
     */
    fun hasSupportedImageHeaderBytes(header: ByteArray, length: Int): Boolean {
        if (length < 3) return false
        // PNG: 89 50 4E 47
        if (length >= 4 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) return true
        // JPEG: FF D8 FF
        if (header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) return true
        // GIF: 'G' 'I' 'F' '8'
        if (length >= 4 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte()
        ) return true
        // BMP: 'B' 'M'
        if (header[0] == 'B'.code.toByte() &&
            header[1] == 'M'.code.toByte()
        ) return true
        // WEBP: 'R' 'I' 'F' 'F' .... 'W' 'E' 'B' 'P'
        if (length >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) return true
        return false
    }

    fun hasSupportedImageHeader(context: Context, uri: Uri): Boolean {
        return try {
            openUriInputStream(context, uri)?.use { stream ->
                val header = ByteArray(12)
                val read = stream.read(header)
                hasSupportedImageHeaderBytes(header, read)
            } ?: false
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Validates that an image file exists, is non-empty, has a valid image signature,
     * and decodes with valid positive dimensions.
     */
    fun validateImageFile(file: File): Pair<Int, Int>? {
        if (!file.exists() || !file.isFile || file.length() == 0L) return null
        return try {
            val hasValidSignature = file.inputStream().use { stream ->
                val header = ByteArray(12)
                val read = stream.read(header)
                hasSupportedImageHeaderBytes(header, read)
            }
            if (!hasValidSignature) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                Pair(opts.outWidth, opts.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Convert List of Images to PDF using PDFBox for universal standards-compliant PDF output
    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFileName: String = "Images_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext null
        var tempFile: File? = null
        var outputFile: File? = null
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)
            tempFile = File(context.cacheDir, "temp_img2pdf_${System.nanoTime()}.pdf")
            doc = PDDocument()

            var addedPages = 0
            for (uri in imageUris) {
                coroutineContext.ensureActive()
                if (!hasSupportedImageHeader(context, uri)) {
                    tempFile.delete()
                    return@withContext null
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openUriInputStream(context, uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    tempFile.delete()
                    return@withContext null
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

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val rawBitmap = openUriInputStream(context, uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                } ?: run {
                    tempFile.delete()
                    return@withContext null
                }

                val rotation = readExifRotationDegrees(context, uri)
                val orientedBitmap = applyRotationIfNeeded(rawBitmap, rotation)

                val flattenedBitmap = if (orientedBitmap.hasAlpha()) {
                    val flat = Bitmap.createBitmap(
                        orientedBitmap.width.coerceAtLeast(1),
                        orientedBitmap.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(flat)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(orientedBitmap, 0f, 0f, null)
                    orientedBitmap.recycle()
                    flat
                } else {
                    orientedBitmap
                }

                try {
                    val page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    doc.addPage(page)

                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val margin = 20f
                    val maxWidth = pageWidth - (margin * 2f)
                    val maxHeight = pageHeight - (margin * 2f)

                    val srcWidth = flattenedBitmap.width.coerceAtLeast(1).toFloat()
                    val srcHeight = flattenedBitmap.height.coerceAtLeast(1).toFloat()
                    val scale = minOf(maxWidth / srcWidth, maxHeight / srcHeight)

                    val destWidth = (srcWidth * scale).coerceAtLeast(1f)
                    val destHeight = (srcHeight * scale).coerceAtLeast(1f)

                    val left = margin + (maxWidth - destWidth) / 2f
                    val bottom = margin + (maxHeight - destHeight) / 2f

                    val pdImage = try {
                        val jpegBytes = ByteArrayOutputStream().use { baos ->
                            flattenedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                            baos.toByteArray()
                        }
                        if (jpegBytes.isNotEmpty()) {
                            JPEGFactory.createFromByteArray(doc, jpegBytes)
                        } else {
                            com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, flattenedBitmap)
                        }
                    } catch (e: Throwable) {
                        com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, flattenedBitmap)
                    }
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { contentStream ->
                        contentStream.drawImage(pdImage, left, bottom, destWidth, destHeight)
                    }
                    addedPages++
                } finally {
                    flattenedBitmap.recycle()
                }
            }

            if (addedPages == 0) {
                tempFile.delete()
                return@withContext null
            }

            doc.save(tempFile)
            doc.close()
            doc = null

            if (!validateGeneratedPdfFile(context, tempFile, expectedMinPages = addedPages)) {
                tempFile.delete()
                return@withContext null
            }

            copyOrMoveFile(tempFile, outputFile)
            if (validateGeneratedPdfFile(context, outputFile, expectedMinPages = addedPages)) {
                outputFile
            } else {
                outputFile.delete()
                null
            }
        } catch (e: CancellationException) {
            tempFile?.delete()
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            tempFile?.delete()
            outputFile?.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempFile?.delete()
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
        var tempFile: File? = null
        var outputFile: File? = null
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument()
            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            val fontSize = fontSizeSp
            val leading = 1.5f * fontSize

            val margin = marginDp.toFloat()
            val width = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width
            val height = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.height
            val usableWidth = width - 2 * margin

            val lines = mutableListOf<String>()
            for (rawPara in textContent.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
                val para = rawPara.replace("\t", "    ")
                if (para.isBlank()) {
                    lines.add("")
                    continue
                }
                var currentLine = ""
                for (word in para.split(" ")) {
                    val safeWord = word.filter { it.code in 32..126 }
                    val testLine = if (currentLine.isEmpty()) safeWord else "$currentLine $safeWord"
                    val textWidth = try {
                        font.getStringWidth(testLine) / 1000f * fontSize
                    } catch (e: Exception) {
                        testLine.length * fontSize * 0.6f
                    }
                    if (textWidth <= usableWidth) {
                        currentLine = testLine
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine)
                        }
                        currentLine = safeWord
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
                coroutineContext.ensureActive()
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

                val safeLine = line.filter { it.code in 32..126 }
                try {
                    contentStream.showText(safeLine)
                } catch (_: Exception) {
                }
                contentStream.newLineAtOffset(0f, -leading)
                currentHeight += leading
            }

            contentStream.endText()
            contentStream.close()

            tempFile = File(context.cacheDir, "temp_txt2pdf_${System.nanoTime()}.pdf")
            doc.save(tempFile)
            doc.close()
            doc = null

            if (!validateGeneratedPdfFile(context, tempFile, expectedMinPages = 1)) {
                tempFile.delete()
                return@withContext null
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)
            copyOrMoveFile(tempFile, outputFile)

            if (validateGeneratedPdfFile(context, outputFile, expectedMinPages = 1)) {
                outputFile
            } else {
                outputFile.delete()
                null
            }
        } catch (e: CancellationException) {
            tempFile?.delete()
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            tempFile?.delete()
            outputFile?.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempFile?.delete()
        }
    }

    /**
     * Validates and reads a plain-text file from a Uri, rejecting binary files (like PDFs, PNGs, ZIPs),
     * and converts its contents into a validated multi-page PDF.
     */
    suspend fun convertTextFileToPdf(
        context: Context,
        textUri: Uri,
        outputFileName: String = "Text_Doc_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        val textContent = readValidatedPlainTextFromUri(context, textUri) ?: return@withContext null
        createTextPdf(context, textContent, outputFileName = outputFileName)
    }

    fun readValidatedPlainTextFromUri(context: Context, uri: Uri): String? {
        return try {
            val bytes = openUriInputStream(context, uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                val maxBytes = 5 * 1024 * 1024 // 5 MB safety limit
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return null
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return null

            if (bytes.isEmpty()) return null

            // Check first 4KB for binary signatures or null bytes
            val sampleLen = minOf(bytes.size, 4096)
            for (i in 0 until sampleLen) {
                if (bytes[i] == 0.toByte()) return null
            }
            val headerAscii = String(bytes, 0, minOf(bytes.size, 16), Charsets.ISO_8859_1)
            if (headerAscii.startsWith("%PDF-") ||
                headerAscii.startsWith("\u0089PNG") ||
                headerAscii.startsWith("PK\u0003\u0004")
            ) {
                return null
            }

            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.isBlank()) null else decoded
        } catch (e: Exception) {
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
        val mergedDoc = PDDocument()
        val loadedDocs = mutableListOf<PDDocument>()
        try {
            PDFBoxResourceLoader.init(context)

            for (file in pdfFiles) {
                coroutineContext.ensureActive()
                if (!file.exists() || file.length() == 0L) continue
                val password = passwords[file]
                val doc = try {
                    if (!password.isNullOrBlank()) {
                        PDDocument.load(file, password)
                    } else {
                        PDDocument.load(file)
                    }
                } catch (e: Exception) {
                    return@withContext null
                }
                loadedDocs.add(doc)
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    mergedDoc.importPage(page)
                }
            }

            if (mergedDoc.numberOfPages == 0) {
                return@withContext null
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            mergedDoc.save(outputFile)

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            try {
                mergedDoc.close()
            } catch (_: Exception) {
            }
            loadedDocs.forEach {
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Prevents overwriting files of any extension by appending a counter e.g. name (1).ext
     */
    fun getUniqueFile(dir: File, desiredName: String, defaultExtension: String): File {
        val extWithDot = if (defaultExtension.startsWith(".")) {
            defaultExtension.lowercase(Locale.US)
        } else {
            ".${defaultExtension.lowercase(Locale.US)}"
        }
        val cleanBase = desiredName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { name ->
                if (name.endsWith(extWithDot, ignoreCase = true)) {
                    name.substring(0, name.length - extWithDot.length)
                } else {
                    name
                }
            }
            .ifBlank { "output_${System.currentTimeMillis()}" }

        var candidate = File(dir, "$cleanBase$extWithDot")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$cleanBase ($counter)$extWithDot")
            counter++
        }
        return candidate
    }

    /**
     * Prevents overwriting PDF files by appending a counter e.g. document_extracted (1).pdf
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

    private fun checkPasswordProtectedSync(context: Context, file: File): Boolean {
        return try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(file).use { doc ->
                doc.isEncrypted
            }
        } catch (e: InvalidPasswordException) {
            true
        } catch (e: Exception) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use {
                        false
                    }
                }
            } catch (se: SecurityException) {
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Checks if a PDF document is password-protected/encrypted
     */
    suspend fun checkPasswordProtected(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        checkPasswordProtectedSync(context, file)
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

        var sourceDoc: PDDocument? = null
        var newDoc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            sourceDoc = try {
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
                return@withContext ExtractResult.Error("Selected page numbers exceed total document pages ($totalPages)")
            }

            newDoc = PDDocument()
            for (idx in validIndices) {
                coroutineContext.ensureActive()
                val page = sourceDoc.getPage(idx)
                newDoc.importPage(page)
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            val outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)

            if (outputFile.exists() && outputFile.length() > 0) {
                ExtractResult.Success(
                    file = outputFile,
                    pageCount = validIndices.size,
                    fileSizeBytes = outputFile.length()
                )
            } else {
                ExtractResult.Error("Failed to generate extracted PDF file")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidPasswordException) {
            ExtractResult.Error("This PDF is password protected", isPasswordProtected = true)
        } catch (e: Exception) {
            ExtractResult.Error("Extraction failed: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            try {
                newDoc?.close()
            } catch (_: Exception) {
            }
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
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
        var sourceDoc: PDDocument? = null
        var newDoc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            sourceDoc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            val totalPages = sourceDoc.numberOfPages
            val validIndices = pageIndicesToKeep.filter { it in 0 until totalPages }
            if (validIndices.isEmpty()) {
                return@withContext null
            }

            newDoc = PDDocument()
            for (idx in validIndices) {
                coroutineContext.ensureActive()
                val page = sourceDoc.getPage(idx)
                newDoc.importPage(page)
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            try {
                newDoc?.close()
            } catch (_: Exception) {
            }
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
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
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            for (page in doc.pages) {
                coroutineContext.ensureActive()
                val resources = page.resources ?: continue
                for (name in resources.xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val imageObj = resources.getXObject(name) as? PDImageXObject ?: continue
                        val bitmap = try { imageObj.image } catch (e: Exception) { null } ?: continue
                        try {
                            if (!bitmap.hasAlpha()) {
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, level.qualityPercentage, stream)
                                val compressedBytes = stream.toByteArray()
                                val newImage = JPEGFactory.createFromByteArray(doc, compressedBytes)
                                resources.put(name, newImage)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            doc.save(outputFile)
            doc.close()
            doc = null

            if (outputFile.exists() && outputFile.length() > 0) {
                if (outputFile.length() >= pdfFile.length()) {
                    outputFile.delete()
                    return@withContext pdfFile
                }
                outputFile
            } else {
                null
            }
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
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
        var sourceDoc: PDDocument? = null
        var newDoc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            sourceDoc = if (!password.isNullOrBlank()) {
                PDDocument.load(pdfFile, password)
            } else {
                PDDocument.load(pdfFile)
            }

            newDoc = PDDocument()
            for (item in pageItems) {
                coroutineContext.ensureActive()
                if (!item.isSelected) {
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
                return@withContext null
            }

            val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
            outputFile = getUniqueOutputFile(outputDir, outputFileName)

            newDoc.save(outputFile)

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            try {
                newDoc?.close()
            } catch (_: Exception) {
            }
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun copyOrMoveFile(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            FileOutputStream(target).use { fos ->
                FileInputStream(source).use { fis ->
                    fis.copyTo(fos)
                }
            }
            source.delete()
        }
    }

    private fun replaceFileSafelyWithBackup(validatedTempFile: File, targetFile: File): Boolean {
        val parentDir = targetFile.parentFile ?: return false
        if (!parentDir.exists() || !parentDir.canWrite() || !targetFile.canWrite()) return false

        val stagedFile = File(parentDir, ".staged_${targetFile.name}_${System.nanoTime()}.tmp")
        val backupFile = File(parentDir, ".backup_${targetFile.name}_${System.nanoTime()}.tmp")

        return try {
            // 1. Copy validated output to sibling staging file in target directory
            FileOutputStream(stagedFile).use { fos ->
                FileInputStream(validatedTempFile).use { fis ->
                    fis.copyTo(fos)
                }
                fos.fd.sync()
            }
            if (!stagedFile.exists() || stagedFile.length() != validatedTempFile.length()) {
                stagedFile.delete()
                return false
            }

            // 2. Create full backup of original file
            val originalLength = targetFile.length()
            FileOutputStream(backupFile).use { fos ->
                FileInputStream(targetFile).use { fis ->
                    fis.copyTo(fos)
                }
                fos.fd.sync()
            }
            if (!backupFile.exists() || backupFile.length() != originalLength) {
                stagedFile.delete()
                backupFile.delete()
                return false
            }

            // 3. Atomically replace original with staged file
            try {
                Files.move(
                    stagedFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                Files.move(
                    stagedFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            backupFile.delete()
            stagedFile.delete()
            true
        } catch (e: Exception) {
            // Restore original from backup if anything went wrong
            if (backupFile.exists() && backupFile.length() > 0) {
                try {
                    Files.move(
                        backupFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: Exception) {
                }
            }
            stagedFile.delete()
            backupFile.delete()
            false
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
        val tempEncrypted = File(context.cacheDir, "temp_encrypt_${System.nanoTime()}.pdf")
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(pdfFile)

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
            doc = null

            val isEncrypted = checkPasswordProtected(context, tempEncrypted)
            val isPasswordValid = verifyPdfPassword(context, tempEncrypted, userPassword)

            if (!isEncrypted || !isPasswordValid) {
                tempEncrypted.delete()
                return@withContext null
            }

            if (inPlace && pdfFile.canWrite()) {
                val replaced = replaceFileSafelyWithBackup(tempEncrypted, pdfFile)
                tempEncrypted.delete()
                return@withContext if (replaced) pdfFile else null
            } else {
                val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
                val outputFile = getUniqueOutputFile(outputDir, outputName)
                copyOrMoveFile(tempEncrypted, outputFile)
                return@withContext if (outputFile.exists() && outputFile.length() > 0) outputFile else null
            }
        } catch (e: CancellationException) {
            tempEncrypted.delete()
            throw e
        } catch (e: Exception) {
            tempEncrypted.delete()
            null
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempEncrypted.delete()
        }
    }

    /**
     * Detailed Remove Password implementation with full security-state inspection,
     * owner/user credential validation, temporary staging, post-decryption verification,
     * and safe non-destructive output saving.
     */
    suspend fun removePasswordDetailed(
        context: Context,
        pdfFile: File,
        currentPassword: String,
        outputName: String = "${pdfFile.nameWithoutExtension}_unprotected.pdf",
        inPlace: Boolean = false,
        tempDir: File = context.cacheDir,
        outputDir: File = File(context.filesDir, "output_pdfs"),
        outputValidator: ((Context, File, Int) -> Boolean)? = null
    ): UnprotectResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        if (!pdfFile.exists() || !pdfFile.isFile || pdfFile.length() == 0L) {
            return@withContext UnprotectResult.Error(
                UnprotectError.FILE_NOT_FOUND_OR_EMPTY,
                "Selected PDF file does not exist or is empty."
            )
        }

        // Step 1: Inspect the PDF security state before attempting removal
        val securityState = inspectPdfSecurity(context, pdfFile)
        when (securityState) {
            is PdfSecurityState.InvalidOrCorrupted -> {
                return@withContext UnprotectResult.Error(
                    UnprotectError.INVALID_OR_CORRUPTED_PDF,
                    securityState.message
                )
            }
            is PdfSecurityState.UnsupportedEncryption -> {
                return@withContext UnprotectResult.Error(
                    UnprotectError.UNSUPPORTED_ENCRYPTION,
                    securityState.message
                )
            }
            is PdfSecurityState.Unencrypted -> {
                return@withContext UnprotectResult.Error(
                    UnprotectError.NOT_ENCRYPTED,
                    "This PDF is not password-protected or encrypted."
                )
            }
            is PdfSecurityState.UserPasswordRequired -> {
                if (currentPassword.isEmpty()) {
                    return@withContext UnprotectResult.Error(
                        UnprotectError.PASSWORD_REQUIRED,
                        "Please enter the password required to open this PDF."
                    )
                }
            }
            is PdfSecurityState.OwnerPasswordRestricted -> {
                if (currentPassword.isEmpty()) {
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OWNER_PASSWORD_REQUIRED,
                        "This PDF has owner permission restrictions. Please enter the owner password to remove security."
                    )
                }
            }
            is PdfSecurityState.EncryptedWithBlankPassword -> {
                // Blank password is genuinely supported for this security configuration
            }
        }

        if (inPlace) {
            val parent = pdfFile.parentFile
            if (!pdfFile.canWrite() || parent == null || !parent.exists() || !parent.isDirectory || !parent.canWrite()) {
                return@withContext UnprotectResult.Error(
                    UnprotectError.REPLACE_NOT_PERMITTED,
                    "Cannot replace original file because its storage location is read-only. Save as New instead."
                )
            }
        }

        if ((tempDir.exists() && !tempDir.isDirectory) || (!tempDir.exists() && !tempDir.mkdirs()) || !tempDir.canWrite()) {
            return@withContext UnprotectResult.Error(
                UnprotectError.OUTPUT_WRITE_FAILED,
                "Unable to create temporary file in cache storage."
            )
        }

        val tempUnprotected = File(tempDir, "temp_unprotect_${System.nanoTime()}.pdf")
        var doc: PDDocument? = null

        try {
            PDFBoxResourceLoader.init(context)
            doc = try {
                PDDocument.load(pdfFile, currentPassword)
            } catch (e: InvalidPasswordException) {
                return@withContext UnprotectResult.Error(
                    UnprotectError.INCORRECT_PASSWORD,
                    "Incorrect password. Please verify the password and try again."
                )
            } catch (e: Throwable) {
                return@withContext if (isUnsupportedEncryptionException(e, pdfFile)) {
                    UnprotectResult.Error(
                        UnprotectError.UNSUPPORTED_ENCRYPTION,
                        "Unsupported PDF encryption configuration: ${e.localizedMessage ?: "Unsupported security handler"}"
                    )
                } else {
                    UnprotectResult.Error(
                        UnprotectError.INVALID_OR_CORRUPTED_PDF,
                        "Unable to open PDF document: ${e.localizedMessage ?: "Corrupted PDF"}"
                    )
                }
            }

            if (!doc.isEncrypted) {
                return@withContext UnprotectResult.Error(
                    UnprotectError.NOT_ENCRYPTED,
                    "This PDF is not password-protected."
                )
            }

            val ap = doc.currentAccessPermission
            if (securityState is PdfSecurityState.OwnerPasswordRestricted) {
                if (ap == null || !ap.isOwnerPermission) {
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OWNER_PASSWORD_REQUIRED,
                        "Owner password required: the supplied credential does not authorize removing permission restrictions."
                    )
                }
            }

            val expectedPageCount = doc.numberOfPages
            if (expectedPageCount <= 0) {
                return@withContext UnprotectResult.Error(
                    UnprotectError.INVALID_OR_CORRUPTED_PDF,
                    "PDF document contains no readable pages."
                )
            }

            coroutineContext.ensureActive()

            doc.isAllSecurityToBeRemoved = true
            try {
                doc.save(tempUnprotected)
            } catch (e: Throwable) {
                tempUnprotected.delete()
                return@withContext UnprotectResult.Error(
                    UnprotectError.OUTPUT_WRITE_FAILED,
                    "Failed to write decrypted PDF output: ${e.localizedMessage ?: "Storage error"}"
                )
            } finally {
                try {
                    doc.close()
                } catch (_: Exception) {
                }
                doc = null
            }

            coroutineContext.ensureActive()

            // Validate temporary output before touching any destination file
            val validator = outputValidator ?: ::validateUnprotectedPdfFile
            val isTempValid = validator(context, tempUnprotected, expectedPageCount)
            if (!isTempValid) {
                tempUnprotected.delete()
                return@withContext UnprotectResult.Error(
                    UnprotectError.OUTPUT_VALIDATION_FAILED,
                    "Generated PDF failed verification (file remained encrypted or unreadable)."
                )
            }

            coroutineContext.ensureActive()

            if (inPlace) {
                val backupBytes = pdfFile.readBytes()
                val replaced = replaceFileSafelyWithBackup(tempUnprotected, pdfFile)
                tempUnprotected.delete()
                if (!replaced) {
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OUTPUT_WRITE_FAILED,
                        "Failed to safely replace original PDF file. The original file has been preserved."
                    )
                }
                if (!validator(context, pdfFile, expectedPageCount)) {
                    // Restore original bytes if post-replace validation failed
                    try {
                        FileOutputStream(pdfFile).use { it.write(backupBytes) }
                    } catch (_: Exception) {
                    }
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OUTPUT_VALIDATION_FAILED,
                        "Post-save validation failed. Original file was restored."
                    )
                }
                return@withContext UnprotectResult.Success(
                    file = pdfFile,
                    pageCount = expectedPageCount,
                    fileSizeBytes = pdfFile.length(),
                    replacedOriginal = true
                )
            } else {
                if ((outputDir.exists() && !outputDir.isDirectory) || (!outputDir.exists() && !outputDir.mkdirs()) || !outputDir.canWrite()) {
                    tempUnprotected.delete()
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OUTPUT_WRITE_FAILED,
                        "Destination directory is unavailable or read-only."
                    )
                }

                val desiredName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_unprotected.pdf" }
                val finalOutputFile = getUniqueOutputFile(outputDir, desiredName)

                try {
                    copyOrMoveFile(tempUnprotected, finalOutputFile)
                } catch (e: Throwable) {
                    finalOutputFile.delete()
                    tempUnprotected.delete()
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OUTPUT_WRITE_FAILED,
                        "Failed to save unprotected PDF to destination: ${e.localizedMessage ?: "I/O error"}"
                    )
                }

                if (!validator(context, finalOutputFile, expectedPageCount)) {
                    finalOutputFile.delete()
                    return@withContext UnprotectResult.Error(
                        UnprotectError.OUTPUT_VALIDATION_FAILED,
                        "Saved PDF failed final verification."
                    )
                }

                return@withContext UnprotectResult.Success(
                    file = finalOutputFile,
                    pageCount = expectedPageCount,
                    fileSizeBytes = finalOutputFile.length(),
                    replacedOriginal = false
                )
            }
        } catch (e: CancellationException) {
            tempUnprotected.delete()
            throw e
        } catch (e: Throwable) {
            tempUnprotected.delete()
            UnprotectResult.Error(
                UnprotectError.OUTPUT_WRITE_FAILED,
                "Unexpected error while removing password: ${e.localizedMessage ?: "Unknown error"}"
            )
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempUnprotected.delete()
        }
    }

    /**
     * Remove Password Protection from PDF using PDFBox with Strict Output Validation.
     * Defaults to Save as New (inPlace = false).
     */
    suspend fun removePassword(
        context: Context,
        pdfFile: File,
        currentPassword: String,
        outputName: String = "Unprotected_${System.currentTimeMillis()}.pdf",
        inPlace: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        when (
            val result = removePasswordDetailed(
                context = context,
                pdfFile = pdfFile,
                currentPassword = currentPassword,
                outputName = outputName,
                inPlace = inPlace
            )
        ) {
            is UnprotectResult.Success -> result.file
            is UnprotectResult.Error -> null
        }
    }

    /**
     * Detailed PDF-to-Images conversion with format support (PNG/JPEG), unique non-colliding filenames,
     * image decoding validation, cancellation support, and atomic cleanup on partial failure.
     */
    suspend fun convertPdfToImagesDetailed(
        context: Context,
        pdfFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        password: String? = null,
        outputPrefix: String = pdfFile.nameWithoutExtension,
        outputDir: File = File(context.filesDir, "output_images")
    ): PdfToImagesResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        if (!pdfFile.exists() || !pdfFile.isFile || pdfFile.length() == 0L || !hasPdfHeader(pdfFile)) {
            return@withContext PdfToImagesResult.Error("Selected file is not a valid PDF document.")
        }

        if ((outputDir.exists() && !outputDir.isDirectory) || (!outputDir.exists() && !outputDir.mkdirs()) || !outputDir.canWrite()) {
            return@withContext PdfToImagesResult.Error("Output image directory is not writable.")
        }

        val generatedImages = mutableListOf<File>()
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = try {
                if (!password.isNullOrEmpty()) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
            } catch (e: InvalidPasswordException) {
                return@withContext PdfToImagesResult.Error(
                    message = if (password.isNullOrEmpty()) {
                        "This PDF is password-protected. Please enter the password to convert its pages."
                    } else {
                        "Incorrect PDF password. Please check the password and try again."
                    },
                    isPasswordProtected = true
                )
            }

            val pageCount = doc.numberOfPages
            if (pageCount <= 0) {
                return@withContext PdfToImagesResult.Error("PDF document has no pages to convert.")
            }

            val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
            val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val sanitizedPrefix = outputPrefix.trim()
                .removeSuffix(".pdf")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "page" }

            for (i in 0 until pageCount) {
                coroutineContext.ensureActive()

                val renderedBitmap = try {
                    pdfBoxRenderer.renderImage(i, 1.5f)
                } catch (e: Exception) {
                    null
                } ?: run {
                    generatedImages.forEach { it.delete() }
                    return@withContext PdfToImagesResult.Error("Failed to render page ${i + 1} of $pageCount.")
                }

                val bitmapToSave = if (format == Bitmap.CompressFormat.JPEG && renderedBitmap.hasAlpha()) {
                    val flattened = Bitmap.createBitmap(
                        renderedBitmap.width.coerceAtLeast(1),
                        renderedBitmap.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(flattened)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(renderedBitmap, 0f, 0f, null)
                    renderedBitmap.recycle()
                    flattened
                } else {
                    renderedBitmap
                }

                val imgFile = getUniqueFile(outputDir, "${sanitizedPrefix}_page_${i + 1}", ext)
                try {
                    FileOutputStream(imgFile).use { fos ->
                        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 92
                        val ok = bitmapToSave.compress(format, quality, fos)
                        if (!ok) {
                            throw IllegalStateException("Bitmap compression returned false")
                        }
                    }
                } finally {
                    bitmapToSave.recycle()
                }

                if (validateImageFile(imgFile) == null) {
                    imgFile.delete()
                    generatedImages.forEach { it.delete() }
                    return@withContext PdfToImagesResult.Error("Generated image for page ${i + 1} failed validation.")
                }

                generatedImages.add(imgFile)
            }

            PdfToImagesResult.Success(
                files = generatedImages,
                format = format,
                pageCount = pageCount
            )
        } catch (e: CancellationException) {
            generatedImages.forEach { it.delete() }
            throw e
        } catch (e: Throwable) {
            generatedImages.forEach { it.delete() }
            PdfToImagesResult.Error("PDF to image conversion failed: ${e.localizedMessage ?: "Unexpected error"}")
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
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
        when (
            val result = convertPdfToImagesDetailed(
                context = context,
                pdfFile = pdfFile,
                format = format,
                password = password,
                outputPrefix = outputPrefix
            )
        ) {
            is PdfToImagesResult.Success -> result.files
            is PdfToImagesResult.Error -> emptyList()
        }
    }

    /**
     * Detailed PDF-to-Text extraction with validation and unique output file naming.
     */
    suspend fun extractTextFromPdfDetailed(
        context: Context,
        pdfFile: File,
        password: String? = null,
        outputName: String = "${pdfFile.nameWithoutExtension}_text.txt",
        outputDir: File = File(context.filesDir, "output_texts")
    ): TextExtractResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        if (!pdfFile.exists() || !pdfFile.isFile || pdfFile.length() == 0L || !hasPdfHeader(pdfFile)) {
            return@withContext TextExtractResult.Error("Selected file is not a valid PDF document.")
        }

        if ((outputDir.exists() && !outputDir.isDirectory) || (!outputDir.exists() && !outputDir.mkdirs()) || !outputDir.canWrite()) {
            return@withContext TextExtractResult.Error("Output directory for text files is not writable.")
        }

        var doc: PDDocument? = null
        var tempFile: File? = null
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = try {
                if (!password.isNullOrEmpty()) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
            } catch (e: InvalidPasswordException) {
                return@withContext TextExtractResult.Error(
                    message = if (password.isNullOrEmpty()) {
                        "This PDF is password-protected. Please enter the password to extract text."
                    } else {
                        "Incorrect PDF password. Please check the password and try again."
                    },
                    isPasswordProtected = true
                )
            }

            if (doc.numberOfPages <= 0) {
                return@withContext TextExtractResult.Error("PDF document contains no pages.")
            }

            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val textContent = stripper.getText(doc)
            doc.close()
            doc = null

            if (textContent.isNullOrBlank()) {
                return@withContext TextExtractResult.Error("No extractable text found in this PDF (it may contain scanned images only).")
            }

            coroutineContext.ensureActive()

            tempFile = File(context.cacheDir, "temp_extract_${System.nanoTime()}.txt")
            FileOutputStream(tempFile).use { fos ->
                fos.write(textContent.toByteArray(Charsets.UTF_8))
            }

            outputFile = getUniqueFile(outputDir, outputName, "txt")
            copyOrMoveFile(tempFile, outputFile)

            val savedText = if (outputFile.exists() && outputFile.length() > 0) {
                outputFile.readText(Charsets.UTF_8)
            } else {
                ""
            }

            if (savedText.isBlank()) {
                outputFile.delete()
                return@withContext TextExtractResult.Error("Failed to validate extracted text output file.")
            }

            TextExtractResult.Success(
                file = outputFile,
                extractedText = savedText,
                charCount = savedText.length
            )
        } catch (e: CancellationException) {
            tempFile?.delete()
            outputFile?.delete()
            throw e
        } catch (e: Throwable) {
            tempFile?.delete()
            outputFile?.delete()
            TextExtractResult.Error("Failed to extract text from PDF: ${e.localizedMessage ?: "Unexpected error"}")
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempFile?.delete()
        }
    }

    // Extract Plain Text from PDF using PDFBox
    suspend fun extractTextFromPdf(
        context: Context,
        pdfFile: File,
        password: String? = null,
        outputName: String = "${pdfFile.nameWithoutExtension}_text.txt"
    ): File? = withContext(Dispatchers.IO) {
        when (
            val result = extractTextFromPdfDetailed(
                context = context,
                pdfFile = pdfFile,
                password = password,
                outputName = outputName
            )
        ) {
            is TextExtractResult.Success -> result.file
            is TextExtractResult.Error -> null
        }
    }

    /**
     * Detailed Image Format Converter (PNG <-> JPEG) with EXIF orientation support,
     * alpha-to-white compositing for JPEG, memory-safe decoding, unique output naming, and output validation.
     */
    suspend fun convertImageFormatDetailed(
        context: Context,
        imageUri: Uri,
        targetFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        outputName: String = "image_converted.${if (targetFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"}",
        outputDir: File = File(context.filesDir, "output_images")
    ): ImageConversionResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        if ((outputDir.exists() && !outputDir.isDirectory) || (!outputDir.exists() && !outputDir.mkdirs()) || !outputDir.canWrite()) {
            return@withContext ImageConversionResult.Error("Output directory for converted images is not writable.")
        }

        var tempFile: File? = null
        var outputFile: File? = null
        try {
            if (!hasSupportedImageHeader(context, imageUri)) {
                return@withContext ImageConversionResult.Error("Selected file is not a valid or supported image.")
            }
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openUriInputStream(context, imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            } ?: return@withContext ImageConversionResult.Error("Unable to open selected image file.")

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext ImageConversionResult.Error("Selected file is not a valid or supported image.")
            }

            val maxDimension = 4096
            var sampleSize = 1
            if (boundsOptions.outWidth > maxDimension || boundsOptions.outHeight > maxDimension) {
                val halfWidth = boundsOptions.outWidth / 2
                val halfHeight = boundsOptions.outHeight / 2
                while (halfWidth / sampleSize >= maxDimension && halfHeight / sampleSize >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = openUriInputStream(context, imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return@withContext ImageConversionResult.Error("Failed to decode image pixels.")

            val rotation = readExifRotationDegrees(context, imageUri)
            val orientedBitmap = applyRotationIfNeeded(decodedBitmap, rotation)

            val bitmapToCompress = if (targetFormat == Bitmap.CompressFormat.JPEG && orientedBitmap.hasAlpha()) {
                val flattened = Bitmap.createBitmap(
                    orientedBitmap.width.coerceAtLeast(1),
                    orientedBitmap.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(orientedBitmap, 0f, 0f, null)
                orientedBitmap.recycle()
                flattened
            } else {
                orientedBitmap
            }

            coroutineContext.ensureActive()

            val ext = if (targetFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"
            tempFile = File(context.cacheDir, "temp_imgconv_${System.nanoTime()}.$ext")

            try {
                FileOutputStream(tempFile).use { fos ->
                    val quality = if (targetFormat == Bitmap.CompressFormat.PNG) 100 else 92
                    if (!bitmapToCompress.compress(targetFormat, quality, fos)) {
                        throw IllegalStateException("Failed to encode target image format")
                    }
                }
            } finally {
                bitmapToCompress.recycle()
            }

            val dimensions = validateImageFile(tempFile) ?: run {
                tempFile.delete()
                return@withContext ImageConversionResult.Error("Converted image failed output validation.")
            }

            outputFile = getUniqueFile(outputDir, outputName, ext)
            copyOrMoveFile(tempFile, outputFile)

            val finalDims = validateImageFile(outputFile) ?: run {
                outputFile.delete()
                return@withContext ImageConversionResult.Error("Saved image failed final verification.")
            }

            ImageConversionResult.Success(
                file = outputFile,
                width = finalDims.first,
                height = finalDims.second,
                format = targetFormat
            )
        } catch (e: CancellationException) {
            tempFile?.delete()
            outputFile?.delete()
            throw e
        } catch (e: Throwable) {
            tempFile?.delete()
            outputFile?.delete()
            ImageConversionResult.Error("Image conversion failed: ${e.localizedMessage ?: "Unexpected error"}")
        } finally {
            tempFile?.delete()
        }
    }

    // Image Format Converter (PNG <-> JPG)
    suspend fun convertImageFormat(
        context: Context,
        imageUri: Uri,
        targetFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        outputName: String = "image_converted.${if (targetFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"}"
    ): File? = withContext(Dispatchers.IO) {
        when (
            val result = convertImageFormatDetailed(
                context = context,
                imageUri = imageUri,
                targetFormat = targetFormat,
                outputName = outputName
            )
        ) {
            is ImageConversionResult.Success -> result.file
            is ImageConversionResult.Error -> null
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
        var doc: PDDocument? = null
        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument.load(pdfFile, password)
            val pages = doc.numberOfPages
            pages > 0
        } catch (e: InvalidPasswordException) {
            false
        } catch (e: Exception) {
            false
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
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

        FileOutputStream(sampleFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
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
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.use { it.copyTo(outputStream) }
                }
                return tempFile
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }
}
