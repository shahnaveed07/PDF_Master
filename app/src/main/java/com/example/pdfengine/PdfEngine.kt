package com.example.pdfengine

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
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.model.PdfCompressionLevel
import com.example.data.model.PdfPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfEngine {

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

    suspend fun getPdfMetadata(context: Context, uri: Uri): PdfMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = getFileFromUri(context, uri) ?: return@withContext null
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            renderer.close()
            pfd.close()

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
        targetWidth: Int = 800
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // Draw crisp white background

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadPdfPageThumbnails(
        context: Context,
        pdfFile: File
    ): List<PdfPageItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PdfPageItem>()
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount

            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val targetWidth = 300
                val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                list.add(PdfPageItem(pageIndex = i, bitmap = bitmap))
            }
            renderer.close()
            pfd.close()
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
        try {
            val pdfDocument = PdfDocument()
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, outputFileName)

            for ((index, uri) in imageUris.withIndex()) {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(Color.WHITE)

                    // Draw image scaled to fit A4 page with margins
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
                    canvas.drawBitmap(originalBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))

                    pdfDocument.finishPage(page)
                    originalBitmap.recycle()
                }
            }

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Merge Multiple PDFs into One
    suspend fun mergePdfs(
        context: Context,
        pdfFiles: List<File>,
        outputFileName: String = "Merged_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            var globalPageCounter = 1
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, outputFileName)

            for (pdfFile in pdfFiles) {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height

                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, globalPageCounter++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)

                    // Render source page into bitmap then draw onto new page canvas
                    val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    canvas.drawBitmap(tempBitmap, 0f, 0f, null)

                    pdfDocument.finishPage(pdfPage)
                    tempBitmap.recycle()
                    page.close()
                }
                renderer.close()
                pfd.close()
            }

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Split PDF into specific page range or single pages
    suspend fun splitPdf(
        context: Context,
        pdfFile: File,
        pageIndicesToKeep: List<Int>,
        outputFileName: String = "Split_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pdfDocument = PdfDocument()
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, outputFileName)

            var newPageIndex = 1
            for (idx in pageIndicesToKeep) {
                if (idx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(idx)
                    val width = page.width
                    val height = page.height

                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageIndex++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)

                    val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    canvas.drawBitmap(tempBitmap, 0f, 0f, null)

                    pdfDocument.finishPage(pdfPage)
                    tempBitmap.recycle()
                    page.close()
                }
            }

            renderer.close()
            pfd.close()

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Compress PDF File Size
    suspend fun compressPdf(
        context: Context,
        pdfFile: File,
        level: PdfCompressionLevel,
        outputFileName: String = "Compressed_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pdfDocument = PdfDocument()
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, outputFileName)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val originalWidth = page.width
                val originalHeight = page.height

                // Scaled dimensions
                val scaledWidth = (originalWidth * level.scaleFactor).toInt().coerceAtLeast(100)
                val scaledHeight = (originalHeight * level.scaleFactor).toInt().coerceAtLeast(100)

                val pageInfo = PdfDocument.PageInfo.Builder(originalWidth, originalHeight, i + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                // Render page to bitmap
                val renderBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                page.render(renderBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Compress bitmap to JPEG byte stream
                val stream = java.io.ByteArrayOutputStream()
                renderBitmap.compress(Bitmap.CompressFormat.JPEG, level.qualityPercentage, stream)
                val compressedBytes = stream.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                // Draw compressed bitmap back to full-page canvas
                val destRect = Rect(0, 0, originalWidth, originalHeight)
                canvas.drawBitmap(compressedBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                pdfDocument.finishPage(pdfPage)

                renderBitmap.recycle()
                compressedBitmap.recycle()
                page.close()
            }

            renderer.close()
            pfd.close()

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Organize Pages: Rotate, Delete, Reorder
    suspend fun organizePages(
        context: Context,
        pdfFile: File,
        pageItems: List<PdfPageItem>,
        outputFileName: String = "Organized_${System.currentTimeMillis()}.pdf"
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pdfDocument = PdfDocument()
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, outputFileName)

            var newPageIndex = 1
            for (item in pageItems) {
                if (!item.isSelected) { // Not deleted
                    val page = renderer.openPage(item.pageIndex)
                    var width = page.width
                    var height = page.height

                    // Swap dimensions if rotated 90 or 270 degrees
                    if (item.rotationDegrees == 90 || item.rotationDegrees == 270) {
                        val temp = width
                        width = height
                        height = temp
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageIndex++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)

                    val renderWidth = page.width
                    val renderHeight = page.height
                    val tempBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (item.rotationDegrees != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(item.rotationDegrees.toFloat())
                        val rotatedBitmap = Bitmap.createBitmap(
                            tempBitmap, 0, 0, renderWidth, renderHeight, matrix, true
                        )
                        canvas.drawBitmap(rotatedBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                        rotatedBitmap.recycle()
                    } else {
                        canvas.drawBitmap(tempBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                    }

                    pdfDocument.finishPage(pdfPage)
                    tempBitmap.recycle()
                    page.close()
                }
            }

            renderer.close()
            pfd.close()

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun protectPdf(
        context: Context,
        pdfFile: File,
        password: String,
        outputName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
            val sanitizedName = if (outputName.endsWith(".pdf", ignoreCase = true)) outputName else "$outputName.pdf"
            val outputFile = File(outputDir, sanitizedName)
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount

            val pdfDocument = PdfDocument()

            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val width = page.width
                val height = page.height
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas = newPage.canvas

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val bmpCanvas = Canvas(bitmap)
                bmpCanvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(newPage)
            }

            renderer.close()
            pfd.close()

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Generate starter sample PDF on app first launch
    suspend fun createSampleWelcomePdf(context: Context): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesContextDir(), "output_pdfs").apply { mkdirs() }
        val sampleFile = File(outputDir, "PDF_Master_Starter_Guide.pdf")

        if (sampleFile.exists()) return@withContext sampleFile

        val pdfDocument = PdfDocument()

        // Page 1: Welcome Banner & Features Overview
        val page1Info = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val canvas1 = page1.canvas

        canvas1.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.parseColor("#C62828") // PdfRedPrimary
            style = Paint.Style.FILL
        }
        canvas1.drawRect(0f, 0f, A4_WIDTH.toFloat(), 120f, headerPaint)

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
            "Welcome to PDF Master, your fast, reliable, offline-first PDF suite!",
            "",
            "Core Features Available in Version 1.0:",
            "  - Merge PDFs: Combine multiple documents into one single PDF.",
            "  - Split PDF: Extract specific page ranges quickly.",
            "  - Compress PDF: Reduce file sizes up to 80% for easy sharing.",
            "  - Images to PDF: Convert your photo gallery into neat PDF files.",
            "  - Organize Pages: Delete, rotate, or reorder pages visually.",
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

        // Page 2: Sample Page 2 for testing split & reorder
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
            "  - Engine: Android Native PdfDocument & PdfRenderer",
            "  - Security: Local Processing (100% Private, No Cloud Needed)",
            "",
            "Try merging this sample with another PDF or compressing it!"
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

    private fun Context.filesContextDir() = filesDir

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
