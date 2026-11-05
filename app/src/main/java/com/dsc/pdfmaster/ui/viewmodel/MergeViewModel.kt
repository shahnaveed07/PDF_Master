package com.dsc.pdfmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
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

data class MergeUiState(
    val selectedFiles: List<File> = emptyList(),
    val isProcessing: Boolean = false,
    val progressPercent: Int = 0,
    val processingMessage: String = "",
    val mergedOutputFile: File? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class MergeViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val workManager: PdfWorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    fun addFiles(files: List<File>) {
        _uiState.update { current ->
            current.copy(
                selectedFiles = (current.selectedFiles + files).distinctBy { it.absolutePath },
                error = null
            )
        }
    }

    fun removeFile(file: File) {
        _uiState.update { current ->
            current.copy(
                selectedFiles = current.selectedFiles.filter { it.absolutePath != file.absolutePath },
                error = null
            )
        }
    }

    fun clearFiles() {
        _uiState.update { MergeUiState() }
    }

    fun executeMerge(
        outputFileName: String,
        onSuccess: ((File) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        val current = _uiState.value
        if (current.isProcessing) return
        if (current.selectedFiles.size < 2) {
            val err = PdfOperationError.Unknown("Please select at least 2 PDF files to merge")
            _uiState.update { it.copy(error = err) }
            onError?.invoke(err)
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                progressPercent = 10,
                processingMessage = "Merging ${current.selectedFiles.size} PDF files...",
                error = null
            )
        }

        workManager.enqueue(
            spec = PdfWorkSpec.Merge(
                inputFiles = current.selectedFiles,
                outputFileName = outputFileName
            ),
            onFinished = { info ->
                when (info.state) {
                    PdfWorkState.SUCCEEDED -> {
                        val out = info.outputFiles.firstOrNull()
                        if (out != null) {
                            _uiState.update {
                                it.copy(
                                    selectedFiles = emptyList(),
                                    isProcessing = false,
                                    progressPercent = 100,
                                    processingMessage = "",
                                    mergedOutputFile = out,
                                    error = null
                                )
                            }
                            onSuccess?.invoke(out)
                        } else {
                            val err = PdfOperationError.CorruptedPdf("Merge failed. Please verify the selected PDFs.")
                            _uiState.update { it.copy(isProcessing = false, error = err) }
                            onError?.invoke(err)
                        }
                    }
                    PdfWorkState.CANCELLED -> {
                        val err = info.error ?: PdfOperationError.Cancelled("Merge cancelled")
                        _uiState.update { it.copy(isProcessing = false, error = err) }
                        onError?.invoke(err)
                    }
                    else -> {
                        val err = info.error ?: PdfOperationError.CorruptedPdf("Merge failed. Please verify the selected PDFs.")
                        _uiState.update { it.copy(isProcessing = false, error = err) }
                        onError?.invoke(err)
                    }
                }
            }
        )
    }

    fun cancelMerge() {
        workManager.cancelActiveWork()
        _uiState.update {
            it.copy(
                isProcessing = false,
                error = PdfOperationError.Cancelled("Merge cancelled")
            )
        }
    }
}
