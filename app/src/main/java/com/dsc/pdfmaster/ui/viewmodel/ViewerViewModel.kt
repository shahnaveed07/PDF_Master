package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfBookmarkEntry
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.model.PdfSearchMatch
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
    val currentPageIndex: Int = 0,
    val lastReadPageIndex: Int = 0,
    val isLoading: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val verifiedPassword: String? = null,
    val isNightInvertMode: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val activeSearchMatchIndex: Int = 0,
    val bookmarks: List<PdfBookmarkEntry> = emptyList(),
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
                    searchMatches = emptyList(),
                    activeSearchMatchIndex = 0,
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
                    val savedPage = repository.getLastReadPage(file.absolutePath)
                        .coerceIn(0, (meta.pageCount - 1).coerceAtLeast(0))
                    val bookmarks = repository.extractPdfBookmarksAndOutline(file, password)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPasswordProtected = false,
                            verifiedPassword = password,
                            pageCount = meta.pageCount,
                            currentPageIndex = savedPage,
                            lastReadPageIndex = savedPage,
                            bookmarks = bookmarks,
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

    fun toggleNightInvertMode() {
        _uiState.update { it.copy(isNightInvertMode = !it.isNightInvertMode) }
    }

    fun updateCurrentReadPage(pageIndex: Int) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        val safePage = pageIndex.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPageIndex = safePage, lastReadPageIndex = safePage) }
        viewModelScope.launch {
            repository.updateLastReadPage(file.absolutePath, safePage)
        }
    }

    fun searchInDocument(query: String) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchMatches = emptyList(), activeSearchMatchIndex = 0, isSearching = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val matches = repository.searchInPdf(file, query, current.verifiedPassword)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchMatches = matches,
                    activeSearchMatchIndex = 0
                )
            }
        }
    }

    fun nextSearchMatch(): Int? {
        val current = _uiState.value
        if (current.searchMatches.isEmpty()) return null
        val nextIdx = (current.activeSearchMatchIndex + 1) % current.searchMatches.size
        _uiState.update { it.copy(activeSearchMatchIndex = nextIdx) }
        return current.searchMatches[nextIdx].pageIndex
    }

    fun previousSearchMatch(): Int? {
        val current = _uiState.value
        if (current.searchMatches.isEmpty()) return null
        val prevIdx = if (current.activeSearchMatchIndex - 1 < 0) {
            current.searchMatches.lastIndex
        } else {
            current.activeSearchMatchIndex - 1
        }
        _uiState.update { it.copy(activeSearchMatchIndex = prevIdx) }
        return current.searchMatches[prevIdx].pageIndex
    }

    fun addBookmarkForPage(pageIndex: Int, title: String) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        viewModelScope.launch {
            val updated = repository.addBookmarkToPdf(
                pdfFile = file,
                title = title,
                pageIndex = pageIndex,
                writeToPdfOutline = false,
                password = current.verifiedPassword
            )
            _uiState.update { it.copy(bookmarks = updated) }
        }
    }

    fun removeBookmarkForPage(pageIndex: Int) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        viewModelScope.launch {
            val updated = repository.removeUserBookmark(file, pageIndex)
            _uiState.update { it.copy(bookmarks = updated) }
        }
    }

    fun extractCurrentPage(
        pageIndex: Int,
        onResult: (File?) -> Unit
    ) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        viewModelScope.launch {
            val outName = "${file.nameWithoutExtension}_page_${pageIndex + 1}.pdf"
            when (val res = repository.extractPages(file, listOf(pageIndex), outName, current.verifiedPassword)) {
                is PdfEngine.ExtractResult.Success -> onResult(res.file)
                is PdfEngine.ExtractResult.Error -> onResult(null)
            }
        }
    }

    fun deleteCurrentPage(
        pageIndex: Int,
        onResult: (File?) -> Unit
    ) {
        val current = _uiState.value
        val file = current.pdfFile ?: return
        if (current.pageCount <= 1) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val outName = "${file.nameWithoutExtension}_deleted_p${pageIndex + 1}.pdf"
            val updatedFile = repository.deleteSinglePageFromPdf(
                pdfFile = file,
                pageIndexToDelete = pageIndex,
                outputFileName = outName,
                password = current.verifiedPassword
            )
            if (updatedFile != null && updatedFile.exists()) {
                loadPdf(updatedFile, null)
            }
            onResult(updatedFile)
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
