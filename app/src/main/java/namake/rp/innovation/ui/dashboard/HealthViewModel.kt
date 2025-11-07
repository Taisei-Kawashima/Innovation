package namake.rp.innovation.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import namake.rp.innovation.HealthRepository

// UIの状態を表すデータクラス
data class HealthUiState(
    val userName: String = "ユーザー名",
    val isHealthConnectLinked: Boolean = false,
    val totalScore: Int = 0,
    val exerciseStatus: String = "",
    val sleepStatus: String = "",
    val heartRateStatus: String = "",
    val sleepDeficitMinutes: Int = 0,
    val showSleepAlert: Boolean = false,
    val steps: Int = 0,
    val sleepHours: Double = 0.0,
    val heartRate: Int = 0,
    val todaySteps: Int = 0,  // 本日の歩数
    val dailySteps: List<Int> = emptyList(),  // 過去7日間の日別歩数
    val todaySleepHours: Double = 0.0,  // 本日の睡眠時間
    val yesterdaySleepHours: Double = 0.0  // 昨日の睡眠時間
)


class HealthViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadHealthData()
    }

    private fun loadHealthData() {
        viewModelScope.launch {
            try {
                // ヘルスコネクト連携を確認
                val isConnected = hasHealthConnectPermission()
                Log.d("HealthViewModel", "Health Connect connected: $isConnected")
                setHealthConnectStatus(isConnected)

                // ヘルスコネクトからデータを取得
                if (isConnected) {
                    fetchHealthConnectData()
                } else {
                    Log.d("HealthViewModel", "Using demo data")
                    updateWithDemoData()
                }
            } catch (e: Exception) {
                Log.e("HealthViewModel", "Failed to load health data", e)
                updateWithDemoData()
            }
        }
    }

    private fun fetchHealthConnectData() {
        viewModelScope.launch {
            try {
                Log.d("HealthViewModel", "Fetching Health Connect data...")
                val repoClass = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl")
                val constructor = repoClass.getConstructor(Context::class.java)
                val repository = constructor.newInstance(context) as? HealthRepository

                if (repository != null) {
                    Log.d("HealthViewModel", "Repository instance created successfully")
                    val healthData = repository.fetchHealthData()

                    if (healthData != null) {
                        Log.d("HealthViewModel", "✅ Health data received from Health Connect:")
                        Log.d("HealthViewModel", "  - Steps (7 days): ${healthData.steps} (歩)")
                        Log.d("HealthViewModel", "  - Today's Steps: ${healthData.todaySteps} (歩)")
                        Log.d("HealthViewModel", "  - Daily Steps: ${healthData.dailySteps}")
                        Log.d("HealthViewModel", "  - Sleep Hours (7 days): ${healthData.sleepHours} (時間)")
                        Log.d("HealthViewModel", "  - Today's Sleep Hours: ${healthData.todaySleepHours} (時間)")
                        Log.d("HealthViewModel", "  - Yesterday's Sleep Hours: ${healthData.yesterdaySleepHours} (時間)")
                        Log.d("HealthViewModel", "  - Heart Rate: ${healthData.heartRate} (bpm)")
                        Log.d("HealthViewModel", "  - Exercise Score: ${healthData.exerciseScore}")

                        updateHealthData(
                            score = healthData.exerciseScore,
                            sleepHours = healthData.sleepHours,
                            steps = healthData.steps,
                            heartRate = healthData.heartRate,
                            todaySteps = healthData.todaySteps,
                            dailySteps = healthData.dailySteps,
                            todaySleepHours = healthData.todaySleepHours,
                            yesterdaySleepHours = healthData.yesterdaySleepHours
                        )
                    } else {
                        Log.w("HealthViewModel", "Health data is null, using demo data")
                        updateWithDemoData()
                    }
                } else {
                    Log.w("HealthViewModel", "Repository instance is null, using demo data")
                    updateWithDemoData()
                }
            } catch (e: Exception) {
                Log.e("HealthViewModel", "Failed to fetch Health Connect data: ${e.message}", e)
                e.printStackTrace()
                updateWithDemoData()
            }
        }
    }


    private fun updateWithDemoData() {
        Log.d("HealthViewModel", "Loading demo data")
        val demoDailySteps = listOf(7500, 8200, 9100, 8500, 7800, 9500, 8000)
        updateHealthData(
            score = 75,
            sleepHours = 6.5,
            steps = demoDailySteps.sum(),
            heartRate = 72,
            todaySteps = demoDailySteps.last(),
            dailySteps = demoDailySteps,
            todaySleepHours = 7.0,
            yesterdaySleepHours = 6.5
        )
    }

    private fun calculateScore(steps: Int, heartRate: Int): Int {
        // スコア計算ロジック
        val stepsScore = (steps.toFloat() / 10000f * 40f).toInt().coerceIn(0, 40)
        val heartRateScore = if (heartRate in 60..100) 35 else 20
        return stepsScore + heartRateScore
    }

    // UIの状態を更新
    fun updateHealthData(score: Int, sleepHours: Double, steps: Int, heartRate: Int, todaySteps: Int = 0, dailySteps: List<Int> = emptyList(), todaySleepHours: Double = 0.0, yesterdaySleepHours: Double = 0.0) {
        Log.d("HealthViewModel", "========== UI STATE UPDATE ==========")
        Log.d("HealthViewModel", "📊 Updating UI with new health data:")
        Log.d("HealthViewModel", "  - Score: $score")
        Log.d("HealthViewModel", "  - Total Steps (7 days): $steps")
        Log.d("HealthViewModel", "  - Today's Steps: $todaySteps")
        Log.d("HealthViewModel", "  - Daily Steps: $dailySteps")
        Log.d("HealthViewModel", "  - Today's Sleep Hours: $todaySleepHours")
        Log.d("HealthViewModel", "  - Yesterday's Sleep Hours: $yesterdaySleepHours")
        Log.d("HealthViewModel", "  - Heart Rate: $heartRate bpm")
        Log.d("HealthViewModel", "  - Sleep Hours (7 days): $sleepHours hours")
        Log.d("HealthViewModel", "====================================")

        _uiState.update { currentState ->
            val deficit = ((7.0 - sleepHours) * 60).toInt().coerceAtLeast(0)
            currentState.copy(
                totalScore = score,
                sleepHours = sleepHours,
                steps = steps,
                heartRate = heartRate,
                todaySteps = todaySteps,
                dailySteps = dailySteps,
                todaySleepHours = todaySleepHours,
                yesterdaySleepHours = yesterdaySleepHours,
                exerciseStatus = if (steps >= 8000) "良好 (+5P)" else "要改善",
                sleepStatus = if (sleepHours < 7) "要改善 (-10P)" else "良好 (+5P)",
                heartRateStatus = if (heartRate in 60..100) "安定 (+2P)" else "要確認",
                sleepDeficitMinutes = deficit,
                showSleepAlert = deficit > 60,
                isHealthConnectLinked = true
            )
        }
    }

    // JSの setHealthConnectStatus に相当
    fun setHealthConnectStatus(isConnected: Boolean) {
        Log.d("HealthViewModel", "setHealthConnectStatus: $isConnected")
        _uiState.update { it.copy(isHealthConnectLinked = isConnected) }
    }

    private fun hasHealthConnectPermission(): Boolean {
        return try {
            val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl\$HealthConnectRuntimeBridge")
            val method = cls.getMethod("hasPermissions", Context::class.java)
            val result = method.invoke(null, context) as? Boolean ?: false
            Log.d("HealthViewModel", "hasHealthConnectPermission: $result")
            result
        } catch (e: Exception) {
            Log.d("HealthViewModel", "Health Connect not available: ${e.message}")
            false
        }
    }
}