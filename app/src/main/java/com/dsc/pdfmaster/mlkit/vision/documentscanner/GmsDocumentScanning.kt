package com.dsc.pdfmaster.mlkit.vision.documentscanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/**
 * Options for the ML Kit Document Scanner API (requires no CAMERA permission in the app).
 */
class GmsDocumentScannerOptions private constructor(
    val isGalleryImportAllowed: Boolean,
    val pageLimit: Int,
    val resultFormats: IntArray,
    val scannerMode: Int
) {
    class Builder {
        private var galleryImportAllowed: Boolean = true
        private var pageLimit: Int = 20
        private var resultFormats: IntArray = intArrayOf(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        private var scannerMode: Int = SCANNER_MODE_FULL

        fun setGalleryImportAllowed(allowed: Boolean): Builder = apply {
            this.galleryImportAllowed = allowed
        }

        fun setPageLimit(limit: Int): Builder = apply {
            this.pageLimit = limit.coerceAtLeast(1)
        }

        fun setResultFormats(format: Int, vararg moreFormats: Int): Builder = apply {
            this.resultFormats = intArrayOf(format, *moreFormats)
        }

        fun setScannerMode(mode: Int): Builder = apply {
            this.scannerMode = mode
        }

        fun build(): GmsDocumentScannerOptions {
            return GmsDocumentScannerOptions(
                isGalleryImportAllowed = galleryImportAllowed,
                pageLimit = pageLimit,
                resultFormats = resultFormats,
                scannerMode = scannerMode
            )
        }
    }

    companion object {
        const val RESULT_FORMAT_JPEG = 101
        const val RESULT_FORMAT_PDF = 102

        const val SCANNER_MODE_FULL = 1
        const val SCANNER_MODE_BASE_WITH_FILTER = 2
        const val SCANNER_MODE_BASE = 3
    }
}

/**
 * Result returned from the ML Kit Document Scanner Activity.
 */
class GmsDocumentScanningResult(
    val pages: List<Page>?,
    val pdf: Pdf?
) {
    data class Page(val imageUri: Uri)
    data class Pdf(val uri: Uri, val pageCount: Int)

    companion object {
        const val EXTRA_SCAN_PAGE_URIS = "com.google.mlkit.vision.documentscanner.EXTRA_SCAN_PAGE_URIS"
        const val EXTRA_SCAN_PDF_URI = "com.google.mlkit.vision.documentscanner.EXTRA_SCAN_PDF_URI"
        const val EXTRA_SCAN_PDF_PAGE_COUNT = "com.google.mlkit.vision.documentscanner.EXTRA_SCAN_PDF_PAGE_COUNT"

        @JvmStatic
        fun fromActivityResultIntent(data: Intent?): GmsDocumentScanningResult? {
            if (data == null) return null
            @Suppress("DEPRECATION")
            val pageUris = data.getParcelableArrayListExtra<Uri>(EXTRA_SCAN_PAGE_URIS)
                ?: data.clipData?.let { clip ->
                    (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                }
                ?: data.data?.let { listOf(it) }

            @Suppress("DEPRECATION")
            val pdfUri = data.getParcelableExtra<Uri>(EXTRA_SCAN_PDF_URI)
            val pdfPageCount = data.getIntExtra(EXTRA_SCAN_PDF_PAGE_COUNT, pageUris?.size ?: 1)

            if (pageUris.isNullOrEmpty() && pdfUri == null) return null

            return GmsDocumentScanningResult(
                pages = pageUris?.map { Page(it) },
                pdf = pdfUri?.let { Pdf(it, pdfPageCount) }
            )
        }
    }
}

class ScannerTask<T>(
    private val supplier: () -> T
) {
    private var successListener: ((T) -> Unit)? = null
    private var failureListener: ((Exception) -> Unit)? = null

    fun addOnSuccessListener(listener: (T) -> Unit): ScannerTask<T> {
        successListener = listener
        try {
            val result = supplier()
            listener(result)
        } catch (e: Exception) {
            failureListener?.invoke(e)
        }
        return this
    }

    fun addOnFailureListener(listener: (Exception) -> Unit): ScannerTask<T> {
        failureListener = listener
        return this
    }
}

interface GmsDocumentScanner {
    val options: GmsDocumentScannerOptions
    fun getStartScanIntent(activity: Activity): ScannerTask<IntentSender>
}

object GmsDocumentScanning {
    private const val GMS_SCAN_ACTION = "com.google.android.gms.mlkit.ACTION_DOCUMENT_SCAN"

    @JvmStatic
    fun getClient(options: GmsDocumentScannerOptions): GmsDocumentScanner {
        return object : GmsDocumentScanner {
            override val options: GmsDocumentScannerOptions = options

            override fun getStartScanIntent(activity: Activity): ScannerTask<IntentSender> {
                return ScannerTask {
                    val intent = Intent(GMS_SCAN_ACTION).apply {
                        setPackage("com.google.android.gms")
                    }
                    val resolved = activity.packageManager.resolveActivity(intent, 0)
                    if (resolved == null) {
                        throw IllegalStateException("Google Play Services Document Scanner module is not installed on this device; using built-in zero-permission document scanner.")
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        activity,
                        9104,
                        intent,
                        android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    pendingIntent.intentSender
                }
            }
        }
    }
}
