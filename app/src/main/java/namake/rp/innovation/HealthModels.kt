package namake.rp.innovation

// Shared data models and repository interface

data class HealthData(
    val exerciseScore: Int,
    val sleepHours: Double,
    val steps: Int,
    val heartRate: Int,
    val dailySteps: List<Int> = emptyList(),  // 過去7日間の日別歩数（古い順）
    val todaySteps: Int = 0,  // 今日の歩数
    val todaySleepHours: Double = 0.0,  // 今日の睡眠時間
    val yesterdaySleepHours: Double = 0.0  // 昨日の睡眠時間
)

interface HealthRepository {
    suspend fun fetchHealthData(): HealthData
}

