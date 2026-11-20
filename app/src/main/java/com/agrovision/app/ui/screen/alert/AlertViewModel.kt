package com.agrovision.app.ui.screen.alert

import androidx.lifecycle.ViewModel
import com.agrovision.app.data.agent.AgentOrchestrator
import com.agrovision.app.data.agent.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AlertUiState(
    val alerts: List<Alert> = emptyList(),
    val activeCount: Int = 0
)

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState())
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val all = orchestrator.getAllAlerts()
        val active = orchestrator.getActiveAlerts()
        _uiState.value = AlertUiState(alerts = all, activeCount = active.size)
    }

    fun markRead(id: String) {
        orchestrator.markAlertRead(id)
        refresh()
    }

    fun markAllRead() {
        orchestrator.getAllAlerts().forEach { orchestrator.markAlertRead(it.id) }
        refresh()
    }
}
