package com.dsc.pdfmaster.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.dsc.pdfmaster.data.database.RecentPdfDao
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class PdfRepository(
    private val context: Context,
    private val dao: RecentPdfDao
) {
    val allRecentPdfs: Flow<List<RecentPdfEntity>> = dao.getAllRecentPdfs()
    val favoritePdfs: Flow<List<RecentPdfEntity>> = dao.getFavoritePdfs()

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
            isFavorite = existing?.isFavorite ?: isFavorite
        )
        dao.insertPdf(entity)
    }

    suspend fun updateFavorite(filePath: String, isFavorite: Boolean) {
        dao.updateFavorite(filePath, isFavorite)
    }

    suspend fun renamePdf(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = if (newName.endsWith(".pdf", ignoreCase = true)) newName else "$newName.pdf"
            val newFile = File(file.parentFile, sanitizedName)
            if (file.renameTo(newFile)) {
                dao.deletePdfByPath(file.absolutePath)
                registerPdfFile(newFile)
                newFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
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

    suspend fun compressPdf(pdfFile: File, level: PdfCompressionLevel, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.compressPdf(context, pdfFile, level, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
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
        currentPassword: String? = null,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true,
        outputName: String,
        replaceOriginal: Boolean = false
    ): PdfEngine.ProtectResult = withContext(Dispatchers.IO) {
        val defaultName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_protected.pdf" }
        val result = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = pdfFile,
            userPassword = password,
            confirmPassword = confirmPassword,
            ownerPassword = ownerPassword ?: password,
            currentPassword = currentPassword,
            allowPrinting = allowPrinting,
            allowCopy = allowCopy,
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
}
