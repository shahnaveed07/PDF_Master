package com.dsc.pdfmaster.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.model.ScannedPageDraft
import com.dsc.pdfmaster.data.model.ScannerFilterMode
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class DocumentScannerUiState(
    val scannedPages: List<ScannedPageDraft> = emptyList(),
    val defaultFilterMode: ScannerFilterMode = ScannerFilterMode.AUTO_ENHANCE,
    val isProcessing: Boolean = false,
    val exportedPdfFile: File? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class DocumentScannerViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentScannerUiState())
    val uiState: StateFlow<DocumentScannerUiState> = _uiState.asStateFlow()

    fun onMlKitScannerResult(result: GmsDocumentScanningResult?) {
        if (result == null) return
        val pages = result.pages?.map { it.imageUri }.orEmpty()
        if (pages.isNotEmpty()) {
            addScannedPageUris(pages)
            return
        }
        result.pdf?.uri?.let { pdfUri ->
            viewModelScope.launch {
                val imported = repository.getOrCreateManagedFileFromUri(pdfUri)
                if (imported != null) {
                    repository.registerPdfFile(imported)
                    _uiState.update { it.copy(exportedPdfFile = imported, error = null) }
                }
            }
        }
    }

    fun addScannedPageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { repository.takePersistableReadPermission(it) }
        val filter = _uiState.value.defaultFilterMode
        val newDrafts = uris.map { uri ->
            ScannedPageDraft(
                id = UUID.randomUUID().toString(),
                uri = uri,
                filterMode = filter,
                rotationDegrees = 0
            )
        }
        _uiState.update {
            it.copy(
                scannedPages = it.scannedPages + newDrafts,
                exportedPdfFile = null,
                error = null
            )
        }
    }

    fun setDefaultFilterMode(mode: ScannerFilterMode) {
        _uiState.update { state ->
            state.copy(
                defaultFilterMode = mode,
                scannedPages = state.scannedPages.map { it.copy(filterMode = mode) },
                exportedPdfFile = null
            )
        }
    }

    fun rotatePage(pageId: String) {
        _uiState.update { state ->
            state.copy(
                scannedPages = state.scannedPages.map { page ->
                    if (page.id == pageId) {
                        page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
                    } else {
                        page
                    }
                },
                exportedPdfFile = null
            )
        }
    }

    fun removePage(pageId: String) {
        _uiState.update { state ->
            state.copy(
                scannedPages = state.scannedPages.filterNot { it.id == pageId },
                exportedPdfFile = null
            )
        }
    }

    fun clearAllPages() {
        _uiState.update { it.copy(scannedPages = emptyList(), exportedPdfFile = null, error = null) }
    }

    fun exportScannedPdf(
        outputName: String,
        onSuccess: ((File) -> Unit)? = null
    ) {
        val current = _uiState.value
        if (current.isProcessing || current.scannedPages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, exportedPdfFile = null, error = null) }
            try {
                val desiredName = outputName.trim().ifBlank { "Scanned_Document_${System.currentTimeMillis() / 1000}.pdf" }
                val out = repository.exportScannedPagesToPdf(current.scannedPages, desiredName)
                if (out != null && out.exists()) {
                    _uiState.update { it.copy(isProcessing = false, exportedPdfFile = out, error = null) }
                    onSuccess?.invoke(out)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = PdfOperationError.CorruptedPdf("Failed to export scanned pages to PDF.")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = PdfOperationError.fromThrowable(e, "Failed to export scanned PDF")
                    )
                }
            }
        }
    }
}
