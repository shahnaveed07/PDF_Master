package com.dsc.pdfmaster.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.dsc.pdfmaster.data.database.RecentPdfDao
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.BatchProgressState
import com.dsc.pdfmaster.data.model.OcrSearchablePdfResult
import com.dsc.pdfmaster.data.model.PageNumberConfig
import com.dsc.pdfmaster.data.model.PdfAnnotationItem
import com.dsc.pdfmaster.data.model.PdfBookmarkEntry
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.data.model.PdfSearchMatch
import com.dsc.pdfmaster.data.model.SavedSignatureItem
import com.dsc.pdfmaster.data.model.ScannedPageDraft
import com.dsc.pdfmaster.data.model.SignaturePlacement
import com.dsc.pdfmaster.data.model.WatermarkConfig
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfAdvancedToolsEngine
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    private val context: Context,
    private val dao: RecentPdfDao
) {
    val allRecentPdfs: Flow<List<RecentPdfEntity>> = dao.getAllRecentPdfs()
    val favoritePdfs: Flow<List<RecentPdfEntity>> = dao.getFavoritePdfs()
    val pinnedOrFavoritePdfs: Flow<List<RecentPdfEntity>> = dao.getPinnedOrFavoritePdfs()

    private fun computeSha256(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Content-aware import of a PDF URI into managed storage.
     * - Validates that the imported content has a valid PDF header.
     * - If an existing imported file has the same display name AND identical SHA-256 content,
     *   it reuses that file.
     * - If two different PDFs share the same filename, it creates a distinct non-colliding file
     *   (e.g., `report (1).pdf`) instead of silently returning the previous unrelated document.
     */
    /**
     * Takes a persistable read URI permission for [uri] when granted by the system document picker.
     */
    fun takePersistableReadPermission(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether a [RecentPdfEntity] points to an existing, readable PDF file or valid persisted URI.
     */
    fun isPdfAvailable(pdf: RecentPdfEntity): Boolean {
        return isFileAvailable(pdf.filePath)
    }

    fun isFileAvailable(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        if (filePath.startsWith("content://")) {
            return try {
                val uri = Uri.parse(filePath)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize > 0
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
        val file = File(filePath)
        return file.exists() && file.isFile && file.canRead() && file.length() > 0L
    }

    suspend fun getOrCreateManagedFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme
        if (scheme == "file") {
            val file = uri.path?.let { File(it) } ?: return@withContext null
            return@withContext if (file.exists() && file.isFile && file.length() > 0 && PdfEngine.hasPdfHeader(file)) {
                file
            } else {
                null
            }
        }
        if (scheme == "content") {
            takePersistableReadPermission(uri)
            var tempImport: File? = null
            try {
                var fileName: String? = null
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = it.getString(index)
                        }
                    }
                }
                val sanitizedName = if (!fileName.isNullOrBlank()) {
                    val clean = fileName!!.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (clean.endsWith(".pdf", ignoreCase = true)) clean else "$clean.pdf"
                } else {
                    "Document_${uri.lastPathSegment?.replace(Regex("[^a-zA-Z0-9_]"), "") ?: "imported"}.pdf"
                }

                // Stream incoming content to a staging file and compute SHA-256 simultaneously
                tempImport = File(context.cacheDir, "temp_import_${System.nanoTime()}.pdf")
                val digest = MessageDigest.getInstance("SHA-256")
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                inputStream.use { input ->
                    FileOutputStream(tempImport).use { fos ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                            fos.write(buffer, 0, read)
                        }
                    }
                }

                if (!tempImport.exists() || tempImport.length() == 0L || !PdfEngine.hasPdfHeader(tempImport)) {
                    tempImport.delete()
                    return@withContext null
                }

                val incomingHash = digest.digest().joinToString("") { "%02x".format(it) }
                val incomingLength = tempImport.length()

                val importsDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
                val baseName = if (sanitizedName.endsWith(".pdf", ignoreCase = true)) {
                    sanitizedName.substring(0, sanitizedName.length - 4)
                } else {
                    sanitizedName
                }

                // Check if this exact document content already exists under baseName or baseName (k)
                var counter = 0
                while (true) {
                    val candidateName = if (counter == 0) "$baseName.pdf" else "$baseName ($counter).pdf"
                    val candidate = File(importsDir, candidateName)
                    if (!candidate.exists()) {
                        // Unused slot: move tempImport here
                        if (!tempImport.renameTo(candidate)) {
                            FileOutputStream(candidate).use { fos ->
                                FileInputStream(tempImport).use { fis ->
                                    fis.copyTo(fos)
                                }
                            }
                            tempImport.delete()
                        }
                        return@withContext if (candidate.exists() && candidate.length() > 0) candidate else null
                    } else if (candidate.length() == incomingLength && computeSha256(candidate) == incomingHash) {
                        // Identical content already imported: reuse existing file
                        tempImport.delete()
                        return@withContext candidate
                    }
                    counter++
                }
            } catch (e: Exception) {
                tempImport?.delete()
                return@withContext null
            }
        }
        return@withContext null
    }

    suspend fun registerPdfFile(
        file: File,
        isFavorite: Boolean = false,
        knownPageCount: Int? = null
    ) = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || file.length() == 0L) return@withContext
        if (!file.name.endsWith(".pdf", ignoreCase = true) || !PdfEngine.hasPdfHeader(file)) return@withContext
        val pageCount = if (knownPageCount != null && knownPageCount > 0) {
            knownPageCount
        } else {
            val meta = PdfEngine.getPdfMetadata(context, Uri.fromFile(file))
            meta?.pageCount ?: 1
        }
        val existing = dao.getPdfByPath(file.absolutePath)
        val entity = RecentPdfEntity(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSizeBytes = file.length(),
            pageCount = pageCount,
            lastAccessedTimestamp = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: isFavorite,
            thumbnailPath = existing?.thumbnailPath,
            isPinned = existing?.isPinned ?: false,
            lastReadPage = existing?.lastReadPage ?: PdfAdvancedToolsEngine.getLastReadPage(context, file.absolutePath)
        )
        dao.insertPdf(entity)
    }

    suspend fun updateFavorite(filePath: String, isFavorite: Boolean) {
        dao.updateFavorite(filePath, isFavorite)
    }

    suspend fun updatePinned(filePath: String, isPinned: Boolean) {
        dao.updatePinned(filePath, isPinned)
    }

    suspend fun updateLastReadPage(filePath: String, lastReadPage: Int) = withContext(Dispatchers.IO) {
        val safePage = lastReadPage.coerceAtLeast(0)
        PdfAdvancedToolsEngine.saveLastReadPage(context, filePath, safePage)
        dao.updateLastReadPage(filePath, safePage)
    }

    suspend fun getLastReadPage(filePath: String): Int = withContext(Dispatchers.IO) {
        dao.getLastReadPage(filePath) ?: PdfAdvancedToolsEngine.getLastReadPage(context, filePath)
    }

    fun sanitizeFileName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('\u0000') || trimmed.contains("..")) return null
        val cleaned = trimmed.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        if (cleaned.isEmpty() || cleaned.all { it == '.' || it == '_' || it == ' ' }) return null
        return if (cleaned.endsWith(".pdf", ignoreCase = true)) {
            val base = cleaned.substring(0, cleaned.length - 4).trimEnd('.', ' ', '_')
            if (base.isEmpty()) null else "$base.pdf"
        } else {
            val base = cleaned.trimEnd('.', ' ', '_')
            if (base.isEmpty()) null else "$base.pdf"
        }
    }

    suspend fun renamePdf(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.isFile) return@withContext null
            val sanitizedName = sanitizeFileName(newName) ?: return@withContext null
            val parent = file.parentFile ?: return@withContext null
            val newFile = File(parent, sanitizedName)

            if (newFile.canonicalPath == file.canonicalPath) {
                return@withContext file
            }
            if (newFile.parentFile?.canonicalPath != parent.canonicalPath) {
                return@withContext null
            }

            if (newFile.exists()) {
                return@withContext null
            }

            val oldPath = file.absolutePath
            val success = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    true
                } else {
                    file.renameTo(newFile)
                }
            } catch (_: Exception) {
                file.renameTo(newFile)
            }

            if (success && newFile.exists()) {
                val existingEntity = dao.getPdfByPath(oldPath)
                if (existingEntity != null) {
                    dao.insertPdf(
                        existingEntity.copy(
                            filePath = newFile.absolutePath,
                            fileName = newFile.name,
                            fileSizeBytes = newFile.length(),
                            lastAccessedTimestamp = System.currentTimeMillis()
                        )
                    )
                    dao.deletePdfByPath(oldPath)
                } else {
                    registerPdfFile(newFile)
                }
                newFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun reconcileMissingFiles(): Int = withContext(Dispatchers.IO) {
        var prunedCount = 0
        try {
            val entities = dao.getAllRecentPdfsSnapshot()
            for (entity in entities) {
                val file = File(entity.filePath)
                if (!file.exists()) {
                    dao.deletePdfByPath(entity.filePath)
                    prunedCount++
                }
            }
        } catch (_: Exception) {
        }
        prunedCount
    }

    suspend fun deletePdf(filePath: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.deletePdfByPath(filePath)
    }

    suspend fun removeFromRecent(filePath: String) = withContext(Dispatchers.IO) {
        dao.deletePdfByPath(filePath)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAllRecentPdfs()
    }

    suspend fun mergePdfs(pdfFiles: List<File>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.mergePdfs(context, pdfFiles, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun splitPdf(pdfFile: File, pageIndices: List<Int>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.splitPdf(context, pdfFile, pageIndices, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun extractPages(
        pdfFile: File,
        pageIndices: List<Int>,
        outputName: String,
        password: String? = null
    ): PdfEngine.ExtractResult = withContext(Dispatchers.IO) {
        val result = PdfEngine.extractPages(context, pdfFile, pageIndices, outputName, password)
        if (result is PdfEngine.ExtractResult.Success) {
            registerPdfFile(result.file, isFavorite = false)
        }
        result
    }

    suspend fun compressPdf(
        pdfFile: File,
        level: PdfCompressionLevel,
        outputName: String,
        mode: PdfCompressionMode = PdfCompressionMode.IMAGE_RECOMPRESSION
    ): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.compressPdf(
            context = context,
            pdfFile = pdfFile,
            level = level,
            outputFileName = outputName,
            mode = mode
        )
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun compressPdfDetailed(
        pdfFile: File,
        level: PdfCompressionLevel,
        outputName: String,
        mode: PdfCompressionMode = PdfCompressionMode.IMAGE_RECOMPRESSION
    ): PdfEngine.CompressResult = withContext(Dispatchers.IO) {
        val result = PdfEngine.compressPdfDetailed(
            context = context,
            pdfFile = pdfFile,
            level = level,
            outputFileName = outputName,
            mode = mode
        )
        if (result is PdfEngine.CompressResult.Success) {
            registerPdfFile(result.file)
        }
        result
    }

    suspend fun convertImagesToPdf(imageUris: List<Uri>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.convertImagesToPdf(context, imageUris, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun createTextPdf(textContent: String, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.createTextPdf(context, textContent, outputFileName = outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun convertTextFileToPdf(textUri: Uri, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.convertTextFileToPdf(context, textUri, outputFileName = outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun organizePages(pdfFile: File, pages: List<PdfPageItem>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.organizePages(context, pdfFile, pages, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    /**
     * Encrypts and password-protects [pdfFile] with [password].
     * Safe default: Save as New ([replaceOriginal] = false) using a unique filename.
     */
    suspend fun protectPdfDetailed(
        pdfFile: File,
        password: String,
        confirmPassword: String = password,
        ownerPassword: String? = null,
        confirmOwnerPassword: String? = ownerPassword,
        currentPassword: String? = null,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true,
        keyLengthBits: Int = 256,
        outputName: String,
        replaceOriginal: Boolean = false
    ): PdfEngine.ProtectResult = withContext(Dispatchers.IO) {
        val defaultName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_protected.pdf" }
        val result = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = pdfFile,
            userPassword = password,
            confirmPassword = confirmPassword,
            ownerPassword = ownerPassword,
            confirmOwnerPassword = confirmOwnerPassword,
            currentPassword = currentPassword,
            allowPrinting = allowPrinting,
            allowCopy = allowCopy,
            keyLengthBits = keyLengthBits,
            outputName = defaultName,
            inPlace = replaceOriginal
        )
        if (result is PdfEngine.ProtectResult.Success) {
            registerPdfFile(result.file, knownPageCount = result.pageCount)
        }
        result
    }

    suspend fun protectPdf(
        pdfFile: File,
        password: String,
        outputName: String,
        replaceOriginal: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        when (
            val result = protectPdfDetailed(
                pdfFile = pdfFile,
                password = password,
                confirmPassword = password,
                outputName = outputName,
                replaceOriginal = replaceOriginal
            )
        ) {
            is PdfEngine.ProtectResult.Success -> result.file
            is PdfEngine.ProtectResult.Error -> null
        }
    }

    /**
     * Removes password protection from [pdfFile].
     * Safe default: Save as New ([replaceOriginal] = false). Never silently overwrites [pdfFile]
     * merely because it resides inside app-managed storage.
     */
    suspend fun removePasswordDetailed(
        pdfFile: File,
        password: String,
        outputName: String,
        replaceOriginal: Boolean = false
    ): PdfEngine.UnprotectResult = withContext(Dispatchers.IO) {
        val defaultName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_unprotected.pdf" }
        val result = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = pdfFile,
            currentPassword = password,
            outputName = defaultName,
            inPlace = replaceOriginal
        )
        if (result is PdfEngine.UnprotectResult.Success) {
            registerPdfFile(result.file)
        }
        result
    }

    suspend fun removePassword(
        pdfFile: File,
        password: String,
        outputName: String,
        replaceOriginal: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        when (val result = removePasswordDetailed(pdfFile, password, outputName, replaceOriginal)) {
            is PdfEngine.UnprotectResult.Success -> result.file
            is PdfEngine.UnprotectResult.Error -> null
        }
    }

    suspend fun convertPdfToImagesDetailed(
        pdfFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        password: String? = null,
        outputPrefix: String = pdfFile.nameWithoutExtension
    ): PdfEngine.PdfToImagesResult = withContext(Dispatchers.IO) {
        PdfEngine.convertPdfToImagesDetailed(
            context = context,
            pdfFile = pdfFile,
            format = format,
            password = password,
            outputPrefix = outputPrefix
        )
    }

    suspend fun convertPdfToImages(
        pdfFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        password: String? = null,
        outputPrefix: String = pdfFile.nameWithoutExtension
    ): List<File> = withContext(Dispatchers.IO) {
        PdfEngine.convertPdfToImages(
            context = context,
            pdfFile = pdfFile,
            format = format,
            password = password,
            outputPrefix = outputPrefix
        )
    }

    suspend fun extractTextFromPdfDetailed(
        pdfFile: File,
        password: String? = null,
        outputName: String = "${pdfFile.nameWithoutExtension}_text.txt"
    ): PdfEngine.TextExtractResult = withContext(Dispatchers.IO) {
        PdfEngine.extractTextFromPdfDetailed(
            context = context,
            pdfFile = pdfFile,
            password = password,
            outputName = outputName
        )
    }

    suspend fun extractTextFromPdf(
        pdfFile: File,
        password: String? = null,
        outputName: String = "${pdfFile.nameWithoutExtension}_text.txt"
    ): File? = withContext(Dispatchers.IO) {
        PdfEngine.extractTextFromPdf(
            context = context,
            pdfFile = pdfFile,
            password = password,
            outputName = outputName
        )
    }

    suspend fun convertImageFormatDetailed(
        imageUri: Uri,
        targetFormat: Bitmap.CompressFormat,
        outputName: String
    ): PdfEngine.ImageConversionResult = withContext(Dispatchers.IO) {
        PdfEngine.convertImageFormatDetailed(
            context = context,
            imageUri = imageUri,
            targetFormat = targetFormat,
            outputName = outputName
        )
    }

    suspend fun convertImageFormat(
        imageUri: Uri,
        targetFormat: Bitmap.CompressFormat,
        outputName: String
    ): File? = withContext(Dispatchers.IO) {
        PdfEngine.convertImageFormat(
            context = context,
            imageUri = imageUri,
            targetFormat = targetFormat,
            outputName = outputName
        )
    }

    suspend fun inspectOfficeSource(
        uri: Uri,
        expectedType: OfficeToPdfConverter.OfficeDocumentType
    ): OfficeToPdfConverter.OfficeSourceInspection = withContext(Dispatchers.IO) {
        PdfEngine.inspectOfficeSource(context, uri, expectedType)
    }

    suspend fun convertOfficeToPdfDetailed(
        uri: Uri,
        expectedType: OfficeToPdfConverter.OfficeDocumentType,
        outputName: String
    ): OfficeToPdfConverter.OfficeConversionResult = withContext(Dispatchers.IO) {
        val result = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = uri,
            expectedType = expectedType,
            outputName = outputName
        )
        if (result is OfficeToPdfConverter.OfficeConversionResult.Success) {
            registerPdfFile(result.file, knownPageCount = result.pageCount)
        }
        result
    }

    suspend fun ensureStarterSamplePdf(): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val sampleFile = File(outputDir, "Welcome_PDF_Master.pdf")
        if (sampleFile.exists() && sampleFile.length() > 0 && PdfEngine.hasPdfHeader(sampleFile)) {
            sampleFile
        } else {
            val starterFile = PdfEngine.createSampleWelcomePdf(context)
            registerPdfFile(starterFile)
            starterFile
        }
    }

    // =========================================================================
    // Phase 3 Repository Operations
    // =========================================================================

    suspend fun saveReusableSignature(signatureBitmap: Bitmap, name: String): SavedSignatureItem? =
        PdfAdvancedToolsEngine.saveReusableSignature(context, signatureBitmap, name)

    suspend fun loadSavedSignatures(): List<SavedSignatureItem> =
        PdfAdvancedToolsEngine.loadSavedSignatures(context)

    suspend fun deleteSavedSignature(file: File): Boolean =
        PdfAdvancedToolsEngine.deleteSavedSignature(file)

    suspend fun flattenSignaturesIntoPdf(
        pdfFile: File,
        placements: List<SignaturePlacement>,
        outputFileName: String,
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val out = PdfAdvancedToolsEngine.flattenSignaturesIntoPdf(
            context = context,
            pdfFile = pdfFile,
            placements = placements,
            outputFileName = outputFileName,
            password = password
        )
        if (out != null) {
            registerPdfFile(out)
        }
        out
    }

    suspend fun applyAnnotationsToPdf(
        pdfFile: File,
        annotations: List<PdfAnnotationItem>,
        outputFileName: String,
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val out = PdfAdvancedToolsEngine.applyAnnotationsToPdf(
            context = context,
            pdfFile = pdfFile,
            annotations = annotations,
            outputFileName = outputFileName,
            password = password
        )
        if (out != null) {
            registerPdfFile(out)
        }
        out
    }

    suspend fun applyWatermarkAndPageNumbers(
        pdfFile: File,
        watermarkConfig: WatermarkConfig,
        pageNumberConfig: PageNumberConfig,
        outputFileName: String,
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val out = PdfAdvancedToolsEngine.applyWatermarkAndPageNumbers(
            context = context,
            pdfFile = pdfFile,
            watermarkConfig = watermarkConfig,
            pageNumberConfig = pageNumberConfig,
            outputFileName = outputFileName,
            password = password
        )
        if (out != null) {
            registerPdfFile(out)
        }
        out
    }

    suspend fun searchInPdf(
        pdfFile: File,
        query: String,
        password: String? = null
    ): List<PdfSearchMatch> =
        PdfAdvancedToolsEngine.searchInPdf(context, pdfFile, query, password)

    suspend fun extractPdfBookmarksAndOutline(
        pdfFile: File,
        password: String? = null
    ): List<PdfBookmarkEntry> =
        PdfAdvancedToolsEngine.extractPdfBookmarksAndOutline(context, pdfFile, password)

    suspend fun addBookmarkToPdf(
        pdfFile: File,
        title: String,
        pageIndex: Int,
        writeToPdfOutline: Boolean = false,
        password: String? = null
    ): List<PdfBookmarkEntry> =
        PdfAdvancedToolsEngine.addBookmarkToPdf(context, pdfFile, title, pageIndex, writeToPdfOutline, password)

    suspend fun removeUserBookmark(
        pdfFile: File,
        pageIndex: Int
    ): List<PdfBookmarkEntry> =
        PdfAdvancedToolsEngine.removeUserBookmark(context, pdfFile, pageIndex)

    suspend fun deleteSinglePageFromPdf(
        pdfFile: File,
        pageIndexToDelete: Int,
        outputFileName: String,
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val out = PdfAdvancedToolsEngine.deleteSinglePageFromPdf(
            context = context,
            pdfFile = pdfFile,
            pageIndexToDelete = pageIndexToDelete,
            outputFileName = outputFileName,
            password = password
        )
        if (out != null) {
            registerPdfFile(out)
        }
        out
    }

    suspend fun performOcrToSearchablePdf(
        sourcePdfFile: File? = null,
        sourceImageUris: List<Uri> = emptyList(),
        outputFileName: String,
        password: String? = null
    ): OcrSearchablePdfResult? = withContext(Dispatchers.IO) {
        val res = PdfAdvancedToolsEngine.performOcrToSearchablePdf(
            context = context,
            sourcePdfFile = sourcePdfFile,
            sourceImageUris = sourceImageUris,
            outputFileName = outputFileName,
            password = password
        )
        if (res != null) {
            registerPdfFile(res.outputFile, knownPageCount = res.pageCount)
        }
        res
    }

    suspend fun exportScannedPagesToPdf(
        pages: List<ScannedPageDraft>,
        outputFileName: String
    ): File? = withContext(Dispatchers.IO) {
        val out = PdfAdvancedToolsEngine.exportScannedPagesToPdf(context, pages, outputFileName)
        if (out != null) {
            registerPdfFile(out, knownPageCount = pages.size)
        }
        out
    }

    suspend fun batchCompressPdfs(
        files: List<File>,
        level: PdfCompressionLevel,
        mode: PdfCompressionMode = PdfCompressionMode.IMAGE_RECOMPRESSION,
        onProgress: (BatchProgressState) -> Unit = {}
    ): BatchProgressState = withContext(Dispatchers.IO) {
        val result = PdfAdvancedToolsEngine.batchCompressPdfs(context, files, level, mode, onProgress)
        result.items.mapNotNull { it.outputFile }.forEach { registerPdfFile(it) }
        result
    }

    suspend fun batchConvertPdfsToText(
        files: List<File>,
        onProgress: (BatchProgressState) -> Unit = {}
    ): BatchProgressState =
        PdfAdvancedToolsEngine.batchConvertPdfsToText(context, files, onProgress)
}
