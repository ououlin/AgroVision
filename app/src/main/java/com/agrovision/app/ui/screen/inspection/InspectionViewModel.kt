package com.agrovision.app.ui.screen.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.agent.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class InspectionUiState(
    val tasks: List<InspectionTask> = emptyList(),
    val systemStatus: SystemStatus = SystemStatus(),
    val isExecuting: Boolean = false
)

@HiltViewModel
class InspectionViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    init {
        refreshTaskList()
    }

    fun createTask(title: String, type: InspectionType, robot: RobotType) {
        val task = InspectionTask(
            id = UUID.randomUUID().toString().take(8),
            title = title,
            type = type,
            assignee = robot,
            targetArea = "默认区域",
            route = listOf(GeoPoint(39.9042, 116.4074), GeoPoint(39.9082, 116.4104))
        )
        orchestrator.createInspectionTask(task)
        refreshTaskList()
    }

    fun executeTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            orchestrator.executeInspectionPipeline(taskId)
            refreshTaskList()
            _uiState.value = _uiState.value.copy(isExecuting = false)
        }
    }

    fun getSystemStatus(): SystemStatus = orchestrator.getSystemStatus()

    private fun refreshTaskList() {
        _uiState.value = _uiState.value.copy(
            tasks = orchestrator.getAllTasks(),
            systemStatus = orchestrator.getSystemStatus()
        )
    }
}
