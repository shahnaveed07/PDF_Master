package com.dsc.pdfmastertool.pdfengine

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

    // Cache up to 40 thumbnails in memory (around ~15-20MB max)
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(40) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return 1
        }

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC collect or recycle if evicted
        }
    }

    private fun buildKey(filePath: String, pageIndex: Int, targetWidth: Int): String {
        return "$filePath:$pageIndex:$targetWidth"
    }

    fun getFromMemory(filePath: String, pageIndex: Int, targetWidth: Int): Bitmap? {
        return memoryCache.get(buildKey(filePath, pageIndex, targetWidth))
    }

    suspend fun loadThumbnail(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 240
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = buildKey(pdfFile.absolutePath, pageIndex, targetWidth)
        memoryCache.get(key)?.let { return@withContext it }

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

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                memoryCache.put(key, bitmap)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
