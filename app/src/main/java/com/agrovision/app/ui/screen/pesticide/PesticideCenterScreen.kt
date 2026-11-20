package com.agrovision.app.ui.screen.pesticide

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.domain.advisor.SprayCalculation
import com.agrovision.app.domain.model.PesticideCategory
import com.agrovision.app.domain.model.PesticideProduct
import com.agrovision.app.domain.model.matchesIngredient
import com.agrovision.app.ui.theme.*
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesticideCenterScreen(
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onSaveToFarm: (String, String, Int) -> Unit = { _, _, _ -> },
    viewModel: PesticideCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable(initialTab) {
        mutableIntStateOf(initialTab.coerceIn(0, 2))
    }
    val cartCount = state.cart.values.sum()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("植保用药", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { selectedTab = 2 }) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) Badge { Text(cartCount.coerceAtMost(99).toString()) }
                            }
                        ) {
                            Icon(Icons.Outlined.ShoppingCart, "采购清单")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                PesticideTab(0, selectedTab, Icons.Outlined.Science, "配药计算") { selectedTab = 0 }
                PesticideTab(1, selectedTab, Icons.Outlined.Storefront, "农资商城") { selectedTab = 1 }
                PesticideTab(2, selectedTab, Icons.Outlined.ShoppingBasket, "采购清单") { selectedTab = 2 }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> EmptyState(Icons.Outlined.ErrorOutline, state.error ?: "加载失败")
                selectedTab == 0 -> MixingPage(
                    state = state,
                    calculation = viewModel.calculation(),
                    onPestSelected = viewModel::selectPest,
                    onIngredientSelected = viewModel::selectIngredient,
                    onAreaChanged = viewModel::setArea,
                    onDoseChanged = viewModel::setDose,
                    onWaterChanged = viewModel::setWater,
                    onTankChanged = viewModel::setTank,
                    onUnitChanged = viewModel::setDoseUnit,
                    onOpenShop = {
                        viewModel.showSelectedIngredientInShop()
                        selectedTab = 1
                    },
                    onSaveToFarm = onSaveToFarm
                )
                selectedTab == 1 -> ShopPage(
                    state = state,
                    products = viewModel.visibleProducts(),
                    onSearchChanged = viewModel::setSearchQuery,
                    onCategorySelected = viewModel::setCategory,
                    onAdd = viewModel::addToCart
                )
                else -> CartPage(
                    lines = viewModel.cartLines(),
                    onQuantityChanged = viewModel::changeQuantity,
                    onBrowse = { selectedTab = 1 }
                )
            }
        }
    }
}

