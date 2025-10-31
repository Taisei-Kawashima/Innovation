package namake.rp.innovation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 現在のビルド構成では Health Connect のライブラリがプロジェクトに含まれていないため、
 * ここではモック実装を提供します。Health Connect を導入するときはこのクラスを差し替えてください。
 */
class HealthConnectRepositoryImpl(private val context: Context) : HealthRepository {
    override suspend fun fetchHealthData(): HealthData = withContext(Dispatchers.IO) {
        // TODO: Health Connect SDK を導入したら、ここで HealthConnectClient を使ってデータを取得してください。
        // 例（擬似）:
        // val client = HealthConnectClient.getOrCreate(context)
        // val steps = client.readRecords(Steps::class, ...)
        // val sleep = client.readRecords(SleepSession::class, ...)
        // val heart = client.readRecords(HeartRate::class, ...)

        // ここではモック値を返す
        HealthData(
            exerciseScore = 75,
            sleepHours = 6.2,
            steps = 8342,
            heartRate = 68
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
