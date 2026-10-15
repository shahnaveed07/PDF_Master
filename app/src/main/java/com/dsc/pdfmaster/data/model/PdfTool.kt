package com.dsc.pdfmaster.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector

enum class PdfToolType {
    MERGE,
    EXTRACT_PAGES,
    SPLIT,
    COMPRESS,
    IMAGES_TO_PDF,
    VIEW_PDF,
    MANAGE_PAGES,
    PASSWORD_PROTECT,
    SCANNER_OCR
}

data class PdfTool(
    val type: PdfToolType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isPopular: Boolean = false,
    val badge: String? = null
)

val pdfToolsList = listOf(
    PdfTool(
        type = PdfToolType.EXTRACT_PAGES,
        title = "Extract Pages",
        description = "Select pages or ranges to generate a clean new PDF document",
        icon = Icons.Default.ContentCut,
        isPopular = true,
        badge = "NEW"
    ),
    PdfTool(
        type = PdfToolType.MERGE,
        title = "Merge PDFs",
        description = "Combine multiple PDF files into one seamless document",
        icon = Icons.AutoMirrored.Filled.CallMerge,
        isPopular = true,
        badge = "HOT"
    ),
    PdfTool(
        type = PdfToolType.SPLIT,
        title = "Split PDF",
        description = "Extract pages or split PDF into custom ranges",
        icon = Icons.Default.CallSplit,
        isPopular = true
    ),
    PdfTool(
        type = PdfToolType.COMPRESS,
        title = "Compress PDF",
        description = "Reduce file size up to 80% without losing readable quality",
        icon = Icons.Default.Compress,
        isPopular = true,
        badge = "SAVER"
    ),
    PdfTool(
        type = PdfToolType.IMAGES_TO_PDF,
        title = "Images to PDF",
        description = "Convert photos, JPG & PNG images into clean PDF documents",
        icon = Icons.Default.Image,
        isPopular = true
    ),
    PdfTool(
        type = PdfToolType.MANAGE_PAGES,
        title = "Organize Pages",
        description = "Delete, reorder, or rotate PDF pages visually",
        icon = Icons.Default.SwapVert
    ),
    PdfTool(
        type = PdfToolType.VIEW_PDF,
        title = "PDF Reader",
        description = "High-speed PDF viewer with zoom, jump & thumbnails",
        icon = Icons.Default.PictureAsPdf
    ),
    PdfTool(
        type = PdfToolType.PASSWORD_PROTECT,
        title = "Encrypt & Lock",
        description = "Secure PDF files with custom user passwords",
        icon = Icons.Default.Lock
    ),
    PdfTool(
        type = PdfToolType.SCANNER_OCR,
        title = "Smart Doc Scan",
        description = "Scan paper docs & convert to clean searchable PDFs",
        icon = Icons.Default.QrCodeScanner,
        badge = "SMART"
    )
)
