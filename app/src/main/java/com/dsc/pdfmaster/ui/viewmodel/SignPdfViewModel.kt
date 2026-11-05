package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.model.SavedSignatureItem
import com.dsc.pdfmaster.data.model.SignaturePlacement
import com.dsc.pdfmaster.data.model.SignatureStroke
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfAdvancedToolsEngine
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
import javax.inject.Inject

data class SignPdfUiState(
    val sourceFile: File? = null,
    val pageCount: Int = 0,
    val selectedPageIndex: Int = 0,
    val pagePreviewBitmap: Bitmap? = null,
    val savedSignatures: List<SavedSignatureItem> = emptyList(),
    val selectedSignature: SavedSignatureItem? = null,
    val normalizedX: Float = 0.52f,
    val normalizedY: Float = 0.76f,
    val widthFraction: Float = 0.34f,
    val heightFraction: Float = 0.12f,
    val applyToAllPages: Boolean = false,
    val isProcessing: Boolean = false,
    val outputFile: File? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class SignPdfViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignPdfUiState())
    val uiState: StateFlow<SignPdfUiState> = _uiState.asStateFlow()

    init {
        refreshSavedSignatures()
    }

    fun refreshSavedSignatures() {
        viewModelScope.launch {
            val saved = repository.loadSavedSignatures()
            _uiState.update { state ->
                state.copy(
                    savedSignatures = saved,
                    selectedSignature = state.selectedSignature ?: saved.firstOrNull()
                )
            }
        }
    }

    fun setSourcePdf(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _uiState.update { it.copy(sourceFile = null, pageCount = 0, pagePreviewBitmap = null) }
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
                    outputFile = null,
                    error = null
                )
            }
            renderCurrentPagePreview(file, 0)
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
            renderCurrentPagePreview(file, clamped)
        }
    }

    private suspend fun renderCurrentPagePreview(file: File, pageIndex: Int) {
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

    fun saveDrawnSignature(strokes: List<SignatureStroke>, label: String = "My Signature") {
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                PdfAdvancedToolsEngine.renderSignatureStrokesToBitmap(strokes, 600, 240)
            }
            try {
                val saved = repository.saveReusableSignature(bitmap, label)
                val all = repository.loadSavedSignatures()
                _uiState.update {
                    it.copy(
                        savedSignatures = all,
                        selectedSignature = saved ?: all.firstOrNull(),
                        error = null
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun selectSignature(item: SavedSignatureItem) {
        _uiState.update { it.copy(selectedSignature = item) }
    }

    fun deleteSignature(item: SavedSignatureItem) {
        viewModelScope.launch {
            repository.deleteSavedSignature(item.file)
            val all = repository.loadSavedSignatures()
            _uiState.update {
                it.copy(
                    savedSignatures = all,
                    selectedSignature = if (it.selectedSignature?.id == item.id) all.firstOrNull() else it.selectedSignature
                )
            }
        }
    }

    fun updatePlacementPosition(normalizedX: Float, normalizedY: Float) {
        val current = _uiState.value
        val maxX = (1f - current.widthFraction).coerceAtLeast(0f)
        val maxY = (1f - current.heightFraction).coerceAtLeast(0f)
        _uiState.update {
            it.copy(
                normalizedX = normalizedX.coerceIn(0f, maxX),
                normalizedY = normalizedY.coerceIn(0f, maxY)
            )
        }
    }

    fun updatePlacementScale(widthFraction: Float) {
        val clampedW = widthFraction.coerceIn(0.15f, 0.75f)
        val clampedH = (clampedW * 0.38f).coerceIn(0.06f, 0.35f)
        _uiState.update {
            it.copy(
                widthFraction = clampedW,
                heightFraction = clampedH,
                normalizedX = it.normalizedX.coerceIn(0f, (1f - clampedW).coerceAtLeast(0f)),
                normalizedY = it.normalizedY.coerceIn(0f, (1f - clampedH).coerceAtLeast(0f))
            )
        }
    }

    fun setApplyToAllPages(applyAll: Boolean) {
        _uiState.update { it.copy(applyToAllPages = applyAll) }
    }

    fun flattenAndSaveSignedPdf(
        outputName: String,
        onSuccess: ((File) -> Unit)? = null
    ) {
        val current = _uiState.value
        val pdfFile = current.sourceFile ?: return
        val sig = current.selectedSignature ?: return
        if (current.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, outputFile = null) }
            try {
                val targetPages = if (current.applyToAllPages && current.pageCount > 1) {
                    (0 until current.pageCount).toList()
                } else {
                    listOf(current.selectedPageIndex)
                }
                val placements = targetPages.map { pageIdx ->
                    SignaturePlacement(
                        pageIndex = pageIdx,
                        signatureFile = sig.file,
                        normalizedX = current.normalizedX,
                        normalizedY = current.normalizedY,
                        widthFraction = current.widthFraction,
                        heightFraction = current.heightFraction
                    )
                }
                val desiredName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_signed.pdf" }
                val out = repository.flattenSignaturesIntoPdf(pdfFile, placements, desiredName)
                if (out != null && out.exists()) {
                    _uiState.update { it.copy(isProcessing = false, outputFile = out, error = null) }
                    onSuccess?.invoke(out)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = PdfOperationError.CorruptedPdf("Failed to flatten signature into PDF.")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = PdfOperationError.fromThrowable(e, "Failed to sign PDF")
                    )
                }
            }
        }
    }
}
