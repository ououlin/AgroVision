package com.agrovision.app.ui.screen.pesticide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.domain.advisor.SprayCalculation
import com.agrovision.app.domain.advisor.SprayCalculator
import com.agrovision.app.domain.model.PestInfo
import com.agrovision.app.domain.model.PestLabelCatalog
import com.agrovision.app.domain.model.PesticideCatalog
import com.agrovision.app.domain.model.PesticideCategory
import com.agrovision.app.domain.model.PesticideProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PesticideCenterUiState(
    val pests: List<PestInfo> = emptyList(),
    val selectedPest: PestInfo? = null,
    val selectedIngredient: String = "",
    val areaMu: String = "1",
    val labelDosePerMu: String = "",
    val waterPerMu: String = "30",
    val tankCapacity: String = "16",
    val doseUnit: String = "毫升",
    val searchQuery: String = "",
    val selectedCategory: PesticideCategory? = null,
    val cart: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val recommendedIngredients: List<String>
        get() = selectedPest?.pesticides.orEmpty()

    val safetyIntervalDays: Int
        get() = selectedPest?.let { pest ->
            PestLabelCatalog.ENTRIES.firstOrNull { it.cnName == pest.cnName }?.safetyIntervalDays
        }?.takeIf { it > 0 } ?: 14
}

@HiltViewModel
class PesticideCenterViewModel @Inject constructor(
    private val encyclopediaRepository: EncyclopediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val initialPest = savedStateHandle.get<String>("initialPest").orEmpty()
    private val _uiState = MutableStateFlow(PesticideCenterUiState())
    val uiState: StateFlow<PesticideCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                encyclopediaRepository.getAllPests()
                    .filter { it.pesticides.isNotEmpty() }
                    .sortedBy { it.cnName }
            }.onSuccess { pests ->
                val selected = pests.firstOrNull {
                    it.cnName == initialPest || it.enName.equals(initialPest, ignoreCase = true)
                } ?: pests.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    pests = pests,
                    selectedPest = selected,
                    selectedIngredient = selected?.pesticides?.firstOrNull().orEmpty(),
                    isLoading = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "药剂资料加载失败"
                )
            }
        }
    }

    fun selectPest(name: String) {
        val pest = _uiState.value.pests.firstOrNull { it.cnName == name } ?: return
        _uiState.value = _uiState.value.copy(
            selectedPest = pest,
            selectedIngredient = pest.pesticides.firstOrNull().orEmpty()
        )
    }

    fun selectIngredient(value: String) {
        _uiState.value = _uiState.value.copy(selectedIngredient = value)
    }

    fun setArea(value: String) { _uiState.value = _uiState.value.copy(areaMu = numeric(value)) }
    fun setDose(value: String) { _uiState.value = _uiState.value.copy(labelDosePerMu = numeric(value)) }
    fun setWater(value: String) { _uiState.value = _uiState.value.copy(waterPerMu = numeric(value)) }
    fun setTank(value: String) { _uiState.value = _uiState.value.copy(tankCapacity = numeric(value)) }
    fun setDoseUnit(value: String) { _uiState.value = _uiState.value.copy(doseUnit = value) }
    fun setSearchQuery(value: String) { _uiState.value = _uiState.value.copy(searchQuery = value) }
    fun setCategory(value: PesticideCategory?) { _uiState.value = _uiState.value.copy(selectedCategory = value) }

    fun showSelectedIngredientInShop() {
        _uiState.value = _uiState.value.copy(searchQuery = _uiState.value.selectedIngredient)
    }

    fun calculation(): SprayCalculation? {
        val state = _uiState.value
        return runCatching {
            SprayCalculator.calculate(
                areaMu = state.areaMu.toDouble(),
                labelDosePerMu = state.labelDosePerMu.toDouble(),
                waterPerMuLiters = state.waterPerMu.toDouble(),
                tankCapacityLiters = state.tankCapacity.toDouble()
            )
        }.getOrNull()
    }

    fun visibleProducts(): List<PesticideProduct> = PesticideCatalog.search(
        query = _uiState.value.searchQuery,
        category = _uiState.value.selectedCategory,
        recommendedIngredients = _uiState.value.recommendedIngredients
    )

    fun addToCart(productId: String) {
        changeQuantity(productId, (_uiState.value.cart[productId] ?: 0) + 1)
    }

    fun changeQuantity(productId: String, quantity: Int) {
        val updated = _uiState.value.cart.toMutableMap()
        if (quantity <= 0) updated.remove(productId) else updated[productId] = quantity.coerceAtMost(99)
        _uiState.value = _uiState.value.copy(cart = updated)
    }

    fun cartLines(): List<Pair<PesticideProduct, Int>> = _uiState.value.cart.mapNotNull { (id, quantity) ->
        PesticideCatalog.products.firstOrNull { it.id == id }?.let { it to quantity }
    }

    private fun numeric(value: String): String = value.filterIndexed { index, c ->
        c.isDigit() || (c == '.' && index > 0 && value.take(index).none { it == '.' })
    }
}
