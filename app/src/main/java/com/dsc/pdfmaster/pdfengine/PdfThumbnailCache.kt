package com.dsc.pdfmaster.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfThumbnailCache {

    private val maxCacheSizeKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 8).coerceIn(4 * 1024, 32 * 1024)
    }

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun buildKey(file: File, pageIndex: Int, targetWidth: Int): String {
        val modTime = if (file.exists()) file.lastModified() else 0L
        val size = if (file.exists()) file.length() else 0L
        return "${file.absolutePath}:$modTime:$size:$pageIndex:$targetWidth"
    }

    fun buildKey(filePath: String, pageIndex: Int, targetWidth: Int): String {
        val file = File(filePath)
        return buildKey(file, pageIndex, targetWidth)
    }

    fun getFromMemory(filePath: String, pageIndex: Int, targetWidth: Int): Bitmap? {
        val key = buildKey(filePath, pageIndex, targetWidth)
        val cached = memoryCache.get(key)
        return if (cached != null && !cached.isRecycled) {
            cached
        } else {
            if (cached != null) memoryCache.remove(key)
            null
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 240
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = buildKey(pdfFile, pageIndex, targetWidth)
        getFromMemory(pdfFile.absolutePath, pageIndex, targetWidth)?.let { return@withContext it }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex in 0 until renderer.pageCount) {
                page = renderer.openPage(pageIndex)
                val originalWidth = page.width
                val originalHeight = page.height
                val scale = targetWidth.toFloat() / originalWidth.coerceAtLeast(1)
                val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(60)

                if (!PdfEngine.isSafeBitmapAllocation(targetWidth, targetHeight)) return@withContext null

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                memoryCache.put(key, bitmap)
                bitmap
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { page?.close() } catch (_: Throwable) {}
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
