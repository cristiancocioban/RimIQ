package com.rimiq.training.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rimiq.training.drills.DrillEngine
import com.rimiq.training.model.BallObservation
import com.rimiq.training.model.DrillMetrics
import com.rimiq.training.model.DrillState
import com.rimiq.training.model.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrainingUiState(
    val state: DrillState = DrillState.READY,
    val metrics: DrillMetrics = DrillMetrics(),
    val latestPoseFrame: PoseFrame? = null
)

class TrainingViewModel : ViewModel() {
    private val drillEngine = DrillEngine()
    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    fun onPoseFrame(frame: PoseFrame, ballObservation: BallObservation?) {
        viewModelScope.launch {
            val (nextState, metrics) = drillEngine.consume(frame, ballObservation)
            _uiState.value = _uiState.value.copy(
                state = nextState,
                metrics = metrics,
                latestPoseFrame = frame
            )
        }
    }

    fun endSession() {
        drillEngine.finishSession()
    }
}
