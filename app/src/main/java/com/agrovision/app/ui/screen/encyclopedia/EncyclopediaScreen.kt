package com.agrovision.app.ui.screen.encyclopedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.domain.model.PestInfo
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaScreen(
    onNavigateToPestDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EncyclopediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }

    // 合并所有分类
    val allCategories = remember(uiState.pests) {
        val cats = uiState.pests.map { it.category }.distinct().sorted()
        listOf("全部") + cats
    }

    // 筛选
    val filteredPests = remember(uiState.pests, searchQuery, selectedCategory) {
        var list = if (searchQuery.isNotBlank()) {
            uiState.pests.filter {
                it.cnName.contains(searchQuery, ignoreCase = true) ||
                it.enName.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        } else uiState.pests

        if (selectedCategory != "全部") {
            list = list.filter { it.category == selectedCategory }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("病虫百科", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索
            // 搜索：纯本地过滤，ViewModel 只持有全量列表，避免双重搜索与多余 IO
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                placeholder = { Text("搜索病虫害") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, null) }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = brandGreen(),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            // 分类标签
            if (uiState.categories.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = allCategories.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 18.dp, divider = {},
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    allCategories.forEach { cat ->
                        val sel = cat == selectedCategory
                        Tab(selected = sel, onClick = { selectedCategory = cat }) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (sel) brandGreen() else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(cat, style = MaterialTheme.typography.labelLarge,
                                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = brandGreen())
                    }
                }
                filteredPests.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.MenuBook, null, Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            Spacer(Modifier.height(16.dp))
                            Text("未找到相关病虫害", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPests, key = { it.enName }) { pest ->
                            EncyclopediaCard(
                                pest = pest,
                                onClick = { onNavigateToPestDetail(pest.cnName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EncyclopediaCard(pest: PestInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val green = brandGreen()
            if (pest.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = pest.imageUrl,
                    contentDescription = "${pest.cnName}图片",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = green.copy(alpha = 0.08f)
                ) {
                    Icon(Icons.Filled.BugReport, null, Modifier.padding(16.dp), tint = green)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pest.cnName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(pest.enName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(pest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
