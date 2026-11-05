package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.work.PdfWorkManager
import com.dsc.pdfmaster.work.PdfWorkSpec
import com.dsc.pdfmaster.work.PdfWorkState
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

data class ConvertUiState(
    val sourcePdf: File? = null,
    val pdfRequiresPassword: Boolean = false,
    val sourceImageUri: Uri? = null,
    val sourceImageName: String? = null,
    val selectedImageUrisForPdf: List<Uri> = emptyList(),
    val sourceTextUri: Uri? = null,
    val sourceTextName: String? = null,
    val sourceTextPreview: String? = null,
    val sourceOfficeUri: Uri? = null,
    val sourceOfficeName: String? = null,
    val sourceOfficeFormat: OfficeToPdfConverter.OfficeDocumentType? = null,
    val sourceOfficeSummary: String? = null,
    val sourceOfficePreview: String? = null,
    val outputFiles: List<File> = emptyList(),
    val extractedText: String? = null,
    val resultSummary: String? = null,
    val layoutDisclosure: String? = null,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val error: PdfOperationError? = null
)

@HiltViewModel
class ConvertViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository,
    private val workManager: PdfWorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConvertUiState())
    val uiState: StateFlow<ConvertUiState> = _uiState.asStateFlow()

    fun resetSession() {
        _uiState.value = ConvertUiState()
    }

    fun clearOutputAndErrors() {
        _uiState.update {
            it.copy(
                outputFiles = emptyList(),
                extractedText = null,
                resultSummary = null,
                layoutDisclosure = null,
                error = null
            )
        }
    }

    fun addImagesForPdf(uris: List<Uri>) {
        uris.forEach { repository.takePersistableReadPermission(it) }
        _uiState.update {
            it.copy(
                selectedImageUrisForPdf = (it.selectedImageUrisForPdf + uris).distinct(),
                error = null
            )
        }
    }

    fun removeImageForPdf(uri: Uri) {
        _uiState.update {
            it.copy(
                selectedImageUrisForPdf = it.selectedImageUrisForPdf.filter { item -> item != uri }
            )
        }
    }

    fun clearSelectedImagesForPdf() {
        _uiState.update { it.copy(selectedImageUrisForPdf = emptyList()) }
    }

    fun selectSourcePdfFromUri(uri: Uri, onResult: ((File?, PdfOperationError?) -> Unit)? = null) {
        viewModelScope.launch {
            clearOutputAndErrors()
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null && file.exists() && PdfEngine.hasPdfHeader(file)) {
                val requiresPwd = PdfEngine.checkPasswordProtected(application, file)
                repository.registerPdfFile(file)
                _uiState.update {
                    it.copy(
                        sourcePdf = file,
                        pdfRequiresPassword = requiresPwd,
                        error = null
                    )
                }
                onResult?.invoke(file, null)
            } else {
                val err = PdfOperationError.CorruptedPdf("The selected file is not a valid PDF document.")
                _uiState.update {
                    it.copy(
                        sourcePdf = null,
                        pdfRequiresPassword = false,
                        error = err
                    )
                }
                onResult?.invoke(null, err)
            }
        }
    }

    fun selectSourceImageFromUri(uri: Uri, onResult: ((Boolean, String?, PdfOperationError?) -> Unit)? = null) {
        viewModelScope.launch {
            repository.takePersistableReadPermission(uri)
            clearOutputAndErrors()
            val isValid = withContext(Dispatchers.IO) {
                try {
                    if (!PdfEngine.hasSupportedImageHeader(application, uri)) {
                        return@withContext false
                    }
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val stream = if (uri.scheme == "file") {
                        uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
                    } else {
                        application.contentResolver.openInputStream(uri)
                    }
                    stream?.use { BitmapFactory.decodeStream(it, null, opts) }
                    opts.outWidth > 0 && opts.outHeight > 0
                } catch (_: Exception) {
                    false
                }
            }
            if (isValid) {
                val name = resolveDisplayName(uri, "selected_image")
                _uiState.update {
                    it.copy(
                        sourceImageUri = uri,
                        sourceImageName = name,
                        error = null
                    )
                }
                onResult?.invoke(true, name, null)
            } else {
                val err = PdfOperationError.UnsupportedFormat("Selected file is not a valid image.")
                _uiState.update {
                    it.copy(
                        sourceImageUri = null,
                        sourceImageName = null,
                        error = err
                    )
                }
                onResult?.invoke(false, null, err)
            }
        }
    }

    fun selectSourceTextFromUri(uri: Uri, onResult: ((String?, String?, PdfOperationError?) -> Unit)? = null) {
        viewModelScope.launch {
            repository.takePersistableReadPermission(uri)
            clearOutputAndErrors()
            val text = withContext(Dispatchers.IO) {
                PdfEngine.readValidatedPlainTextFromUri(application, uri)
            }
            if (!text.isNullOrBlank()) {
                val name = resolveDisplayName(uri, "document.txt")
                val preview = text.take(500)
                _uiState.update {
                    it.copy(
                        sourceTextUri = uri,
                        sourceTextName = name,
                        sourceTextPreview = preview,
                        error = null
                    )
                }
                onResult?.invoke(name, preview, null)
            } else {
                val err = PdfOperationError.UnsupportedFormat("Selected file is empty or is not a valid plain-text file.")
                _uiState.update {
                    it.copy(
                        sourceTextUri = null,
                        sourceTextName = null,
                        sourceTextPreview = null,
                        error = err
                    )
                }
                onResult?.invoke(null, null, err)
            }
        }
    }

    fun selectSourceOfficeFromUri(
        uri: Uri,
        expectedType: OfficeToPdfConverter.OfficeDocumentType,
        onResult: ((OfficeToPdfConverter.OfficeSourceInspection, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.takePersistableReadPermission(uri)
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Inspecting ${expectedType.label} document...",
                    outputFiles = emptyList(),
                    resultSummary = null,
                    layoutDisclosure = null,
                    error = null
                )
            }
            try {
                val displayName = resolveDisplayName(
                    uri,
                    when (expectedType) {
                        OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX -> "document.docx"
                        OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX -> "spreadsheet.xlsx"
                        OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX -> "presentation.pptx"
                    }
                )
                val inspection = repository.inspectOfficeSource(uri, expectedType)
                if (inspection.isValid) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            sourceOfficeUri = uri,
                            sourceOfficeName = displayName,
                            sourceOfficeFormat = inspection.detectedType ?: expectedType,
                            sourceOfficeSummary = inspection.previewSummary,
                            sourceOfficePreview = inspection.previewSummary,
                            error = null
                        )
                    }
                } else {
                    val errMsg = inspection.errorMessage ?: "Selected file is not a valid ${expectedType.label} document."
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            sourceOfficeUri = null,
                            sourceOfficeName = null,
                            sourceOfficeFormat = null,
                            sourceOfficeSummary = null,
                            sourceOfficePreview = null,
                            error = PdfOperationError.UnsupportedFormat(errMsg)
                        )
                    }
                }
                onResult?.invoke(inspection, displayName)
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun executeImagesToPdf(
        outputFileName: String,
        onSuccess: ((File) -> Unit)? = null,
        onError: ((PdfOperationError) -> Unit)? = null
    ) {
        val current = _uiState.value
        if (current.isProcessing) return
        if (current.selectedImageUrisForPdf.isEmpty()) {
            val err = PdfOperationError.Unknown("Please select at least 1 image")
            _uiState.update { it.copy(error = err) }
            onError?.invoke(err)
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingMessage = "Converting ${current.selectedImageUrisForPdf.size} images to PDF...",
                error = null
            )
        }

        workManager.enqueue(
            spec = PdfWorkSpec.ImagesToPdf(
                imageUris = current.selectedImageUrisForPdf,
                outputFileName = outputFileName
            ),
            onFinished = { info ->
                if (info.state == PdfWorkState.SUCCEEDED && info.outputFiles.isNotEmpty()) {
                    val out = info.outputFiles.first()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            selectedImageUrisForPdf = emptyList(),
                            outputFiles = listOf(out),
                            error = null
                        )
                    }
                    onSuccess?.invoke(out)
                } else {
                    val err = info.error ?: PdfOperationError.UnsupportedFormat(info.message)
                    _uiState.update { it.copy(isProcessing = false, error = err) }
                    onError?.invoke(err)
                }
            }
        )
    }

    private fun resolveDisplayName(uri: Uri, defaultFallback: String): String {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name } ?: defaultFallback
        }
        return try {
            val cursor = application.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val name = it.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
            defaultFallback
        } catch (_: Exception) {
            defaultFallback
        }
    }
}
