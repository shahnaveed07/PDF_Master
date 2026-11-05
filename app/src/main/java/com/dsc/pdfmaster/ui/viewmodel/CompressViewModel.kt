package com.dsc.pdfmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.ui.CompressionResultState
import com.dsc.pdfmaster.work.PdfWorkManager
import com.dsc.pdfmaster.work.PdfWorkSpec
import com.dsc.pdfmaster.work.PdfWorkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

data class CompressUiState(
    val sourceFile: File? = null,
    val selectedLevel: PdfCompressionLevel = PdfCompressionLevel.MEDIUM,
    val selectedMode: PdfCompressionMode = PdfCompressionMode.IMAGE_RECOMPRESSION,
    val isProcessing: Boolean = false,
    val progressPercent: Int = 0,
    val processingMessage: String = "",
    val compressionResult: CompressionResultState? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class CompressViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val workManager: PdfWorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    fun setSourceFile(file: File?) {
        _uiState.update {
            it.copy(
                sourceFile = file,
                compressionResult = null,
                error = null
            )
        }
    }

    fun setCompressionLevel(level: PdfCompressionLevel) {
        _uiState.update {
            it.copy(
                selectedLevel = level,
                compressionResult = null,
                error = null
            )
        }
    }

    fun setCompressionMode(mode: PdfCompressionMode) {
        _uiState.update {
            it.copy(
                selectedMode = mode,
                compressionResult = null,
                error = null
            )
        }
    }

    fun resetCompressionResult() {
        _uiState.update { it.copy(compressionResult = null, error = null) }
    }

    fun executeCompress(
        file: File,
        level: PdfCompressionLevel = _uiState.value.selectedLevel,
        mode: PdfCompressionMode = _uiState.value.selectedMode,
        outputName: String,
        onSuccess: ((CompressionResultState) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        if (_uiState.value.isProcessing) return
        if (!file.exists() || file.length() == 0L) {
            val err = PdfOperationError.FileNotFound()
            _uiState.update { it.copy(error = err) }
            onError?.invoke(err)
            return
        }

        _uiState.update {
            it.copy(
                sourceFile = file,
                selectedLevel = level,
                selectedMode = mode,
                isProcessing = true,
                progressPercent = 15,
                processingMessage = "Compressing PDF with ${level.label}...",
                compressionResult = null,
                error = null
            )
        }

        workManager.enqueue(
            spec = PdfWorkSpec.Compress(
                sourceFile = file,
                level = level,
                mode = mode,
                outputFileName = outputName
            ),
            onFinished = { info ->
                when (info.state) {
                    PdfWorkState.SUCCEEDED -> {
                        val outFile = info.outputFiles.firstOrNull()
                        if (outFile != null && outFile.exists()) {
                            val resultState = CompressionResultState(
                                originalSizeBytes = info.originalSizeBytes,
                                compressedSizeBytes = info.compressedSizeBytes,
                                outputFile = outFile,
                                isComplete = true
                            )
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    progressPercent = 100,
                                    processingMessage = "",
                                    compressionResult = resultState,
                                    error = null
                                )
                            }
                            onSuccess?.invoke(resultState)
                        } else {
                            val err = PdfOperationError.CorruptedPdf("Compression failed.")
                            _uiState.update { it.copy(isProcessing = false, error = err) }
                            onError?.invoke(err)
                        }
                    }
                    PdfWorkState.CANCELLED -> {
                        val err = info.error ?: PdfOperationError.Cancelled("Compression cancelled")
                        _uiState.update { it.copy(isProcessing = false, error = err) }
                        onError?.invoke(err)
                    }
                    else -> {
                        val err = info.error ?: PdfOperationError.CorruptedPdf("Compression failed.")
                        _uiState.update { it.copy(isProcessing = false, error = err) }
                        onError?.invoke(err)
                    }
                }
            }
        )
    }

    fun cancelCompression() {
        workManager.cancelActiveWork()
        _uiState.update {
            it.copy(
                isProcessing = false,
                error = PdfOperationError.Cancelled("Compression cancelled")
            )
        }
    }
}
