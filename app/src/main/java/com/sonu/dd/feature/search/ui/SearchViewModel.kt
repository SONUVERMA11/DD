package com.sonu.dd.feature.search.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonu.dd.core.data.db.SearchHistoryDao
import com.sonu.dd.core.domain.model.TorrentResult
import com.sonu.dd.feature.search.data.TorrentSearchAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<TorrentResult> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchAggregator: TorrentSearchAggregator,
    private val searchHistoryDao: SearchHistoryDao,
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches = searchHistoryDao.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun search(query: String = _uiState.value.query, category: String = "") {
        if (query.isBlank()) return

        // Prevent duplicate searches for the same query
        val currentState = _uiState.value
        if (currentState.query == query && currentState.hasSearched && currentState.results.isNotEmpty()) {
            Log.d(TAG, "Skipping duplicate search for '$query'")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Starting search for '$query'")
            _uiState.value = _uiState.value.copy(
                query = query,
                isLoading = true,
                error = null,
                hasSearched = true
            )

            try {
                val results = withContext(Dispatchers.IO) {
                    searchAggregator.search(query, category)
                }
                Log.d(TAG, "Search returned ${results.size} results for '$query'")
                _uiState.value = _uiState.value.copy(
                    results = results,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query'", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearch(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
        }
    }
}
