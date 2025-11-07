package namake.rp.innovation.ui.dashboard

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UIの状態を表すデータクラス
data class HealthUiState(
    val userName: String = "ユーザー名",
    val isHealthConnectLinked: Boolean = true,
    val totalScore: Int = 75,
    val exerciseStatus: String = "良好 (+5P)",
    val sleepStatus: String = "要改善 (-10P)",
    val heartRateStatus: String = "安定 (+2P)",
    val sleepDeficitMinutes: Int = 90,
    val showSleepAlert: Boolean = true
)

class HealthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // デモデータの読み込みなど
        loadData()
    }

    private fun loadData() {
        // ここでリポジトリからデータを取得する
        // この例では初期値のまま
    }

    // JSの updateHealthData に相当
    fun updateHealthData(score: Int, sleepHours: Double, steps: Int, heartRate: Int) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val deficit = ((7.0 - sleepHours) * 60).toInt().coerceAtLeast(0)
                currentState.copy(
                    totalScore = score,
                    sleepStatus = if (sleepHours < 7) "要改善 (-10P)" else "良好 (+5P)",
                    // 他のロジックもここに追加
                    sleepDeficitMinutes = deficit,
                    showSleepAlert = deficit > 60
                )
            }
        }
    }

    // JSの setHealthConnectStatus に相当
    fun setHealthConnectStatus(isConnected: Boolean) {
        _uiState.update { it.copy(isHealthConnectLinked = isConnected) }
    }
}