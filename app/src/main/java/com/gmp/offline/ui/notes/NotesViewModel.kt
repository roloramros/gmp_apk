package com.gmp.offline.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.notes.NoteDraftItem
import com.gmp.offline.data.notes.NoteEntity
import com.gmp.offline.data.notes.NoteWithItems
import com.gmp.offline.data.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = repository.observeMyNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(uuid: String): NoteWithItems? = repository.get(uuid)

    suspend fun save(uuid: String?, type: String, title: String, text: String, items: List<NoteDraftItem>): String? =
        repository.save(uuid, type, title, text, items)

    suspend fun delete(uuid: String) = repository.delete(uuid)
}
