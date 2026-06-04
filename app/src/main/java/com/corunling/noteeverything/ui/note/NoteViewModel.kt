// ============================================================
// NoteViewModel.kt — 笔记/随笔的 ViewModel
// ============================================================

package com.corunling.noteeverything.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(
    private val repository: NoteEverythingRepository
) : ViewModel() {

    val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNote(
        softwareId: Long?,
        content: String,
        timestamp: Long,
        tags: String? = null,
        imageUri: String? = null,
        location: String? = null
    ) {
        viewModelScope.launch {
            repository.createNote(softwareId, content, timestamp, tags, imageUri, location)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch { repository.updateNote(note) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    class Factory(private val repository: NoteEverythingRepository) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteViewModel(repository) as T
        }
    }
}
