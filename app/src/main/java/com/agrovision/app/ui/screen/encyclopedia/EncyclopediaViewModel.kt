package com.agrovision.app.ui.screen.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.domain.model.PestInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EncyclopediaUiState(
    val pests: List<PestInfo> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EncyclopediaViewModel @Inject constructor(
    private val encyclopediaRepository: EncyclopediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EncyclopediaUiState())
    val uiState: StateFlow<EncyclopediaUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = EncyclopediaUiState(isLoading = true)
            try {
                val pests = encyclopediaRepository.getAllPests()
                val categories = encyclopediaRepository.getCategories()
                _uiState.value = EncyclopediaUiState(
                    pests = pests,
                    categories = categories,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = EncyclopediaUiState(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadAll()
                return@launch
            }
            try {
                val results = encyclopediaRepository.search(query)
                _uiState.value = _uiState.value.copy(pests = results, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
