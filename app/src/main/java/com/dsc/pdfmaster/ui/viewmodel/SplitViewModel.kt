package com.dsc.pdfmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.ExtractionResultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SplitUiState(
    val sourceFile: File? = null,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val splitOutputFile: File? = null,
    val extractionResult: ExtractionResultState? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setSourceFile(file: File?) {
        _uiState.update {
            it.copy(
                sourceFile = file,
                splitOutputFile = null,
                extractionResult = null,
                error = null
            )
        }
    }

    fun resetExtractionResult() {
        _uiState.update { it.copy(extractionResult = null, error = null) }
    }

    fun executeSplit(
        file: File,
        pageIndices: List<Int>,
        outputName: String,
        onSuccess: ((File) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        if (_uiState.value.isProcessing) return
        if (pageIndices.isEmpty()) {
            val err = PdfOperationError.Unknown("Please select valid page numbers to split")
            _uiState.update { it.copy(error = err) }
            onError?.invoke(err)
            return
        }

        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Extracting ${pageIndices.size} pages...",
                    error = null
                )
            }
            try {
                val result = repository.splitPdf(file, pageIndices, outputName)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            splitOutputFile = result,
                            error = null
                        )
                    }
                    onSuccess?.invoke(result)
                } else {
                    val err = PdfOperationError.CorruptedPdf("Failed to split PDF.")
                    _uiState.update { it.copy(isProcessing = false, error = err) }
                    onError?.invoke(err)
                }
            } catch (e: CancellationException) {
                val err = PdfOperationError.Cancelled("Split cancelled")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Failed to split PDF.")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            }
        }
    }

    fun executeExtractPages(
        file: File,
        pageIndices: List<Int>,
        outputName: String,
        password: String? = null,
        onComplete: ((PdfEngine.ExtractResult) -> Unit)? = null
    ) {
        if (_uiState.value.isProcessing) return
        if (pageIndices.isEmpty()) {
            val err = PdfOperationError.Unknown("Please select at least 1 page to extract")
            _uiState.update { it.copy(error = err) }
            return
        }

        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Extracting ${pageIndices.size} pages...",
                    error = null
                )
            }
            try {
                when (val result = repository.extractPages(file, pageIndices, outputName, password)) {
                    is PdfEngine.ExtractResult.Success -> {
                        val state = ExtractionResultState(
                            outputFile = result.file,
                            pageCount = result.pageCount,
                            fileSizeBytes = result.fileSizeBytes
                        )
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                extractionResult = state,
                                error = null
                            )
                        }
                        onComplete?.invoke(result)
                    }
                    is PdfEngine.ExtractResult.Error -> {
                        val err = if (result.isPasswordProtected) {
                            PdfOperationError.PasswordRequired(result.message)
                        } else {
                            PdfOperationError.CorruptedPdf(result.message)
                        }
                        _uiState.update { it.copy(isProcessing = false, error = err) }
                        onComplete?.invoke(result)
                    }
                }
            } catch (e: CancellationException) {
                val err = PdfOperationError.Cancelled("Extraction cancelled")
                _uiState.update { it.copy(isProcessing = false, error = err) }
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Extraction failed")
                _uiState.update { it.copy(isProcessing = false, error = err) }
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(isProcessing = false) }
    }
}
