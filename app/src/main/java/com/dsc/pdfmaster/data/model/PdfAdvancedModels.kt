package com.dsc.pdfmaster.data.model

import android.net.Uri
import java.io.File

/**
 * Parsed incoming intent payload for Open-with (ACTION_VIEW) and Share (ACTION_SEND / ACTION_SEND_MULTIPLE).
 */
sealed class IncomingIntentPayload {
    data class OpenPdf(val uri: Uri) : IncomingIntentPayload()
    data class ShareSinglePdf(val uri: Uri) : IncomingIntentPayload()
    data class ShareMultiplePdfs(val uris: List<Uri>) : IncomingIntentPayload()
    data class ShareImages(val uris: List<Uri>) : IncomingIntentPayload()
    data object None : IncomingIntentPayload()
}

// ============================================================================
// 2. Sign PDF Models
// ============================================================================

data class SavedSignatureItem(
    val id: String,
    val name: String,
    val file: File,
    val createdAtTimestamp: Long = file.lastModified()
)

data class SignatureStroke(
    val points: List<NormalizedPoint>,
    val strokeWidthPx: Float = 6f
)

data class SignaturePlacement(
    val pageIndex: Int,
    val signatureFile: File,
    val normalizedX: Float = 0.55f,
    val normalizedY: Float = 0.75f,
    val widthFraction: Float = 0.32f,
    val heightFraction: Float = 0.12f
)

// ============================================================================
// 3. Annotate PDF Models & Undo/Redo Stack
// ============================================================================

enum class AnnotationToolType(val label: String) {
    HIGHLIGHT("Highlight"),
    FREEHAND("Freehand Draw"),
    TEXT_BOX("Text Box")
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

sealed class PdfAnnotationItem {
    abstract val id: String
    abstract val pageIndex: Int

    data class Highlight(
        override val id: String,
        override val pageIndex: Int,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val opacity: Float = 0.35f,
        val grayShade: Int = 180
    ) : PdfAnnotationItem()

    data class Freehand(
        override val id: String,
        override val pageIndex: Int,
        val points: List<NormalizedPoint>,
        val strokeWidthPt: Float = 2.5f,
        val grayShade: Int = 20
    ) : PdfAnnotationItem()

    data class TextBox(
        override val id: String,
        override val pageIndex: Int,
        val text: String,
        val normalizedX: Float,
        val normalizedY: Float,
        val fontSizePt: Float = 14f,
        val grayShade: Int = 0
    ) : PdfAnnotationItem()
}

/**
 * Immutable Undo/Redo history manager for page annotations.
 */
data class AnnotationHistoryStack(
    val applied: List<PdfAnnotationItem> = emptyList(),
    val undone: List<PdfAnnotationItem> = emptyList()
) {
    val canUndo: Boolean get() = applied.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun add(item: PdfAnnotationItem): AnnotationHistoryStack {
        return AnnotationHistoryStack(
            applied = applied + item,
            undone = emptyList()
        )
    }

    fun undo(): AnnotationHistoryStack {
        if (applied.isEmpty()) return this
        val last = applied.last()
        return AnnotationHistoryStack(
            applied = applied.dropLast(1),
            undone = undone + last
        )
    }

    fun redo(): AnnotationHistoryStack {
        if (undone.isEmpty()) return this
        val restored = undone.last()
        return AnnotationHistoryStack(
            applied = applied + restored,
            undone = undone.dropLast(1)
        )
    }

    fun clearPage(pageIndex: Int): AnnotationHistoryStack {
        return AnnotationHistoryStack(
            applied = applied.filterNot { it.pageIndex == pageIndex },
            undone = emptyList()
        )
    }

    fun clearAll(): AnnotationHistoryStack = AnnotationHistoryStack()

    fun itemsForPage(pageIndex: Int): List<PdfAnnotationItem> {
        return applied.filter { it.pageIndex == pageIndex }
    }
}

// ============================================================================
// 4. Watermark and Page Numbers Models
// ============================================================================

enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    TILED("Tiled Grid")
}

data class WatermarkConfig(
    val enabled: Boolean = true,
    val text: String = "CONFIDENTIAL",
    val opacity: Float = 0.25f,
    val rotationDegrees: Float = -45f,
    val fontSizePt: Float = 36f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val grayShade: Int = 110
)

enum class PageNumberFormat(val label: String) {
    PAGE_X_OF_Y("Page 1 of N"),
    X_SLASH_Y("1 / N"),
    NUMBER_ONLY("1"),
    DASH_NUMBER_DASH("- 1 -");

    fun formatPage(pageNumber: Int, totalPages: Int): String = when (this) {
        PAGE_X_OF_Y -> "Page $pageNumber of $totalPages"
        X_SLASH_Y -> "$pageNumber / $totalPages"
        NUMBER_ONLY -> "$pageNumber"
        DASH_NUMBER_DASH -> "- $pageNumber -"
    }
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_LEFT("Bottom Left"),
    TOP_CENTER("Top Center"),
    TOP_RIGHT("Top Right"),
    TOP_LEFT("Top Left")
}

data class PageNumberConfig(
    val enabled: Boolean = true,
    val format: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val startNumber: Int = 1,
    val fontSizePt: Float = 11f
)

// ============================================================================
// 5. Viewer Upgrade Models (Search, Bookmarks/Outline, Night Mode)
// ============================================================================

data class PdfSearchMatch(
    val pageIndex: Int,
    val matchOrdinalInDocument: Int,
    val matchCountOnPage: Int,
    val snippet: String
)

data class PdfBookmarkEntry(
    val title: String,
    val pageIndex: Int,
    val isDocumentOutline: Boolean = false
)

// ============================================================================
// 6. On-Device OCR Models
// ============================================================================

data class OcrPageSummary(
    val pageIndex: Int,
    val recognizedText: String,
    val blockCount: Int,
    val lineCount: Int
)

data class OcrSearchablePdfResult(
    val outputFile: File,
    val pageCount: Int,
    val fullRecognizedText: String,
    val pageSummaries: List<OcrPageSummary>,
    val bundledModelCompressedMb: Double = 4.6,
    val bundledModelUncompressedMb: Double = 16.2
)

// ============================================================================
// 7. Document Scanner Models
// ============================================================================

enum class ScannerFilterMode(val label: String, val subtitle: String) {
    ORIGINAL("Original", "Unmodified capture"),
    AUTO_ENHANCE("Auto Enhance", "Sharpen contrast for readable text"),
    GRAYSCALE("Grayscale", "Clean monochrome document"),
    BLACK_AND_WHITE("High-Contrast B&W", "Crisp ink on pure white page")
}

data class ScannedPageDraft(
    val id: String,
    val uri: Uri,
    val filterMode: ScannerFilterMode = ScannerFilterMode.AUTO_ENHANCE,
    val rotationDegrees: Int = 0
)

// ============================================================================
// 8. Batch Operations Models
// ============================================================================

enum class BatchItemStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class BatchFileItemResult(
    val sourceName: String,
    val status: BatchItemStatus,
    val originalSizeBytes: Long = 0L,
    val outputSizeBytes: Long = 0L,
    val outputFile: File? = null,
    val message: String = ""
) {
    val sourceFile: File
        get() = File(sourceName)
}

data class BatchProgressState(
    val isBatchRunning: Boolean = false,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileName: String = "",
    val overallProgressPercent: Int = 0,
    val items: List<BatchFileItemResult> = emptyList(),
    val isFinished: Boolean = false
) {
    val progressPercent: Int
        get() = overallProgressPercent
}
