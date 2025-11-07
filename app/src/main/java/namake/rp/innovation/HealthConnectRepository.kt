package namake.rp.innovation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mock implementation kept in main sources so the project compiles without Health Connect dependency.
 * When Health Connect is enabled, a real `HealthConnectRepositoryImpl` is compiled into the optional
 * source set and will be used by the factory. To avoid duplicate class names, this mock is renamed.
 */
class MockHealthConnectRepositoryImpl(private val context: Context) : HealthRepository {
    override suspend fun fetchHealthData(): HealthData = withContext(Dispatchers.IO) {
        // Return mock values with daily steps simulation
        val mockDailySteps = listOf(7500, 8200, 9100, 8500, 7800, 9500, 8342)  // 過去7日間
        val mockTodaySteps = mockDailySteps.last()
        val mockTotalSteps = mockDailySteps.sum()

        HealthData(
            exerciseScore = 75,
            sleepHours = 6.2,
            steps = mockTotalSteps,
            heartRate = 68,
            dailySteps = mockDailySteps,
            todaySteps = mockTodaySteps,
            todaySleepHours = 7.5,  // 本日の睡眠時間
            yesterdaySleepHours = 6.5  // 昨日の睡眠時間
        )
    }
}

/**
 * 権限ハンドラ用の抽象化（実装は Activity での ActivityResultLauncher を用いたものに差し替えてください）。
 */
interface HealthPermissionHandler {
    fun hasPermissions(context: Context): Boolean
    fun requestPermissions(context: Context)
}

/**
 * モック権限ハンドラ（常に false を返す）
 */
class MockHealthPermissionHandler : HealthPermissionHandler {
    override fun hasPermissions(context: Context): Boolean = false
    override fun requestPermissions(context: Context) {}
}
