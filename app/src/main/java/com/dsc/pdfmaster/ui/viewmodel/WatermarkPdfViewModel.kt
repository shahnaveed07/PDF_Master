package com.dsc.pdfmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PageNumberConfig
import com.dsc.pdfmaster.data.model.PageNumberFormat
import com.dsc.pdfmaster.data.model.PageNumberPosition
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.model.WatermarkConfig
import com.dsc.pdfmaster.data.model.WatermarkPosition
import com.dsc.pdfmaster.data.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class WatermarkPdfUiState(
    val sourceFile: File? = null,
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val pageNumberConfig: PageNumberConfig = PageNumberConfig(),
    val isProcessing: Boolean = false,
    val outputFile: File? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class WatermarkPdfViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatermarkPdfUiState())
    val uiState: StateFlow<WatermarkPdfUiState> = _uiState.asStateFlow()

    fun setSourcePdf(file: File?) {
        _uiState.update {
            it.copy(sourceFile = file, outputFile = null, error = null)
        }
    }

    fun selectSourcePdfFromUri(uri: android.net.Uri) {
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

    fun updateWatermarkEnabled(enabled: Boolean) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(enabled = enabled), outputFile = null) }
    }

    fun updateWatermarkText(text: String) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(text = text), outputFile = null) }
    }

    fun updateWatermarkOpacity(opacity: Float) {
        _uiState.update {
            it.copy(watermarkConfig = it.watermarkConfig.copy(opacity = opacity.coerceIn(0.05f, 1.0f)), outputFile = null)
        }
    }

    fun updateWatermarkRotation(rotationDegrees: Float) {
        _uiState.update {
            it.copy(
                watermarkConfig = it.watermarkConfig.copy(rotationDegrees = rotationDegrees.coerceIn(-90f, 90f)),
                outputFile = null
            )
        }
    }

    fun updateWatermarkPosition(position: WatermarkPosition) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(position = position), outputFile = null) }
    }

    fun updateWatermarkFontSize(fontSizePt: Float) {
        _uiState.update {
            it.copy(
                watermarkConfig = it.watermarkConfig.copy(fontSizePt = fontSizePt.coerceIn(12f, 96f)),
                outputFile = null
            )
        }
    }

    fun updatePageNumbersEnabled(enabled: Boolean) {
        _uiState.update { it.copy(pageNumberConfig = it.pageNumberConfig.copy(enabled = enabled), outputFile = null) }
    }

    fun updatePageNumberFormat(format: PageNumberFormat) {
        _uiState.update { it.copy(pageNumberConfig = it.pageNumberConfig.copy(format = format), outputFile = null) }
    }

    fun updatePageNumberPosition(position: PageNumberPosition) {
        _uiState.update { it.copy(pageNumberConfig = it.pageNumberConfig.copy(position = position), outputFile = null) }
    }

    fun updatePageNumberStart(startNumber: Int) {
        _uiState.update {
            it.copy(
                pageNumberConfig = it.pageNumberConfig.copy(startNumber = startNumber.coerceAtLeast(1)),
                outputFile = null
            )
        }
    }

    fun applyWatermarkAndPageNumbers(
        outputName: String,
        onSuccess: ((File) -> Unit)? = null
    ) {
        val current = _uiState.value
        val pdfFile = current.sourceFile ?: return
        if (current.isProcessing) return
        if (!current.watermarkConfig.enabled && !current.pageNumberConfig.enabled) {
            _uiState.update {
                it.copy(error = PdfOperationError.Unknown("Enable either Watermark or Page Numbers to apply."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, outputFile = null) }
            try {
                val desiredName = outputName.trim().ifBlank { "${pdfFile.nameWithoutExtension}_watermarked.pdf" }
                val out = repository.applyWatermarkAndPageNumbers(
                    pdfFile = pdfFile,
                    watermarkConfig = current.watermarkConfig,
                    pageNumberConfig = current.pageNumberConfig,
                    outputFileName = desiredName
                )
                if (out != null && out.exists()) {
                    _uiState.update { it.copy(isProcessing = false, outputFile = out, error = null) }
                    onSuccess?.invoke(out)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = PdfOperationError.CorruptedPdf("Failed to apply watermark/page numbers.")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = PdfOperationError.fromThrowable(e, "Failed to apply watermark/page numbers")
                    )
                }
            }
        }
    }
}
