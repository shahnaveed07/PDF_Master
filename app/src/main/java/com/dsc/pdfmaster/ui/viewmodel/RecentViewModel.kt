package com.dsc.pdfmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RecentPdfItemUiState(
    val entity: RecentPdfEntity,
    val isAvailable: Boolean
)

data class RecentUiState(
    val recentItems: List<RecentPdfItemUiState> = emptyList(),
    val favoriteItems: List<RecentPdfItemUiState> = emptyList(),
    val pinnedOrFavoriteItems: List<RecentPdfItemUiState> = emptyList(),
    val searchQuery: String = "",
    val error: PdfOperationError? = null
)

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _error = MutableStateFlow<PdfOperationError?>(null)

    val uiState: StateFlow<RecentUiState> = combine(
        repository.allRecentPdfs,
        repository.favoritePdfs,
        repository.pinnedOrFavoritePdfs,
        _searchQuery,
        _error
    ) { recentList, favList, pinnedOrFavList, query, err ->
        val filteredRecent = if (query.isBlank()) {
            recentList
        } else {
            recentList.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        val filteredPinnedOrFav = if (query.isBlank()) {
            pinnedOrFavList
        } else {
            pinnedOrFavList.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        RecentUiState(
            recentItems = filteredRecent.map { pdf ->
                RecentPdfItemUiState(
                    entity = pdf,
                    isAvailable = repository.isPdfAvailable(pdf)
                )
            },
            favoriteItems = favList.map { pdf ->
                RecentPdfItemUiState(
                    entity = pdf,
                    isAvailable = repository.isPdfAvailable(pdf)
                )
            },
            pinnedOrFavoriteItems = filteredPinnedOrFav.map { pdf ->
                RecentPdfItemUiState(
                    entity = pdf,
                    isAvailable = repository.isPdfAvailable(pdf)
                )
            },
            searchQuery = query,
            error = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecentUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun isPdfAvailable(pdf: RecentPdfEntity): Boolean {
        return repository.isPdfAvailable(pdf)
    }

    fun toggleFavorite(filePath: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(filePath, !currentStatus)
        }
    }

    fun togglePinned(filePath: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.updatePinned(filePath, !currentStatus)
        }
    }

    fun removeStaleOrRecentEntry(filePath: String) {
        viewModelScope.launch {
            repository.removeFromRecent(filePath)
        }
    }

    fun deletePdf(filePath: String) {
        viewModelScope.launch {
            val res = repository.deletePdf(filePath)
            if (res is PdfRepository.DeleteResult.Failed) {
                _error.value = PdfOperationError.Unknown(res.message)
            } else if (res is PdfRepository.DeleteResult.PermissionDenied) {
                _error.value = PdfOperationError.RevokedPermission()
            }
        }
    }

    fun renamePdf(file: File, newName: String) {
        viewModelScope.launch {
            repository.renamePdf(file, newName)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
