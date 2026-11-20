package com.agrovision.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.repository.FeedbackRepository
import com.agrovision.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryListState(
    val histories: List<HistoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class HistoryDetailState(
    val history: HistoryEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val feedbackSubmitted: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(HistoryListState())
    val listState: StateFlow<HistoryListState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(HistoryDetailState())
    val detailState: StateFlow<HistoryDetailState> = _detailState.asStateFlow()

    init {
        loadHistoryList()
    }

    /** 保存识别反馈（正确/不正确），用于模型迭代 */
    fun saveFeedback(historyId: Long, isCorrect: Boolean) {
        if (_detailState.value.feedbackSubmitted) return
        viewModelScope.launch {
            try {
                feedbackRepository.saveFeedback(historyId, isCorrect)
                _detailState.value = _detailState.value.copy(feedbackSubmitted = true)
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(error = e.message)
            }
        }
    }

    private fun loadHistoryList() {
        viewModelScope.launch {
            historyRepository.getAllHistory()
                .catch { e ->
                    _listState.value = HistoryListState(isLoading = false, error = e.message)
                }
                .collect { list ->
                    _listState.value = HistoryListState(histories = list, isLoading = false)
                }
        }
    }

    fun loadDetail(historyId: Long) {
        viewModelScope.launch {
            _detailState.value = HistoryDetailState(isLoading = true)
            try {
                val entity = historyRepository.getById(historyId)
                _detailState.value = HistoryDetailState(history = entity, isLoading = false)
            } catch (e: Exception) {
                _detailState.value = HistoryDetailState(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch {
            try {
                historyRepository.deleteById(id)
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(error = e.message)
            }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            try {
                historyRepository.toggleFavorite(id)
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(error = e.message)
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            historyRepository.searchHistory(query)
                .catch { e ->
                    _listState.value = HistoryListState(isLoading = false, error = e.message)
                }
                .collect { list ->
                    _listState.value = HistoryListState(histories = list, isLoading = false)
                }
        }
    }

    fun clearError() {
        _listState.value = _listState.value.copy(error = null)
        _detailState.value = _detailState.value.copy(error = null)
    }
}
