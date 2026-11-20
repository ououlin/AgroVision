package com.agrovision.app.ui.screen.agrisearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.rag.AgriSearchService
import com.agrovision.app.data.rag.SearchResultItem
import com.agrovision.app.domain.model.PestInfo
import com.agrovision.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgriSearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val knowledgeBaseSize: Int = 0
)

@HiltViewModel
class AgriSearchViewModel @Inject constructor(
    private val agriSearchService: AgriSearchService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgriSearchUiState(
        knowledgeBaseSize = agriSearchService.knowledgeBaseSize()
    ))
    val uiState: StateFlow<AgriSearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(
            query = query,
            suggestions = if (query.length >= 1) agriSearchService.suggest(query, limit = 5) else emptyList()
        )
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, suggestions = emptyList())
            val results = agriSearchService.searchStructured(query, topK = 10)
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                results = results
            )
        }
    }

    fun selectSuggestion(suggestion: String) {
        _uiState.value = _uiState.value.copy(query = suggestion, suggestions = emptyList())
        search()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgriSearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPestDetail: (String) -> Unit,
    viewModel: AgriSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("农技搜索") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 搜索栏
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.search() },
                active = uiState.suggestions.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    // FE Review: 16→XL, 8→SM
                    .padding(horizontal = Spacing.XL.dp, vertical = Spacing.SM.dp)
            ) {
                // 建议下拉
                uiState.suggestions.forEach { suggestion ->
                    ListItem(
                        headlineContent = { Text(suggestion) },
                        modifier = Modifier.clickable { viewModel.selectSuggestion(suggestion) }
                    )
                }
            }

            // 知识库规模提示
            if (uiState.results.isEmpty() && !uiState.isSearching && uiState.query.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // FE Review: 24→XXL
                        .padding(Spacing.XXL.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        // FE Review: 48→TouchTarget.AVATAR
                        modifier = Modifier.size(TouchTarget.AVATAR.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    // FE Review: 12→MD
                    Spacer(Modifier.height(Spacing.MD.dp))
                    Text(
                        "输入病害、虫害或症状关键词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // FE Review: 4→XS
                    Spacer(Modifier.height(Spacing.XS.dp))
                    Text(
                        "知识库规模：${uiState.knowledgeBaseSize} 条 · 离线可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 搜索中
            if (uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 搜索结果
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // FE Review: 16→XL, 8→SM; spaced 8→SM
                contentPadding = PaddingValues(horizontal = Spacing.XL.dp, vertical = Spacing.SM.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM.dp)
            ) {
                items(uiState.results) { item ->
                    SearchResultCard(item = item, onClick = {
                        item.pestInfo?.cnName?.let { onNavigateToPestDetail(it) }
                    })
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: SearchResultItem, onClick: () -> Unit) {
    val pest = item.pestInfo ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        // FE Review: 1→Elevation.CARD
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.CARD.dp)
    ) {
        // FE Review: 16→LG
        Column(modifier = Modifier.padding(Spacing.LG.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    pest.cnName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    pest.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // FE Review: 4→XS
            Spacer(Modifier.height(Spacing.XS.dp))
            Text(
                pest.enName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // FE Review: 8→SM
            Spacer(Modifier.height(Spacing.SM.dp))
            Text(
                item.docSnippet,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pest.pesticides.isNotEmpty()) {
                // FE Review: 8→SM
                Spacer(Modifier.height(Spacing.SM.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text("推荐药剂：${pest.pesticides.take(2).joinToString()}") }
                )
            }
        }
    }
}

// Material3 SearchBar 兼容封装（简化实现：OutlinedTextField + Column 内嵌建议）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索病害、害虫、症状…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                TextButton(onClick = onSearch) { Text("搜索") }
            },
            singleLine = true
        )
        if (active) {
            content()
        }
    }
}
