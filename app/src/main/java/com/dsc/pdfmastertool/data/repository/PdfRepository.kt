package com.dsc.pdfmastertool.data.repository

import android.content.Context
import android.net.Uri
import com.dsc.pdfmastertool.data.database.RecentPdfDao
import com.dsc.pdfmastertool.data.database.RecentPdfEntity
import com.dsc.pdfmastertool.data.model.PdfCompressionLevel
import com.dsc.pdfmastertool.data.model.PdfPageItem
import com.dsc.pdfmastertool.pdfengine.PdfEngine
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

    suspend fun registerPdfFile(file: File, isFavorite: Boolean = false) = withContext(Dispatchers.IO) {
        val meta = PdfEngine.getPdfMetadata(context, Uri.fromFile(file))
        val pageCount = meta?.pageCount ?: 1
        val entity = RecentPdfEntity(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSizeBytes = file.length(),
            pageCount = pageCount,
            lastAccessedTimestamp = System.currentTimeMillis(),
            isFavorite = isFavorite
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

    suspend fun organizePages(pdfFile: File, pages: List<PdfPageItem>, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.organizePages(context, pdfFile, pages, outputName)
        if (outputFile != null) {
            registerPdfFile(outputFile)
        }
        outputFile
    }

    suspend fun protectPdf(pdfFile: File, password: String, outputName: String): File? = withContext(Dispatchers.IO) {
        val outputFile = PdfEngine.protectPdf(context, pdfFile, password, outputName)
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
