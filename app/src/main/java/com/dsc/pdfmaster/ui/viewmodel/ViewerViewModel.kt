package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.pdfengine.PdfPageBitmapCache
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

data class ViewerUiState(
    val pdfFile: File? = null,
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val verifiedPassword: String? = null,
    val error: PdfOperationError? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository,
    val bitmapCache: PdfPageBitmapCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadPdf(file: File?, password: String? = null) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _uiState.value = ViewerUiState(
                pdfFile = file,
                isLoading = false,
                error = if (file != null) PdfOperationError.FileNotFound() else null
            )
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pdfFile = file,
                    isLoading = true,
                    verifiedPassword = password,
                    error = null
                )
            }
            withContext(Dispatchers.IO) {
                if (!PdfEngine.hasPdfHeader(file)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pageCount = 0,
                            error = PdfOperationError.CorruptedPdf()
                        )
                    }
                    return@withContext
                }
                val isEncrypted = PdfEngine.checkPasswordProtected(application, file)
                if (isEncrypted && password == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPasswordProtected = true,
                            pageCount = 0,
                            error = PdfOperationError.PasswordRequired()
                        )
                    }
                    return@withContext
                }
                val meta = PdfEngine.getPdfMetadata(application, Uri.fromFile(file), password)
                if (meta != null && meta.pageCount > 0) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPasswordProtected = false,
                            verifiedPassword = password,
                            pageCount = meta.pageCount,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pageCount = 0,
                            error = PdfOperationError.CorruptedPdf()
                        )
                    }
                }
            }
        }
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidth: Int,
        password: String?
    ): Bitmap? {
        return PdfEngine.renderPageBitmap(
            context = application,
            pdfFile = file,
            pageIndex = pageIndex,
            targetWidth = targetWidth,
            password = password,
            useCache = true,
            bitmapCache = bitmapCache
        )
    }

    fun clearCacheForFile(filePath: String) {
        bitmapCache.invalidateFile(filePath)
    }
}
