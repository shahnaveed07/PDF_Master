package com.dsc.pdfmaster.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.OcrSearchablePdfResult
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OcrUiState(
    val sourcePdfFile: File? = null,
    val selectedImageUris: List<Uri> = emptyList(),
    val isProcessing: Boolean = false,
    val ocrResult: OcrSearchablePdfResult? = null,
    val bundledModelCompressedMb: Double = TextRecognizerOptions.BUNDLED_MODEL_COMPRESSED_SIZE_MB,
    val bundledModelUncompressedMb: Double = TextRecognizerOptions.BUNDLED_MODEL_UNCOMPRESSED_SIZE_MB,
    val error: PdfOperationError? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    fun setSourcePdf(file: File?) {
        _uiState.update {
            it.copy(
                sourcePdfFile = file,
                selectedImageUris = emptyList(),
                ocrResult = null,
                error = null
            )
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

    fun setSourceImages(uris: List<Uri>) {
        uris.forEach { repository.takePersistableReadPermission(it) }
        _uiState.update {
            it.copy(
                sourcePdfFile = null,
                selectedImageUris = uris,
                ocrResult = null,
                error = null
            )
        }
    }

    fun runOcrToSearchablePdf(
        outputName: String,
        onSuccess: ((OcrSearchablePdfResult) -> Unit)? = null
    ) {
        val current = _uiState.value
        if (current.isProcessing) return
        if (current.sourcePdfFile == null && current.selectedImageUris.isEmpty()) {
            _uiState.update {
                it.copy(error = PdfOperationError.Unknown("Select a scanned PDF or one or more images for OCR."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, ocrResult = null, error = null) }
            try {
                val baseName = current.sourcePdfFile?.nameWithoutExtension ?: "Scanned_Document"
                val desiredName = outputName.trim().ifBlank { "${baseName}_searchable_ocr.pdf" }
                val res = repository.performOcrToSearchablePdf(
                    sourcePdfFile = current.sourcePdfFile,
                    sourceImageUris = current.selectedImageUris,
                    outputFileName = desiredName
                )
                if (res != null && res.outputFile.exists()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            ocrResult = res,
                            error = null
                        )
                    }
                    onSuccess?.invoke(res)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = PdfOperationError.CorruptedPdf("OCR processing failed to generate a searchable PDF.")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = PdfOperationError.fromThrowable(e, "OCR failed")
                    )
                }
            }
        }
    }
}
