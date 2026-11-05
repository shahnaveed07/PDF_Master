package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.AnnotationHistoryStack
import com.dsc.pdfmaster.data.model.AnnotationToolType
import com.dsc.pdfmaster.data.model.NormalizedPoint
import com.dsc.pdfmaster.data.model.PdfAnnotationItem
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class AnnotatePdfUiState(
    val sourceFile: File? = null,
    val pageCount: Int = 0,
    val selectedPageIndex: Int = 0,
    val pagePreviewBitmap: Bitmap? = null,
    val selectedTool: AnnotationToolType = AnnotationToolType.HIGHLIGHT,
    val history: AnnotationHistoryStack = AnnotationHistoryStack(),
    val strokeWidthPt: Float = 3f,
    val fontSizePt: Float = 14f,
    val isProcessing: Boolean = false,
    val outputFile: File? = null,
    val error: PdfOperationError? = null
) {
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val currentPageAnnotations: List<PdfAnnotationItem>
        get() = history.itemsForPage(selectedPageIndex)
}

@HiltViewModel
class AnnotatePdfViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnotatePdfUiState())
    val uiState: StateFlow<AnnotatePdfUiState> = _uiState.asStateFlow()

    fun setSourcePdf(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _uiState.update {
                it.copy(
                    sourceFile = null,
                    pageCount = 0,
                    pagePreviewBitmap = null,
                    history = AnnotationHistoryStack()
                )
            }
            return
        }
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                PdfEngine.getPdfMetadata(application, Uri.fromFile(file))
            }
            val count = (meta?.pageCount ?: 1).coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    sourceFile = file,
                    pageCount = count,
                    selectedPageIndex = 0,
                    history = AnnotationHistoryStack(),
                    outputFile = null,
                    error = null
                )
            }
            renderPagePreview(file, 0)
        }
    }

    fun selectSourcePdfFromUri(uri: Uri) {
        viewModelScope.launch {
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null) {
                repository.registerPdfFile(file)
                setSourcePdf(file)
            } else {
                _uiState.update {
                    it.copy(error = PdfOperationError.CorruptedPdf("Selected file is not a valid PDF document."))
                }
            }
        }
    }

    fun selectPage(pageIndex: Int) {
        val current = _uiState.value
        val file = current.sourceFile ?: return
        val clamped = pageIndex.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
        _uiState.update { it.copy(selectedPageIndex = clamped) }
        viewModelScope.launch {
            renderPagePreview(file, clamped)
        }
    }

    private suspend fun renderPagePreview(file: File, pageIndex: Int) {
        val bmp = withContext(Dispatchers.IO) {
            PdfEngine.renderPageBitmap(
                context = application,
                pdfFile = file,
                pageIndex = pageIndex,
                targetWidth = 720,
                useCache = true
            )
        }
        _uiState.update { it.copy(pagePreviewBitmap = bmp) }
    }

    fun setTool(tool: AnnotationToolType) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun setStrokeWidth(widthPt: Float) {
        _uiState.update { it.copy(strokeWidthPt = widthPt.coerceIn(1f, 12f)) }
    }

    fun setFontSize(sizePt: Float) {
        _uiState.update { it.copy(fontSizePt = sizePt.coerceIn(8f, 36f)) }
    }

    fun addHighlight(startX: Float, startY: Float, endX: Float, endY: Float) {
        val current = _uiState.value
        val item = PdfAnnotationItem.Highlight(
            id = UUID.randomUUID().toString(),
            pageIndex = current.selectedPageIndex,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY
        )
        _uiState.update { it.copy(history = it.history.add(item), outputFile = null) }
    }

    fun addFreehandStroke(points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        val current = _uiState.value
        val item = PdfAnnotationItem.Freehand(
            id = UUID.randomUUID().toString(),
            pageIndex = current.selectedPageIndex,
            points = points,
            strokeWidthPt = current.strokeWidthPt
        )
        _uiState.update { it.copy(history = it.history.add(item), outputFile = null) }
    }

    fun addTextBox(text: String, normalizedX: Float, normalizedY: Float) {
        if (text.isBlank()) return
        val current = _uiState.value
        val item = PdfAnnotationItem.TextBox(
            id = UUID.randomUUID().toString(),
            pageIndex = current.selectedPageIndex,
            text = text.trim(),
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            fontSizePt = current.fontSizePt
        )
        _uiState.update { it.copy(history = it.history.add(item), outputFile = null) }
    }

    fun undo() {
        _uiState.update { it.copy(history = it.history.undo()) }
    }

    fun redo() {
        _uiState.update { it.copy(history = it.history.redo()) }
    }

    fun clearCurrentPageAnnotations() {
        _uiState.update { it.copy(history = it.history.clearPage(it.selectedPageIndex)) }
    }

    fun saveAnnotatedPdf(
        outputName: String,
        onSuccess: ((File) -> Unit)? = null
    ) {
        val current = _uiState.value
        val pdfFile = current.sourceFile ?: return
        val annotations = current.history.applied
        if (annotations.isEmpty() || current.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, outputFile = null) }
            try {
                val desiredName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_annotated.pdf" }
                val out = repository.applyAnnotationsToPdf(pdfFile, annotations, desiredName)
                if (out != null && out.exists()) {
                    _uiState.update { it.copy(isProcessing = false, outputFile = out, error = null) }
                    onSuccess?.invoke(out)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = PdfOperationError.CorruptedPdf("Failed to save annotations to PDF.")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = PdfOperationError.fromThrowable(e, "Failed to save annotated PDF")
                    )
                }
            }
        }
    }
}
