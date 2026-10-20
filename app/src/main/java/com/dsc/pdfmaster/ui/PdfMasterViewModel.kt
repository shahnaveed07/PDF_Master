package com.dsc.pdfmaster.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.database.PdfDatabase
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.data.model.PdfTool
import com.dsc.pdfmaster.data.model.PdfToolType
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ScreenRoute {
    HOME,
    RECENT_FILES,
    PDF_VIEWER,
    CREATE_PDF,
    CONVERT_PDF,
    EXTRACT_PAGES,
    MERGE_PDF,
    SPLIT_PDF,
    COMPRESS_PDF,
    IMAGES_TO_PDF,
    MANAGE_PAGES,
    PASSWORD_PROTECT,
    REMOVE_PASSWORD,
    SETTINGS
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
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

    private var activeOperationJob: Job? = null

    private val _currentRoute = MutableStateFlow(ScreenRoute.HOME)
    val currentRoute: StateFlow<ScreenRoute> = _currentRoute.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        if (mode == ThemeMode.DARK) {
            _isDarkMode.value = true
        } else if (mode == ThemeMode.LIGHT) {
            _isDarkMode.value = false
        }
    }

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

    // Tracks whether _selectedPdfFile was deliberately chosen by the user (vs auto-created starter sample)
    private val _isPdfExplicitlySelected = MutableStateFlow(false)
    val isPdfExplicitlySelected: StateFlow<Boolean> = _isPdfExplicitlySelected.asStateFlow()

    // Dedicated Protect PDF state (never pre-populated with starter sample PDF)
    private val _protectSourcePdf = MutableStateFlow<File?>(null)
    val protectSourcePdf: StateFlow<File?> = _protectSourcePdf.asStateFlow()

    private val _protectPdfSecurityState = MutableStateFlow<PdfEngine.PdfSecurityState?>(null)
    val protectPdfSecurityState: StateFlow<PdfEngine.PdfSecurityState?> = _protectPdfSecurityState.asStateFlow()

    private val _protectPdfRequiresCurrentPassword = MutableStateFlow(false)
    val protectPdfRequiresCurrentPassword: StateFlow<Boolean> = _protectPdfRequiresCurrentPassword.asStateFlow()

    private val _protectPdfErrorMessage = MutableStateFlow<String?>(null)
    val protectPdfErrorMessage: StateFlow<String?> = _protectPdfErrorMessage.asStateFlow()

    private val _protectResult = MutableStateFlow<PdfEngine.ProtectResult.Success?>(null)
    val protectResult: StateFlow<PdfEngine.ProtectResult.Success?> = _protectResult.asStateFlow()

    // Unprotect (Remove Password) state
    private val _selectedPdfSecurityState = MutableStateFlow<PdfEngine.PdfSecurityState?>(null)
    val selectedPdfSecurityState: StateFlow<PdfEngine.PdfSecurityState?> = _selectedPdfSecurityState.asStateFlow()

    private val _unprotectErrorMessage = MutableStateFlow<String?>(null)
    val unprotectErrorMessage: StateFlow<String?> = _unprotectErrorMessage.asStateFlow()

    private val _unprotectResult = MutableStateFlow<PdfEngine.UnprotectResult.Success?>(null)
    val unprotectResult: StateFlow<PdfEngine.UnprotectResult.Success?> = _unprotectResult.asStateFlow()

    // Explicit Conversion Session States (never pre-populated with starter sample PDF)
    private val _conversionSourcePdf = MutableStateFlow<File?>(null)
    val conversionSourcePdf: StateFlow<File?> = _conversionSourcePdf.asStateFlow()

    private val _conversionPdfRequiresPassword = MutableStateFlow(false)
    val conversionPdfRequiresPassword: StateFlow<Boolean> = _conversionPdfRequiresPassword.asStateFlow()

    private val _conversionSourceImageUri = MutableStateFlow<Uri?>(null)
    val conversionSourceImageUri: StateFlow<Uri?> = _conversionSourceImageUri.asStateFlow()

    private val _conversionSourceImageName = MutableStateFlow<String?>(null)
    val conversionSourceImageName: StateFlow<String?> = _conversionSourceImageName.asStateFlow()

    private val _conversionSourceTextUri = MutableStateFlow<Uri?>(null)
    val conversionSourceTextUri: StateFlow<Uri?> = _conversionSourceTextUri.asStateFlow()

    private val _conversionSourceTextName = MutableStateFlow<String?>(null)
    val conversionSourceTextName: StateFlow<String?> = _conversionSourceTextName.asStateFlow()

    private val _conversionSourceTextPreview = MutableStateFlow<String?>(null)
    val conversionSourceTextPreview: StateFlow<String?> = _conversionSourceTextPreview.asStateFlow()

    private val _conversionSourceOfficeUri = MutableStateFlow<Uri?>(null)
    val conversionSourceOfficeUri: StateFlow<Uri?> = _conversionSourceOfficeUri.asStateFlow()

    private val _conversionSourceOfficeName = MutableStateFlow<String?>(null)
    val conversionSourceOfficeName: StateFlow<String?> = _conversionSourceOfficeName.asStateFlow()

    private val _conversionSourceOfficeFormat = MutableStateFlow<OfficeToPdfConverter.OfficeDocumentType?>(null)
    val conversionSourceOfficeFormat: StateFlow<OfficeToPdfConverter.OfficeDocumentType?> = _conversionSourceOfficeFormat.asStateFlow()

    private val _conversionSourceOfficeSummary = MutableStateFlow<String?>(null)
    val conversionSourceOfficeSummary: StateFlow<String?> = _conversionSourceOfficeSummary.asStateFlow()

    private val _conversionSourceOfficePreview = MutableStateFlow<String?>(null)
    val conversionSourceOfficePreview: StateFlow<String?> = _conversionSourceOfficePreview.asStateFlow()

    private val _conversionOutputFiles = MutableStateFlow<List<File>>(emptyList())
    val conversionOutputFiles: StateFlow<List<File>> = _conversionOutputFiles.asStateFlow()

    private val _conversionExtractedText = MutableStateFlow<String?>(null)
    val conversionExtractedText: StateFlow<String?> = _conversionExtractedText.asStateFlow()

    private val _conversionResultSummary = MutableStateFlow<String?>(null)
    val conversionResultSummary: StateFlow<String?> = _conversionResultSummary.asStateFlow()

    private val _conversionLayoutDisclosure = MutableStateFlow<String?>(null)
    val conversionLayoutDisclosure: StateFlow<String?> = _conversionLayoutDisclosure.asStateFlow()

    private val _conversionErrorMessage = MutableStateFlow<String?>(null)
    val conversionErrorMessage: StateFlow<String?> = _conversionErrorMessage.asStateFlow()

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
            val sample = repository.ensureStarterSamplePdf()
            if (_selectedPdfFile.value == null) {
                _selectedPdfFile.value = sample
                _isPdfExplicitlySelected.value = false
            }
        }
    }

    fun isStarterSamplePdf(file: File?): Boolean {
        return file?.name == "Welcome_PDF_Master.pdf" || file?.name == "PDF_Master_Welcome_Guide.pdf"
    }

    fun navigateTo(route: ScreenRoute) {
        _isProcessing.value = false
        val previousRoute = _currentRoute.value
        if (previousRoute == route) return

        if (route == ScreenRoute.CONVERT_PDF) {
            resetConversionSession()
        }
        if (route == ScreenRoute.REMOVE_PASSWORD) {
            _unprotectErrorMessage.value = null
            _unprotectResult.value = null
            refreshSelectedPdfSecurityState()
        }
        if (route == ScreenRoute.PASSWORD_PROTECT) {
            _protectPdfErrorMessage.value = null
            _protectResult.value = null
            val currentFile = _selectedPdfFile.value
            if (previousRoute == ScreenRoute.PDF_VIEWER && currentFile != null) {
                selectProtectSourcePdf(currentFile)
            } else if (_isPdfExplicitlySelected.value && currentFile != null) {
                selectProtectSourcePdf(currentFile)
            } else if (_protectSourcePdf.value == null) {
                _protectSourcePdf.value = null
                _protectPdfSecurityState.value = null
                _protectPdfRequiresCurrentPassword.value = false
            }
        }

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
        activeOperationJob?.cancel()
        activeOperationJob = null
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

    fun showNotification(message: String) {
        _userNotification.value = message
    }

    fun clearConversionState() {
        _conversionErrorMessage.value = null
        _conversionOutputFiles.value = emptyList()
        _conversionExtractedText.value = null
        _conversionResultSummary.value = null
        _conversionLayoutDisclosure.value = null
    }

    fun clearUnprotectFeedback() {
        _unprotectErrorMessage.value = null
        _unprotectResult.value = null
    }

    fun refreshSelectedPdfSecurityState(fileOverride: File? = null) {
        val target = fileOverride ?: _selectedPdfFile.value
        if (target == null) {
            _selectedPdfSecurityState.value = null
            return
        }
        viewModelScope.launch {
            _selectedPdfSecurityState.value = PdfEngine.inspectPdfSecurity(getApplication(), target)
        }
    }

    fun openPdfInViewer(file: File) {
        _selectedPdfFile.value = file
        _isPdfExplicitlySelected.value = true
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
                _isPdfExplicitlySelected.value = true
                _unprotectErrorMessage.value = null
                _unprotectResult.value = null
                _selectedPdfSecurityState.value = PdfEngine.inspectPdfSecurity(getApplication(), file)
                repository.registerPdfFile(file)
                if (openViewer) {
                    navigateTo(ScreenRoute.PDF_VIEWER)
                }
            } else {
                _userNotification.value = "Selected file is not a valid PDF document"
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
                _isPdfExplicitlySelected.value = false
            }
            if (_protectSourcePdf.value?.absolutePath == filePath) {
                _protectSourcePdf.value = null
            }
        }
    }

    fun removePdfFromRecent(filePath: String) {
        viewModelScope.launch {
            repository.removeFromRecent(filePath)
            _userNotification.value = "Removed from Recent Documents"
            if (_selectedPdfFile.value?.absolutePath == filePath) {
                _selectedPdfFile.value = null
                _isPdfExplicitlySelected.value = false
            }
        }
    }

    fun renamePdf(file: File, newName: String) {
        viewModelScope.launch {
            val renamed = repository.renamePdf(file, newName)
            if (renamed != null) {
                _selectedPdfFile.value = renamed
                _isPdfExplicitlySelected.value = true
                _userNotification.value = "Renamed to ${renamed.name}"
            } else {
                _userNotification.value = "Failed to rename file"
            }
        }
    }

    fun setSelectedPdfFile(file: File) {
        _selectedPdfFile.value = file
        _isPdfExplicitlySelected.value = true
        _unprotectErrorMessage.value = null
        _unprotectResult.value = null
        refreshSelectedPdfSecurityState(file)
        viewModelScope.launch {
            repository.registerPdfFile(file)
        }
    }

    // Launch tool action from Dashboard
    fun launchTool(tool: PdfTool) {
        when (tool.type) {
            PdfToolType.EXTRACT_PAGES -> navigateTo(ScreenRoute.EXTRACT_PAGES)
            PdfToolType.MERGE -> navigateTo(ScreenRoute.MERGE_PDF)
            PdfToolType.SPLIT -> navigateTo(ScreenRoute.SPLIT_PDF)
            PdfToolType.COMPRESS -> navigateTo(ScreenRoute.COMPRESS_PDF)
            PdfToolType.IMAGES_TO_PDF -> navigateTo(ScreenRoute.IMAGES_TO_PDF)
            PdfToolType.VIEW_PDF -> navigateTo(ScreenRoute.PDF_VIEWER)
            PdfToolType.MANAGE_PAGES -> {
                if (_selectedPdfFile.value != null) {
                    loadPagesForPdf(_selectedPdfFile.value!!)
                }
                navigateTo(ScreenRoute.MANAGE_PAGES)
            }
            PdfToolType.PASSWORD_PROTECT -> navigateTo(ScreenRoute.PASSWORD_PROTECT)
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
        if (_isProcessing.value) return
        val files = _selectedFilesForMerge.value
        if (files.size < 2) {
            _userNotification.value = "Please select at least 2 PDF files to merge"
            return
        }
        activeOperationJob = viewModelScope.launch {
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
                    _userNotification.value = "Merge failed. Please verify the selected PDFs."
                }
            } catch (e: CancellationException) {
                _userNotification.value = "Merge cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Merge failed due to an error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Text to PDF
    fun executeCreateTextPdf(textContent: String, outputName: String) {
        if (_isProcessing.value || textContent.isBlank()) return
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Generating Text PDF..."
            try {
                val result = repository.createTextPdf(textContent, outputName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _userNotification.value = "Text PDF created: ${result.name}"
                    navigateTo(ScreenRoute.PDF_VIEWER)
                } else {
                    _userNotification.value = "Failed to create Text PDF"
                }
            } catch (e: CancellationException) {
                _userNotification.value = "Operation cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Failed to create Text PDF"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun addImagesForPdf(uris: List<Uri>) {
        _selectedImageUris.value = (_selectedImageUris.value + uris).distinct()
    }

    fun removeImageForPdf(uri: Uri) {
        _selectedImageUris.value = _selectedImageUris.value.filter { it != uri }
    }

    fun clearSelectedImagesForPdf() {
        _selectedImageUris.value = emptyList()
    }

    fun executeImagesToPdf(outputFileName: String) {
        if (_isProcessing.value) return
        val uris = _selectedImageUris.value
        if (uris.isEmpty()) {
            _userNotification.value = "Please select at least 1 image"
            return
        }
        activeOperationJob = viewModelScope.launch {
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
                    _userNotification.value = "Conversion failed. Check that all selected files are valid images."
                }
            } catch (e: CancellationException) {
                _userNotification.value = "Image to PDF conversion cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Conversion failed due to an error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Compress PDF
    fun executeCompressPdf(file: File, level: PdfCompressionLevel, outputName: String) {
        if (_isProcessing.value) return
        activeOperationJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                _userNotification.value = "Compression cancelled"
            } catch (e: Exception) {
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
            } catch (_: Exception) {
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
        if (_isProcessing.value) return
        val currentPages = _pdfPagesList.value
        val remainingPages = currentPages.filter { !it.isSelected }
        if (remainingPages.isEmpty()) {
            _userNotification.value = "Cannot delete all pages from PDF!"
            return
        }
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Saving updated PDF structure..."
            try {
                val result = repository.organizePages(file, currentPages, outputName)
                if (result != null) {
                    _selectedPdfFile.value = result
                    _userNotification.value = "Pages saved successfully: ${result.name}"
                    _currentRoute.value = ScreenRoute.PDF_VIEWER
                } else {
                    _userNotification.value = "Failed to organize pages."
                }
            } catch (e: CancellationException) {
                _userNotification.value = "Operation cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Failed to organize pages."
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
        if (_isProcessing.value) return
        if (pageIndices.isEmpty()) {
            _userNotification.value = "Please select valid page numbers to split"
            return
        }
        activeOperationJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                _userNotification.value = "Split cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Failed to split PDF."
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Password Protect PDF
    fun clearProtectFeedback() {
        _protectPdfErrorMessage.value = null
        _protectResult.value = null
    }

    fun selectProtectSourcePdf(file: File?) {
        _protectPdfErrorMessage.value = null
        _protectResult.value = null
        if (file == null) {
            _protectSourcePdf.value = null
            _protectPdfSecurityState.value = null
            _protectPdfRequiresCurrentPassword.value = false
            return
        }
        viewModelScope.launch {
            if (!file.exists() || file.length() == 0L || !PdfEngine.hasPdfHeader(file)) {
                _protectSourcePdf.value = null
                _protectPdfSecurityState.value = PdfEngine.PdfSecurityState.InvalidOrCorrupted("Selected file is not a valid PDF document.")
                _protectPdfErrorMessage.value = "Selected file is not a valid PDF document."
                return@launch
            }
            _protectSourcePdf.value = file
            val secState = PdfEngine.inspectPdfSecurity(getApplication(), file)
            _protectPdfSecurityState.value = secState
            _protectPdfRequiresCurrentPassword.value =
                secState is PdfEngine.PdfSecurityState.UserPasswordRequired ||
                    secState is PdfEngine.PdfSecurityState.OwnerPasswordRestricted
            when (secState) {
                is PdfEngine.PdfSecurityState.InvalidOrCorrupted -> {
                    _protectPdfErrorMessage.value = secState.message
                }
                is PdfEngine.PdfSecurityState.UnsupportedEncryption -> {
                    _protectPdfErrorMessage.value = secState.message
                }
                else -> {}
            }
        }
    }

    fun selectProtectSourcePdfFromUri(uri: Uri) {
        viewModelScope.launch {
            _protectPdfErrorMessage.value = null
            _protectResult.value = null
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null && file.exists() && PdfEngine.hasPdfHeader(file)) {
                _selectedPdfFile.value = file
                _isPdfExplicitlySelected.value = true
                repository.registerPdfFile(file)
                selectProtectSourcePdf(file)
            } else {
                _protectSourcePdf.value = null
                _protectPdfSecurityState.value = PdfEngine.PdfSecurityState.InvalidOrCorrupted("Selected file is not a valid PDF document.")
                val msg = "Selected file is not a valid PDF document."
                _protectPdfErrorMessage.value = msg
                _userNotification.value = msg
            }
        }
    }

    fun executeProtectPdf(
        file: File? = _protectSourcePdf.value,
        password: String,
        outputName: String = "",
        confirmPassword: String = password,
        currentPassword: String? = null,
        ownerPassword: String? = null,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true,
        replaceOriginal: Boolean = false
    ) {
        if (_isProcessing.value) return
        val targetFile = file ?: _protectSourcePdf.value
        if (targetFile == null || (_protectSourcePdf.value == null && !_isPdfExplicitlySelected.value)) {
            val msg = "Please select a PDF document to protect."
            _protectPdfErrorMessage.value = msg
            _userNotification.value = msg
            return
        }
        if (password.isBlank()) {
            val msg = "Password cannot be empty."
            _protectPdfErrorMessage.value = msg
            _userNotification.value = msg
            return
        }
        if (password != confirmPassword) {
            val msg = "Passwords do not match. Please confirm your password."
            _protectPdfErrorMessage.value = msg
            _userNotification.value = msg
            return
        }

        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _protectPdfErrorMessage.value = null
            _protectResult.value = null
            _processingMessage.value = "Applying 256-bit AES encryption & verifying..."
            try {
                val desiredOutputName = outputName.trim().ifBlank {
                    "${targetFile.nameWithoutExtension}_protected.pdf"
                }
                val result = repository.protectPdfDetailed(
                    pdfFile = targetFile,
                    password = password,
                    confirmPassword = confirmPassword,
                    ownerPassword = ownerPassword,
                    currentPassword = currentPassword,
                    allowPrinting = allowPrinting,
                    allowCopy = allowCopy,
                    outputName = desiredOutputName,
                    replaceOriginal = replaceOriginal
                )
                when (result) {
                    is PdfEngine.ProtectResult.Success -> {
                        _protectResult.value = result
                        if (replaceOriginal) {
                            _protectSourcePdf.value = result.file
                            _protectPdfSecurityState.value = PdfEngine.PdfSecurityState.UserPasswordRequired
                            _protectPdfRequiresCurrentPassword.value = true
                        }
                        _userNotification.value = if (result.replacedOriginal) {
                            "Original PDF protected in-place: ${result.file.name}"
                        } else {
                            "Document encrypted as ${result.file.name}!"
                        }
                    }
                    is PdfEngine.ProtectResult.Error -> {
                        if (result.error == PdfEngine.ProtectError.ALREADY_ENCRYPTED ||
                            result.error == PdfEngine.ProtectError.OWNER_PASSWORD_REQUIRED ||
                            result.error == PdfEngine.ProtectError.INCORRECT_CURRENT_PASSWORD
                        ) {
                            _protectPdfRequiresCurrentPassword.value = true
                        }
                        _protectPdfErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _protectPdfErrorMessage.value = "Encryption cancelled. Original file is unchanged."
                _userNotification.value = "Encryption cancelled"
            } catch (e: Exception) {
                val msg = "Failed to encrypt PDF: ${e.localizedMessage ?: "Unexpected error"}"
                _protectPdfErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Removes password protection from [file] and saves to a validated output file.
     * - Prevents duplicate submissions during processing.
     * - Supports blank password only when the PDF security configuration genuinely allows it.
     * - Defaults to Save as New ([replaceOriginal] = false).
     * - Never clears or replaces [selectedPdfFile] on failure.
     */
    fun executeRemovePassword(
        file: File,
        password: String,
        outputName: String,
        replaceOriginal: Boolean = false
    ) {
        if (_isProcessing.value) return
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _unprotectErrorMessage.value = null
            _unprotectResult.value = null
            _processingMessage.value = "Verifying credentials & removing PDF password..."
            try {
                val result = repository.removePasswordDetailed(
                    pdfFile = file,
                    password = password,
                    outputName = outputName,
                    replaceOriginal = replaceOriginal
                )
                when (result) {
                    is PdfEngine.UnprotectResult.Success -> {
                        _selectedPdfFile.value = result.file
                        _isPdfExplicitlySelected.value = true
                        _unprotectResult.value = result
                        _selectedPdfSecurityState.value = PdfEngine.PdfSecurityState.Unencrypted(result.pageCount)
                        _userNotification.value = "Password removed: ${result.file.name}"
                        navigateTo(ScreenRoute.PDF_VIEWER)
                    }
                    is PdfEngine.UnprotectResult.Error -> {
                        _unprotectErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _unprotectErrorMessage.value = "Operation was cancelled. Original file is unchanged."
                _userNotification.value = "Password removal cancelled"
            } catch (e: Exception) {
                val msg = "Failed to remove password: ${e.localizedMessage ?: "Unexpected error"}"
                _unprotectErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Conversion Session Management
    fun resetConversionSession() {
        _conversionSourcePdf.value = null
        _conversionPdfRequiresPassword.value = false
        _conversionSourceImageUri.value = null
        _conversionSourceImageName.value = null
        _conversionSourceTextUri.value = null
        _conversionSourceTextName.value = null
        _conversionSourceTextPreview.value = null
        _conversionSourceOfficeUri.value = null
        _conversionSourceOfficeName.value = null
        _conversionSourceOfficeFormat.value = null
        _conversionSourceOfficeSummary.value = null
        _conversionSourceOfficePreview.value = null
        _conversionOutputFiles.value = emptyList()
        _conversionExtractedText.value = null
        _conversionResultSummary.value = null
        _conversionLayoutDisclosure.value = null
        _conversionErrorMessage.value = null
    }

    fun selectConversionPdfFromUri(uri: Uri) {
        viewModelScope.launch {
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _conversionExtractedText.value = null
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null && file.exists() && PdfEngine.hasPdfHeader(file)) {
                _conversionSourcePdf.value = file
                _conversionPdfRequiresPassword.value = PdfEngine.checkPasswordProtected(getApplication(), file)
                repository.registerPdfFile(file)
            } else {
                _conversionSourcePdf.value = null
                _conversionErrorMessage.value = "The selected file is not a valid PDF document."
                _userNotification.value = "The selected file is not a valid PDF document."
            }
        }
    }

    fun setConversionSourcePdf(file: File?) {
        _conversionOutputFiles.value = emptyList()
        _conversionExtractedText.value = null
        _conversionErrorMessage.value = null
        if (file == null) {
            _conversionSourcePdf.value = null
            _conversionPdfRequiresPassword.value = false
            return
        }
        viewModelScope.launch {
            if (file.exists() && file.length() > 0 && PdfEngine.hasPdfHeader(file)) {
                _conversionSourcePdf.value = file
                _conversionPdfRequiresPassword.value = PdfEngine.checkPasswordProtected(getApplication(), file)
            } else {
                _conversionSourcePdf.value = null
                _conversionErrorMessage.value = "Selected file is not a valid PDF document."
            }
        }
    }

    private fun resolveDisplayName(uri: Uri, defaultFallback: String): String {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name } ?: defaultFallback
        }
        return try {
            val cursor = getApplication<Application>().contentResolver.query(
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
        } catch (e: Exception) {
            defaultFallback
        }
    }

    fun selectConversionImageFromUri(uri: Uri) {
        viewModelScope.launch {
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            val isValid = withContext(Dispatchers.IO) {
                try {
                    if (!PdfEngine.hasSupportedImageHeader(getApplication(), uri)) {
                        return@withContext false
                    }
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val stream = if (uri.scheme == "file") {
                        uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
                    } else {
                        getApplication<Application>().contentResolver.openInputStream(uri)
                    }
                    stream?.use { BitmapFactory.decodeStream(it, null, opts) }
                    opts.outWidth > 0 && opts.outHeight > 0
                } catch (e: Exception) {
                    false
                }
            }
            if (isValid) {
                _conversionSourceImageUri.value = uri
                _conversionSourceImageName.value = resolveDisplayName(uri, "selected_image")
            } else {
                _conversionSourceImageUri.value = null
                _conversionSourceImageName.value = null
                _conversionErrorMessage.value = "Selected file is not a valid image."
                _userNotification.value = "Selected file is not a valid image."
            }
        }
    }

    fun selectConversionTextFileFromUri(uri: Uri) {
        viewModelScope.launch {
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            val text = withContext(Dispatchers.IO) {
                PdfEngine.readValidatedPlainTextFromUri(getApplication(), uri)
            }
            if (!text.isNullOrBlank()) {
                _conversionSourceTextUri.value = uri
                _conversionSourceTextName.value = resolveDisplayName(uri, "document.txt")
                _conversionSourceTextPreview.value = text.take(500)
            } else {
                _conversionSourceTextUri.value = null
                _conversionSourceTextName.value = null
                _conversionSourceTextPreview.value = null
                _conversionErrorMessage.value = "Selected file is empty or is not a valid plain-text file."
                _userNotification.value = "Selected file is empty or is not a valid plain-text file."
            }
        }
    }

    fun executePdfToImages(
        file: File,
        password: String? = null,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        outputPrefix: String = file.nameWithoutExtension
    ) {
        if (_isProcessing.value) return
        val formatLabel = if (format == Bitmap.CompressFormat.PNG) "PNG" else "JPEG"
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _processingMessage.value = "Rendering PDF pages to $formatLabel images..."
            try {
                val result = repository.convertPdfToImagesDetailed(
                    pdfFile = file,
                    format = format,
                    password = password,
                    outputPrefix = outputPrefix
                )
                when (result) {
                    is PdfEngine.PdfToImagesResult.Success -> {
                        _conversionOutputFiles.value = result.files
                        _userNotification.value = "Exported ${result.files.size} $formatLabel image(s) (${result.files.first().name})"
                    }
                    is PdfEngine.PdfToImagesResult.Error -> {
                        if (result.isPasswordProtected) {
                            _conversionPdfRequiresPassword.value = true
                        }
                        _conversionErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _conversionErrorMessage.value = "Conversion cancelled."
                _userNotification.value = "PDF to $formatLabel conversion cancelled"
            } catch (e: Exception) {
                val msg = "Failed to convert PDF pages to $formatLabel images"
                _conversionErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executePdfToText(
        file: File,
        password: String? = null,
        outputName: String = "${file.nameWithoutExtension}_text.txt"
    ) {
        if (_isProcessing.value) return
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _conversionExtractedText.value = null
            _processingMessage.value = "Extracting text content from PDF..."
            try {
                val result = repository.extractTextFromPdfDetailed(
                    pdfFile = file,
                    password = password,
                    outputName = outputName
                )
                when (result) {
                    is PdfEngine.TextExtractResult.Success -> {
                        _conversionOutputFiles.value = listOf(result.file)
                        _conversionExtractedText.value = result.extractedText
                        _userNotification.value = "Text extracted to ${result.file.name}"
                    }
                    is PdfEngine.TextExtractResult.Error -> {
                        if (result.isPasswordProtected) {
                            _conversionPdfRequiresPassword.value = true
                        }
                        _conversionErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _conversionErrorMessage.value = "Text extraction cancelled."
                _userNotification.value = "Text extraction cancelled"
            } catch (e: Exception) {
                val msg = "Failed to extract text from PDF"
                _conversionErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeConvertImageFormat(
        uri: Uri,
        isToJpeg: Boolean,
        outputName: String? = null
    ) {
        if (_isProcessing.value) return
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            val targetFormat = if (isToJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val ext = if (isToJpeg) "jpg" else "png"
            val baseName = outputName?.trim()?.takeIf { it.isNotBlank() }
                ?: "${(_conversionSourceImageName.value ?: "Converted_Image").substringBeforeLast('.')}_converted.$ext"
            _processingMessage.value = "Converting image to ${ext.uppercase()}..."
            try {
                val result = repository.convertImageFormatDetailed(uri, targetFormat, baseName)
                when (result) {
                    is PdfEngine.ImageConversionResult.Success -> {
                        _conversionOutputFiles.value = listOf(result.file)
                        _userNotification.value = "Image converted to ${result.file.name} (${result.width}x${result.height})"
                    }
                    is PdfEngine.ImageConversionResult.Error -> {
                        _conversionErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _conversionErrorMessage.value = "Image conversion cancelled."
                _userNotification.value = "Image conversion cancelled"
            } catch (e: Exception) {
                val msg = "Failed to convert image format"
                _conversionErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeConvertTextFileToPdf(uri: Uri, outputName: String) {
        if (_isProcessing.value) return
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _processingMessage.value = "Converting TXT file to PDF..."
            try {
                val desiredName = outputName.trim().ifBlank {
                    "${(_conversionSourceTextName.value ?: "Text_Document").substringBeforeLast('.')}.pdf"
                }
                val pdfFile = repository.convertTextFileToPdf(uri, desiredName)
                if (pdfFile != null) {
                    _conversionOutputFiles.value = listOf(pdfFile)
                    _selectedPdfFile.value = pdfFile
                    _isPdfExplicitlySelected.value = true
                    _userNotification.value = "TXT converted to PDF: ${pdfFile.name}"
                } else {
                    val msg = "Failed to convert TXT file to PDF. Ensure the file contains valid text."
                    _conversionErrorMessage.value = msg
                    _userNotification.value = msg
                }
            } catch (e: CancellationException) {
                _conversionErrorMessage.value = "TXT to PDF conversion cancelled."
                _userNotification.value = "TXT to PDF conversion cancelled"
            } catch (e: Exception) {
                val msg = "Failed to convert TXT file to PDF"
                _conversionErrorMessage.value = msg
                _userNotification.value = msg
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun selectConversionOfficeFileFromUri(
        uri: Uri,
        expectedType: OfficeToPdfConverter.OfficeDocumentType? = null
    ) {
        val targetType = expectedType ?: OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Inspecting ${targetType.label} document..."
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _conversionResultSummary.value = null
            _conversionLayoutDisclosure.value = null
            try {
                val displayName = resolveDisplayName(
                    uri,
                    when (targetType) {
                        OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX -> "document.docx"
                        OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX -> "spreadsheet.xlsx"
                        OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX -> "presentation.pptx"
                    }
                )
                val inspection = repository.inspectOfficeSource(uri, targetType)
                if (inspection.isValid) {
                    _conversionSourceOfficeUri.value = uri
                    _conversionSourceOfficeName.value = displayName
                    _conversionSourceOfficeFormat.value = inspection.detectedType ?: targetType
                    _conversionSourceOfficeSummary.value = inspection.previewSummary
                    _conversionSourceOfficePreview.value = inspection.previewSummary
                } else {
                    val errMsg = inspection.errorMessage ?: "Selected file is not a valid ${targetType.label} document."
                    _conversionSourceOfficeUri.value = null
                    _conversionSourceOfficeName.value = null
                    _conversionSourceOfficeFormat.value = null
                    _conversionSourceOfficeSummary.value = null
                    _conversionSourceOfficePreview.value = null
                    _conversionErrorMessage.value = errMsg
                    _userNotification.value = errMsg
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeConvertOfficeToPdf(
        uri: Uri,
        expectedType: OfficeToPdfConverter.OfficeDocumentType? = _conversionSourceOfficeFormat.value,
        outputName: String
    ) {
        if (_isProcessing.value) return
        val targetType = expectedType
            ?: _conversionSourceOfficeFormat.value
            ?: OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX
        activeOperationJob = viewModelScope.launch {
            _isProcessing.value = true
            _conversionErrorMessage.value = null
            _conversionOutputFiles.value = emptyList()
            _conversionResultSummary.value = null
            _conversionLayoutDisclosure.value = null
            val formatLabel = targetType.label
            _processingMessage.value = "Converting $formatLabel to PDF..."
            try {
                val fallbackBase = (_conversionSourceOfficeName.value ?: "Office_Document").substringBeforeLast('.')
                val desiredName = outputName.trim().ifBlank { "$fallbackBase.pdf" }
                val result = repository.convertOfficeToPdfDetailed(
                    uri = uri,
                    expectedType = targetType,
                    outputName = desiredName
                )
                when (result) {
                    is OfficeToPdfConverter.OfficeConversionResult.Success -> {
                        _conversionOutputFiles.value = listOf(result.file)
                        _conversionResultSummary.value = "${result.pageCount} PDF page(s) • ${result.summary}"
                        _conversionLayoutDisclosure.value = result.layoutDisclosure
                        _selectedPdfFile.value = result.file
                        _isPdfExplicitlySelected.value = true
                        _userNotification.value = "Converted $formatLabel to ${result.file.name} (${result.pageCount} pages)"
                    }
                    is OfficeToPdfConverter.OfficeConversionResult.Error -> {
                        _conversionErrorMessage.value = result.message
                        _userNotification.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                _conversionErrorMessage.value = "Office conversion cancelled."
                _userNotification.value = "Office conversion cancelled"
            } catch (e: Exception) {
                val msg = "Failed to convert $formatLabel to PDF"
                _conversionErrorMessage.value = msg
                _userNotification.value = msg
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
        if (_isProcessing.value) return
        if (pageIndices.isEmpty()) {
            _userNotification.value = "Please select at least 1 page to extract"
            return
        }

        activeOperationJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                _userNotification.value = "Extraction cancelled"
            } catch (e: Exception) {
                _userNotification.value = "Extraction failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
