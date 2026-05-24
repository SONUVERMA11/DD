package com.sonu.dd.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.LibraryDao
import com.sonu.dd.core.data.db.LibraryItemEntity
import com.sonu.dd.core.domain.model.FileCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val preferences: DDPreferences,
) : ViewModel() {
    val allItems = libraryDao.getAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalSize = libraryDao.getTotalSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val libraryView = preferences.libraryViewFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedTab = MutableStateFlow("All")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    val filteredItems: StateFlow<List<LibraryItemEntity>> = combine(allItems, _selectedTab) { items, tab ->
        when (tab) {
            "All" -> items
            "Videos" -> items.filter { it.category == FileCategory.VIDEO.name }
            "Music" -> items.filter { it.category == FileCategory.AUDIO.name }
            "Books" -> items.filter { it.category == FileCategory.BOOK.name }
            else -> items.filter { it.category == FileCategory.OTHER.name || it.category == FileCategory.ARCHIVE.name || it.category == FileCategory.IMAGE.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: String) { _selectedTab.value = tab }
    fun toggleView() { viewModelScope.launch { val current = libraryView.value; preferences.setLibraryView(if (current == 0) 1 else 0) } }
    fun deleteItem(id: String) { viewModelScope.launch { libraryDao.deleteById(id) } }
}
