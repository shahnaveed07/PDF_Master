package com.dsc.pdfmaster.pdfengine

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfPageBitmapCache @Inject constructor() {

    private val maxCacheSizeKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 8).coerceIn(4 * 1024, 64 * 1024)
    }

    private val lruCache = object : LruCache<String, Bitmap>(maxCacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun cacheKey(file: File, pageIndex: Int, targetWidth: Int): String {
        return "${file.absolutePath}:${file.lastModified()}:${file.length()}:$pageIndex:$targetWidth"
    }

    @Synchronized
    fun get(file: File, pageIndex: Int, targetWidth: Int): Bitmap? {
        val key = cacheKey(file, pageIndex, targetWidth)
        val cached = lruCache.get(key)
        return if (cached != null && !cached.isRecycled) {
            cached
        } else {
            if (cached != null) {
                lruCache.remove(key)
            }
            null
        }
    }

    @Synchronized
    fun put(file: File, pageIndex: Int, targetWidth: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = cacheKey(file, pageIndex, targetWidth)
        lruCache.put(key, bitmap)
    }

    @Synchronized
    fun invalidateFile(filePath: String) {
        val prefix = "$filePath:"
        val snapshot = lruCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith(prefix)) {
                lruCache.remove(key)
            }
        }
    }

    @Synchronized
    fun clear() {
        lruCache.evictAll()
    }

    @Synchronized
    fun sizeKb(): Int = lruCache.size()

    @Synchronized
    fun entryCount(): Int = lruCache.snapshot().size

    companion object {
        val sharedInstance = PdfPageBitmapCache()
    }
}