@Composable
private fun PesticideTab(
    index: Int,
    selectedIndex: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Tab(
        selected = index == selectedIndex,
        onClick = onClick,
        text = { Text(label, maxLines = 1) },
        icon = { Icon(icon, null, Modifier.size(20.dp)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MixingPage(
    state: PesticideCenterUiState,
    calculation: SprayCalculation?,
    onPestSelected: (String) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onAreaChanged: (String) -> Unit,
    onDoseChanged: (String) -> Unit,
    onWaterChanged: (String) -> Unit,
    onTankChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onOpenShop: () -> Unit,
    onSaveToFarm: (String, String, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.XL.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.LG.dp)
    ) {
        item {
            Surface(
                shape = ShapeMD,
                color = brandBlue().copy(alpha = 0.10f),
                border = BorderStroke(1.dp, brandBlue().copy(alpha = 0.22f))
            ) {
                Row(Modifier.padding(Spacing.LG.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = brandBlue())
                    Spacer(Modifier.width(Spacing.MD.dp))
                    Column {
                        Text("按登记标签计算", fontWeight = FontWeight.SemiBold)
                        Text(
                            "推荐成分为备选方案，不代表可以混用；剂量以手中产品标签为准。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(Icons.Outlined.BugReport, "防治对象")
            Spacer(Modifier.height(Spacing.SM.dp))
            PestDropdown(state, onPestSelected)
        }

        if (state.recommendedIngredients.isNotEmpty()) {
            item {
                Text("推荐有效成分", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Spacing.SM.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM.dp)
                ) {
                    state.recommendedIngredients.forEach { ingredient ->
                        FilterChip(
                            selected = ingredient == state.selectedIngredient,
                            onClick = { onIngredientSelected(ingredient) },
                            label = { Text(ingredient) },
                            leadingIcon = if (ingredient == state.selectedIngredient) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(Icons.Outlined.Calculate, "作业参数")
            Spacer(Modifier.height(Spacing.SM.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
                NumberField("面积（亩）", state.areaMu, onAreaChanged, Modifier.weight(1f))
                NumberField("标签亩用量", state.labelDosePerMu, onDoseChanged, Modifier.weight(1f), "必填")
            }
            Spacer(Modifier.height(Spacing.MD.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
                NumberField("亩用水（升）", state.waterPerMu, onWaterChanged, Modifier.weight(1f))
                NumberField("药箱容量（升）", state.tankCapacity, onTankChanged, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.MD.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("毫升", "克").forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.doseUnit == unit,
                        onClick = { onUnitChanged(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(unit) }
                }
            }
        }

        item {
            if (calculation == null) {
                Surface(shape = ShapeMD, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.LG.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.MD.dp))
                        Text("填写产品标签上的亩用量后生成配药结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                CalculationPanel(calculation, state.doseUnit, state.safetyIntervalDays)
            }
        }

        item {
            Column {
                Button(
                    onClick = onOpenShop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.MIN.dp),
                    enabled = state.selectedIngredient.isNotBlank(),
                    shape = ShapeMD
                ) {
                    Icon(Icons.Outlined.Storefront, null)
                    Spacer(Modifier.width(Spacing.SM.dp))
                    Text("查看匹配农资")
                }
                Spacer(Modifier.height(Spacing.SM.dp))
                OutlinedButton(
                    onClick = {
                        val result = calculation ?: return@OutlinedButton
                        val format = DecimalFormat("0.##")
                        val dosage = "总药量${format.format(result.totalProduct)}${state.doseUnit} / " +
                            "总用水${format.format(result.totalWaterLiters)}升 / ${result.tankCount}桶"
                        onSaveToFarm(state.selectedIngredient, dosage, state.safetyIntervalDays)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.MIN.dp),
                    enabled = calculation != null && state.selectedIngredient.isNotBlank(),
                    shape = ShapeMD
                ) {
                    Icon(Icons.Outlined.Agriculture, null)
                    Spacer(Modifier.width(Spacing.SM.dp))
                    Text("保存到农事记录")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PestDropdown(state: PesticideCenterUiState, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.selectedPest?.cnName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("病虫害") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.pests.forEach { pest ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(pest.cnName)
                            Text(pest.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        onSelected(pest.cnName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, maxLines = 1) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun CalculationPanel(calculation: SprayCalculation, unit: String, safetyDays: Int) {
    val format = remember { DecimalFormat("0.##") }
    Surface(
        shape = ShapeMD,
        color = brandGreen().copy(alpha = 0.10f),
        border = BorderStroke(1.dp, brandGreen().copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.LG.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, null, tint = brandGreen())
                Spacer(Modifier.width(Spacing.SM.dp))
                Text("本次配药结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.LG.dp))
            Row(Modifier.fillMaxWidth()) {
                ResultMetric("总药量", "${format.format(calculation.totalProduct)} $unit", Modifier.weight(1f), brandGreen())
                ResultMetric("总用水", "${format.format(calculation.totalWaterLiters)} 升", Modifier.weight(1f), brandBlue())
                ResultMetric("配药桶数", "${calculation.tankCount} 桶", Modifier.weight(1f), accentOrange())
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.LG.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("整桶加入 ${format.format(calculation.fullTankProduct)} $unit", fontWeight = FontWeight.SemiBold)
            Text(
                "最后一桶：${format.format(calculation.lastTankWaterLiters)} 升水 + ${format.format(calculation.lastTankProduct)} ${unit}药剂",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.MD.dp))
            AssistChip(
                onClick = {},
                label = { Text("默认安全间隔期 $safetyDays 天") },
                leadingIcon = { Icon(Icons.Outlined.EventAvailable, null, Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String, modifier: Modifier, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShopPage(
    state: PesticideCenterUiState,
    products: List<PesticideProduct>,
    onSearchChanged: (String) -> Unit,
    onCategorySelected: (PesticideCategory?) -> Unit,
    onAdd: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = accentOrange().copy(alpha = 0.10f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp, vertical = Spacing.MD.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = accentOrange())
                Spacer(Modifier.width(Spacing.SM.dp))
                Text("本地农资展示目录 · 参考价格 · 暂不提供在线支付", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp, vertical = Spacing.MD.dp),
            placeholder = { Text("搜索有效成分、防治对象") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { onSearchChanged("") }) { Icon(Icons.Filled.Close, "清空") } }
            } else null,
            singleLine = true,
            shape = ShapeMD
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.XL.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)
        ) {
            FilterChip(selected = state.selectedCategory == null, onClick = { onCategorySelected(null) }, label = { Text("全部") })
            PesticideCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.label) }
                )
            }
        }
        Spacer(Modifier.height(Spacing.SM.dp))
        if (products.isEmpty()) {
            EmptyState(Icons.Outlined.Inventory2, "没有匹配的农资商品")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.XL.dp, vertical = Spacing.SM.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        quantity = state.cart[product.id] ?: 0,
                        recommended = state.recommendedIngredients.any(product::matchesIngredient),
                        onAdd = { onAdd(product.id) }
                    )
                }
                item { Spacer(Modifier.height(Spacing.XL.dp)) }
            }
        }
    }
}

@Composable
private fun ProductCard(product: PesticideProduct, quantity: Int, recommended: Boolean, onAdd: () -> Unit) {
    Card(
        shape = ShapeMD,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.CARD.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.LG.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductPack(product.category)
            Spacer(Modifier.width(Spacing.LG.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(product.activeIngredient, style = MaterialTheme.typography.labelLarge, color = categoryColor(product.category))
                    if (recommended) {
                        Spacer(Modifier.width(Spacing.SM.dp))
                        Text("当前推荐", style = MaterialTheme.typography.labelSmall, color = brandGreen(), fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Spacing.XS.dp))
                Text(
                    "${product.specification} · ${product.toxicity} · ${product.registeredTargets.take(2).joinToString(" / ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.SM.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥${String.format("%.2f", product.referencePrice)}", fontWeight = FontWeight.Bold, color = accentOrange())
                    Text(" 参考价", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Filled.AddShoppingCart, "加入采购清单") }
                if (quantity > 0) Text("已加 $quantity", style = MaterialTheme.typography.labelSmall, color = brandGreen())
            }
        }
    }
}

@Composable
private fun ProductPack(category: PesticideCategory) {
    val color = categoryColor(category)
    Surface(
        modifier = Modifier.size(width = 64.dp, height = 78.dp),
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(categoryIcon(category), null, Modifier.size(28.dp), tint = color)
            Spacer(Modifier.height(Spacing.XS.dp))
            Text(category.label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun CartPage(
    lines: List<Pair<PesticideProduct, Int>>,
    onQuantityChanged: (String, Int) -> Unit,
    onBrowse: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    if (lines.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.ShoppingBasket,
            message = "采购清单还是空的",
            action = { TextButton(onClick = onBrowse) { Text("去商城选择") } }
        )
        return
    }
    val total = lines.sumOf { (product, quantity) -> product.referencePrice * quantity }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.XL.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
    ) {
        item {
            Text("采购清单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("共 ${lines.sumOf { it.second }} 件农资", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(lines, key = { it.first.id }) { (product, quantity) ->
            Surface(shape = ShapeMD, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(Spacing.LG.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductPack(product.category)
                    Spacer(Modifier.width(Spacing.LG.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.activeIngredient, fontWeight = FontWeight.SemiBold)
                        Text(product.specification, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${String.format("%.2f", product.referencePrice * quantity)}", color = accentOrange(), fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onQuantityChanged(product.id, quantity - 1) }) {
                            Icon(if (quantity == 1) Icons.Outlined.Delete else Icons.Outlined.Remove, "减少")
                        }
                        Text(quantity.toString(), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onQuantityChanged(product.id, quantity + 1) }) {
                            Icon(Icons.Outlined.Add, "增加")
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = ShapeMD, color = brandBlue().copy(alpha = 0.09f)) {
                Row(Modifier.fillMaxWidth().padding(Spacing.LG.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("参考合计", fontWeight = FontWeight.SemiBold)
                    Text("¥${String.format("%.2f", total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = brandBlue())
                }
            }
        }
        item {
            Button(
                onClick = {
                    val text = buildString {
                        appendLine("农智眼农资采购清单")
                        lines.forEach { (product, quantity) ->
                            appendLine("- ${product.name} ${product.specification} × $quantity")
                        }
                        append("参考合计：¥${String.format("%.2f", total)}")
                    }
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "采购清单已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.MIN.dp),
                shape = ShapeMD
            ) {
                Icon(Icons.Outlined.ContentCopy, null)
                Spacer(Modifier.width(Spacing.SM.dp))
                Text("复制采购清单")
            }
            Text(
                "购买前请核对农药登记证、生产日期、登记作物与防治对象。",
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.MD.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.SM.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    action: @Composable (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            Spacer(Modifier.height(Spacing.MD.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun categoryColor(category: PesticideCategory): Color = when (category) {
    PesticideCategory.FUNGICIDE -> brandBlue()
    PesticideCategory.INSECTICIDE -> accentOrange()
    PesticideCategory.BIOLOGICAL -> brandGreen()
    PesticideCategory.ADJUVANT -> MaterialTheme.colorScheme.tertiary
}

private fun categoryIcon(category: PesticideCategory) = when (category) {
    PesticideCategory.FUNGICIDE -> Icons.Outlined.Shield
    PesticideCategory.INSECTICIDE -> Icons.Outlined.PestControl
    PesticideCategory.BIOLOGICAL -> Icons.Outlined.Eco
    PesticideCategory.ADJUVANT -> Icons.Outlined.WaterDrop
}
