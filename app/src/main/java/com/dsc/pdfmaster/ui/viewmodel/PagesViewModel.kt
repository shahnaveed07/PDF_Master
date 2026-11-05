package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
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

data class PagesUiState(
    val sourceFile: File? = null,
    val pages: List<PdfPageItem> = emptyList(),
    val isLoadingPages: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val outputFile: File? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class PagesViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagesUiState())
    val uiState: StateFlow<PagesUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun loadPagesForPdf(file: File) {
        _uiState.update {
            it.copy(
                sourceFile = file,
                isLoadingPages = true,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val pages = PdfEngine.loadPdfPageThumbnails(application, file)
                _uiState.update {
                    it.copy(
                        pages = pages,
                        isLoadingPages = false,
                        error = null
                    )
                }
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Failed to load PDF pages")
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        error = err
                    )
                }
            }
        }
    }

    fun rotatePage(pageIndex: Int) {
        _uiState.update { current ->
            current.copy(
                pages = current.pages.map { item ->
                    if (item.pageIndex == pageIndex) {
                        val newRot = (item.rotationDegrees + 90) % 360
                        item.copy(rotationDegrees = newRot)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun togglePageSelectionForDelete(pageIndex: Int) {
        _uiState.update { current ->
            current.copy(
                pages = current.pages.map { item ->
                    if (item.pageIndex == pageIndex) {
                        item.copy(isSelected = !item.isSelected)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun executeSaveOrganizedPages(
        file: File,
        outputName: String,
        onSuccess: ((File) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        val current = _uiState.value
        if (current.isProcessing) return
        val remainingPages = current.pages.filter { !it.isSelected }
        if (remainingPages.isEmpty()) {
            val err = PdfOperationError.Unknown("Cannot delete all pages from PDF!")
            _uiState.update { it.copy(error = err) }
            onError?.invoke(err)
            return
        }

        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Saving updated PDF structure...",
                    error = null
                )
            }
            try {
                val result = repository.organizePages(file, current.pages, outputName)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            outputFile = result,
                            error = null
                        )
                    }
                    onSuccess?.invoke(result)
                } else {
                    val err = PdfOperationError.CorruptedPdf("Failed to organize pages.")
                    _uiState.update { it.copy(isProcessing = false, error = err) }
                    onError?.invoke(err)
                }
            } catch (e: CancellationException) {
                val err = PdfOperationError.Cancelled("Operation cancelled")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Failed to organize pages.")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            }
        }
    }

    fun executeCreateTextPdf(
        textContent: String,
        outputName: String,
        onSuccess: ((File) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        if (_uiState.value.isProcessing || textContent.isBlank()) return
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Generating Text PDF...",
                    error = null
                )
            }
            try {
                val result = repository.createTextPdf(textContent, outputName)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            outputFile = result,
                            error = null
                        )
                    }
                    onSuccess?.invoke(result)
                } else {
                    val err = PdfOperationError.Unknown("Failed to create Text PDF")
                    _uiState.update { it.copy(isProcessing = false, error = err) }
                    onError?.invoke(err)
                }
            } catch (e: CancellationException) {
                val err = PdfOperationError.Cancelled("Operation cancelled")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Failed to create Text PDF")
                _uiState.update { it.copy(isProcessing = false, error = err) }
                onError?.invoke(err)
            }
        }
    }
}
