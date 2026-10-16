package com.dsc.pdfmaster.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.database.PdfDatabase
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.data.model.PdfTool
import com.dsc.pdfmaster.data.model.PdfToolType
import com.dsc.pdfmaster.data.model.pdfToolsList
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenRoute {
    HOME,
    RECENT_FILES,
    PDF_VIEWER,
    EXTRACT_PAGES,
    MERGE_PDF,
    SPLIT_PDF,
    COMPRESS_PDF,
    IMAGES_TO_PDF,
    MANAGE_PAGES,
    PASSWORD_PROTECT,
    SETTINGS
}

data class ExtractionResultState(
    val outputFile: File,
    val pageCount: Int,
    val fileSizeBytes: Long
)

data class CompressionResultState(
    val originalSizeBytes: Long = 0,
    val compressedSizeBytes: Long = 0,
    val outputFile: File? = null,
    val isComplete: Boolean = false
)

class PdfMasterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PdfDatabase.getDatabase(application)
    val repository = PdfRepository(application, db.recentPdfDao())

    private val _currentRoute = MutableStateFlow(ScreenRoute.HOME)
    val currentRoute: StateFlow<ScreenRoute> = _currentRoute.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isLoadingPages = MutableStateFlow(false)
    val isLoadingPages: StateFlow<Boolean> = _isLoadingPages.asStateFlow()

    private val _processingMessage = MutableStateFlow("")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _userNotification = MutableStateFlow<String?>(null)
    val userNotification: StateFlow<String?> = _userNotification.asStateFlow()

    // Currently opened PDF for Viewer or Operations
    private val _selectedPdfFile = MutableStateFlow<File?>(null)
    val selectedPdfFile: StateFlow<File?> = _selectedPdfFile.asStateFlow()

    // Files selected for Merge
    private val _selectedFilesForMerge = MutableStateFlow<List<File>>(emptyList())
    val selectedFilesForMerge: StateFlow<List<File>> = _selectedFilesForMerge.asStateFlow()

    // Images selected for ImagesToPdf
    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris.asStateFlow()

    // Pages list for Split / Reorder / Rotate
    private val _pdfPagesList = MutableStateFlow<List<PdfPageItem>>(emptyList())
    val pdfPagesList: StateFlow<List<PdfPageItem>> = _pdfPagesList.asStateFlow()

    // Compression comparison state
    private val _compressionResult = MutableStateFlow<CompressionResultState?>(null)
    val compressionResult: StateFlow<CompressionResultState?> = _compressionResult.asStateFlow()

    // Extraction result state
    private val _extractionResult = MutableStateFlow<ExtractionResultState?>(null)
    val extractionResult: StateFlow<ExtractionResultState?> = _extractionResult.asStateFlow()

    // Flow of recent documents combined with search query
    val recentPdfs: StateFlow<List<RecentPdfEntity>> = repository.allRecentPdfs
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritePdfs: StateFlow<List<RecentPdfEntity>> = repository.favoritePdfs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val backStack = ArrayDeque<ScreenRoute>(listOf(ScreenRoute.HOME))

    init {
        viewModelScope.launch {
            // Ensure sample PDF exists on first launch
            val sample = repository.ensureStarterSamplePdf()
            if (_selectedPdfFile.value == null) {
                _selectedPdfFile.value = sample
            }
        }
    }

    fun navigateTo(route: ScreenRoute) {
        _isProcessing.value = false
        if (_currentRoute.value == route) return

        if (route in listOf(ScreenRoute.HOME, ScreenRoute.RECENT_FILES, ScreenRoute.SETTINGS)) {
            backStack.clear()
            backStack.addLast(ScreenRoute.HOME)
            if (route != ScreenRoute.HOME) {
                backStack.addLast(route)
            }
        } else {
            backStack.addLast(route)
        }
        _currentRoute.value = route
    }

    fun navigateBack(): Boolean {
        _isProcessing.value = false
        if (backStack.size > 1) {
            backStack.removeLast()
            _currentRoute.value = backStack.last()
            return true
        }
        return false
    }

    fun resetProcessingState() {
        _isProcessing.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _userNotification.value = "Recent history cleared"
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun clearNotification() {
        _userNotification.value = null
    }

    fun openPdfInViewer(file: File) {
        _selectedPdfFile.value = file
        viewModelScope.launch {
            repository.registerPdfFile(file)
        }
        navigateTo(ScreenRoute.PDF_VIEWER)
    }

    fun importAndSelectPdf(uri: Uri, openViewer: Boolean = false) {
        viewModelScope.launch {
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null) {
                _selectedPdfFile.value = file
                repository.registerPdfFile(file)
                if (openViewer) {
                    navigateTo(ScreenRoute.PDF_VIEWER)
                }
            } else {
                _userNotification.value = "Failed to load selected PDF document"
            }
        }
    }

    fun toggleFavorite(filePath: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(filePath, !currentStatus)
        }
    }

    fun deletePdf(filePath: String) {
        viewModelScope.launch {
            repository.deletePdf(filePath)
            _userNotification.value = "File deleted successfully"
            if (_selectedPdfFile.value?.absolutePath == filePath) {
                _selectedPdfFile.value = null
            }
        }
    }

    fun renamePdf(file: File, newName: String) {
        viewModelScope.launch {
            val renamed = repository.renamePdf(file, newName)
            if (renamed != null) {
                _selectedPdfFile.value = renamed
                _userNotification.value = "Renamed to ${renamed.name}"
            } else {
                _userNotification.value = "Failed to rename file"
            }
        }
    }

    fun setSelectedPdfFile(file: File) {
        _selectedPdfFile.value = file
        viewModelScope.launch {
            repository.registerPdfFile(file)
        }
    }

    // Launch tool action from Dashboard
    fun launchTool(tool: PdfTool) {
        when (tool.type) {
            PdfToolType.EXTRACT_PAGES -> _currentRoute.value = ScreenRoute.EXTRACT_PAGES
            PdfToolType.MERGE -> _currentRoute.value = ScreenRoute.MERGE_PDF
            PdfToolType.SPLIT -> _currentRoute.value = ScreenRoute.SPLIT_PDF
            PdfToolType.COMPRESS -> _currentRoute.value = ScreenRoute.COMPRESS_PDF
            PdfToolType.IMAGES_TO_PDF -> _currentRoute.value = ScreenRoute.IMAGES_TO_PDF
            PdfToolType.VIEW_PDF -> _currentRoute.value = ScreenRoute.PDF_VIEWER
            PdfToolType.MANAGE_PAGES -> {
                if (_selectedPdfFile.value != null) {
                    loadPagesForPdf(_selectedPdfFile.value!!)
                }
                _currentRoute.value = ScreenRoute.MANAGE_PAGES
            }
            PdfToolType.PASSWORD_PROTECT -> _currentRoute.value = ScreenRoute.PASSWORD_PROTECT
        }
    }

    // Merge operations
    fun addFilesForMerge(files: List<File>) {
        _selectedFilesForMerge.value = (_selectedFilesForMerge.value + files).distinctBy { it.absolutePath }
    }

    fun removeFileFromMerge(file: File) {
        _selectedFilesForMerge.value = _selectedFilesForMerge.value.filter { it.absolutePath != file.absolutePath }
    }

    fun executeMergePdfs(outputFileName: String) {
        val files = _selectedFilesForMerge.value
        if (files.size < 2) {
            _userNotification.value = "Please select at least 2 PDF files to merge"
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Merging ${files.size} PDF files..."
            try {
                val result = repository.mergePdfs(files, outputFileName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _selectedFilesForMerge.value = emptyList()
                    _userNotification.value = "Merged successfully into ${result.name}!"
                    _currentRoute.value = ScreenRoute.PDF_VIEWER
                } else {
                    _userNotification.value = "Merge failed. Please try again."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userNotification.value = "Merge failed due to an error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Images to PDF
    fun addImagesForPdf(uris: List<Uri>) {
        _selectedImageUris.value = (_selectedImageUris.value + uris).distinct()
    }

    fun removeImageForPdf(uri: Uri) {
        _selectedImageUris.value = _selectedImageUris.value.filter { it != uri }
    }

    fun executeImagesToPdf(outputFileName: String) {
        val uris = _selectedImageUris.value
        if (uris.isEmpty()) {
            _userNotification.value = "Please select at least 1 image"
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Converting ${uris.size} images to PDF..."
            try {
                val result = repository.convertImagesToPdf(uris, outputFileName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _selectedImageUris.value = emptyList()
                    _userNotification.value = "Converted to PDF: ${result.name}"
                    _currentRoute.value = ScreenRoute.PDF_VIEWER
                } else {
                    _userNotification.value = "Conversion failed. Check image formats."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userNotification.value = "Conversion failed due to an error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Compress PDF
    fun executeCompressPdf(file: File, level: PdfCompressionLevel, outputName: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Compressing PDF with ${level.label}..."
            try {
                val originalSize = file.length()
                val resultFile = repository.compressPdf(file, level, outputName)
                if (resultFile != null) {
                    val compressedSize = resultFile.length()
                    _compressionResult.value = CompressionResultState(
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = compressedSize,
                        outputFile = resultFile,
                        isComplete = true
                    )
                    _selectedPdfFile.value = resultFile
                    _userNotification.value = "Compressed successfully!"
                } else {
                    _userNotification.value = "Compression failed."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userNotification.value = "Compression failed due to an error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun resetCompressionResult() {
        _compressionResult.value = null
    }

    // Organize Pages (Load, Rotate, Delete, Reorder)
    fun loadPagesForPdf(file: File) {
        _selectedPdfFile.value = file
        viewModelScope.launch {
            _isLoadingPages.value = true
            try {
                val pages = PdfEngine.loadPdfPageThumbnails(getApplication(), file)
                _pdfPagesList.value = pages
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingPages.value = false
            }
        }
    }

    fun rotatePage(pageIndex: Int) {
        _pdfPagesList.value = _pdfPagesList.value.map { item ->
            if (item.pageIndex == pageIndex) {
                val newRot = (item.rotationDegrees + 90) % 360
                item.copy(rotationDegrees = newRot)
            } else item
        }
    }

    fun togglePageSelectionForDelete(pageIndex: Int) {
        _pdfPagesList.value = _pdfPagesList.value.map { item ->
            if (item.pageIndex == pageIndex) {
                item.copy(isSelected = !item.isSelected)
            } else item
        }
    }

    fun executeSaveOrganizedPages(file: File, outputName: String) {
        val currentPages = _pdfPagesList.value
        val remainingPages = currentPages.filter { !it.isSelected }
        if (remainingPages.isEmpty()) {
            _userNotification.value = "Cannot delete all pages from PDF!"
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Saving updated PDF structure..."
            try {
                val result = repository.organizePages(file, currentPages, outputName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _userNotification.value = "Pages saved successfully!"
                    _currentRoute.value = ScreenRoute.PDF_VIEWER
                } else {
                    _userNotification.value = "Failed to organize pages."
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    val snackbarMessage: StateFlow<String?> = _userNotification.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSnackbarMessage() {
        _userNotification.value = null
    }

    // Split PDF
    fun executeSplitPdf(file: File, pageIndices: List<Int>, outputName: String) {
        if (pageIndices.isEmpty()) {
            _userNotification.value = "Please select valid page numbers to split"
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Extracting ${pageIndices.size} pages..."
            try {
                val result = repository.splitPdf(file, pageIndices, outputName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _userNotification.value = "Split PDF saved: ${result.name}"
                    _currentRoute.value = ScreenRoute.PDF_VIEWER
                } else {
                    _userNotification.value = "Failed to split PDF."
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Password Protect PDF
    fun executeProtectPdf(file: File, password: String, outputName: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Encrypting & locking PDF..."
            try {
                val result = repository.protectPdf(file, password, outputName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _userNotification.value = "Document encrypted successfully!"
                    navigateTo(ScreenRoute.PDF_VIEWER)
                } else {
                    _userNotification.value = "Failed to encrypt PDF."
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun resetExtractionResult() {
        _extractionResult.value = null
    }

    fun executeExtractPages(
        file: File,
        pageIndices: List<Int>,
        outputName: String,
        password: String? = null,
        onComplete: ((PdfEngine.ExtractResult) -> Unit)? = null
    ) {
        if (pageIndices.isEmpty()) {
            _userNotification.value = "Please select at least 1 page to extract"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Extracting ${pageIndices.size} pages..."
            try {
                val result = repository.extractPages(file, pageIndices, outputName, password)
                when (result) {
                    is PdfEngine.ExtractResult.Success -> {
                        _extractionResult.value = ExtractionResultState(
                            outputFile = result.file,
                            pageCount = result.pageCount,
                            fileSizeBytes = result.fileSizeBytes
                        )
                        _selectedPdfFile.value = result.file
                        _userNotification.value = "Extracted ${result.pageCount} pages to ${result.file.name}"
                        onComplete?.invoke(result)
                    }
                    is PdfEngine.ExtractResult.Error -> {
                        _userNotification.value = result.message
                        onComplete?.invoke(result)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userNotification.value = "Extraction failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
