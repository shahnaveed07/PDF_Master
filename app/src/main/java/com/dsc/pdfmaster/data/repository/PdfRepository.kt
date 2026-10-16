package com.dsc.pdfmaster.data.repository

import android.content.Context
import android.net.Uri
import com.dsc.pdfmaster.data.database.RecentPdfDao
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(
    private val context: Context,
    private val dao: RecentPdfDao
) {
    val allRecentPdfs: Flow<List<RecentPdfEntity>> = dao.getAllRecentPdfs()
    val favoritePdfs: Flow<List<RecentPdfEntity>> = dao.getFavoritePdfs()

    suspend fun getOrCreateManagedFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme
        if (scheme == "file") {
            return@withContext uri.path?.let { File(it) }
        }
        if (scheme == "content") {
            try {
                var fileName: String? = null
                val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = it.getString(index)
                        }
                    }
                }
                val sanitizedName = if (!fileName.isNullOrBlank()) {
                    val name = fileName!!
                    if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
                } else {
                    "Document_${uri.lastPathSegment?.replace(Regex("[^a-zA-Z0-9_]"), "") ?: "imported"}.pdf"
                }

                val importsDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
                val targetFile = File(importsDir, sanitizedName)

                // Reuse if already imported and valid to avoid duplicate files
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@withContext targetFile
                }

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                java.io.FileOutputStream(targetFile).use { fos ->
                    inputStream.copyTo(fos)
                }
                inputStream.close()

                return@withContext if (targetFile.exists() && targetFile.length() > 0) targetFile else null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    suspend fun registerPdfFile(file: File, isFavorite: Boolean = false) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext
        val meta = PdfEngine.getPdfMetadata(context, Uri.fromFile(file))
        val pageCount = meta?.pageCount ?: 1
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
            e.printStackTrace()
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

    suspend fun organizePages(pdfFile: File, pages: List<PdfPageItem>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.organizePages(context, pdfFile, pages, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun protectPdf(pdfFile: File, password: String, outputName: String): File? = withContext(Dispatchers.IO) {
        val isManagedFile = pdfFile.absolutePath.startsWith(context.filesDir.absolutePath)
        val resultFile = PdfEngine.protectPdf(
            context = context,
            pdfFile = pdfFile,
            userPassword = password,
            outputName = outputName,
            inPlace = isManagedFile
        )
        if (resultFile != null) {
            registerPdfFile(resultFile)
        }
        resultFile
    }

    suspend fun removePassword(pdfFile: File, password: String, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.removePassword(context, pdfFile, password, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun ensureStarterSamplePdf(): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "output_pdfs").apply { mkdirs() }
        val sampleFile = File(outputDir, "Welcome_PDF_Master.pdf")
        if (sampleFile.exists() && sampleFile.length() > 0) {
            sampleFile
        } else {
            val starterFile = PdfEngine.createSampleWelcomePdf(context)
            registerPdfFile(starterFile)
            starterFile
        }
    }
}
