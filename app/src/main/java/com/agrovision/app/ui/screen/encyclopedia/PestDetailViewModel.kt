package com.agrovision.app.ui.screen.encyclopedia

import androidx.lifecycle.SavedStateHandle
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

/**
 * 病虫详情 ViewModel — 从百科仓库加载真实数据
 */
@HiltViewModel
class PestDetailViewModel @Inject constructor(
    private val encyclopediaRepository: EncyclopediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class PestDetailUiState(
        val pestInfo: PestInfo? = null,
        val isLoading: Boolean = true,
        val pestName: String = ""
    )

    private val _uiState = MutableStateFlow(PestDetailUiState())
    val uiState: StateFlow<PestDetailUiState> = _uiState.asStateFlow()

    init {
        val name = savedStateHandle.get<String>("pestName") ?: ""
        loadPest(name)
    }

    private fun loadPest(name: String) {
        viewModelScope.launch {
            // 先按中文名查（检测页传的是中文名），再按英文名查
            val info = encyclopediaRepository.getByChineseName(name)
                ?: encyclopediaRepository.getByEnglishName(name)
            _uiState.value = PestDetailUiState(
                pestInfo = info,
                isLoading = false,
                pestName = name
            )
        }
    }
}
