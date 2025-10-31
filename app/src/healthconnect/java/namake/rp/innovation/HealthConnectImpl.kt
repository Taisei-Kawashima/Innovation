package namake.rp.innovation

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRate
import androidx.health.connect.client.records.Steps
import androidx.health.connect.client.records.SleepSession
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 実機で Health Connect を有効にしたときにのみコンパイルされる実装。
 * 有効化は Gradle プロパティ -PenableHealthConnect=true を指定してビルドします。
 */
class HealthConnectRepositoryImpl(private val context: Context) : HealthRepository {
    override suspend fun fetchHealthData(): HealthData = withContext(Dispatchers.IO) {
        val client = HealthConnectClient.getOrCreate(context)

        val end = Instant.now()
        val start = end.minus(7, ChronoUnit.DAYS)
        val timeRange = TimeRangeFilter.between(start, end)

        // Steps
        val stepsRequest = ReadRecordsRequest(timeRangeFilter = timeRange)
        val stepsResponse = client.readRecords(Steps::class, stepsRequest)
        val totalSteps = stepsResponse.records.sumOf { it.count?.toLong() ?: 0L }

        // Sleep sessions: 合計睡眠時間（時間）
        val sleepRequest = ReadRecordsRequest(timeRangeFilter = timeRange)
        val sleepResponse = client.readRecords(SleepSession::class, sleepRequest)
        val totalSleepSeconds = sleepResponse.records.fold(0L) { acc, session ->
            val startSec = session.startTime?.epochSecond ?: 0L
            val endSec = session.endTime?.epochSecond ?: 0L
            acc + maxOf(0L, endSec - startSec)
        }
        val totalSleepHours = totalSleepSeconds / 3600.0

        // Heart rate: 平均
        val hrRequest = ReadRecordsRequest(timeRangeFilter = timeRange)
        val hrResponse = client.readRecords(HeartRate::class, hrRequest)
        val hrCount = hrResponse.records.size
        val avgHr = if (hrCount > 0) hrResponse.records.mapNotNull { it.bpm }.average().toInt() else 0

        // 簡易スコア
        val stepScore = when {
            totalSteps >= 70000 -> 40
            totalSteps >= 50000 -> 30
            totalSteps >= 30000 -> 20
            else -> 10
        }
        val sleepScore = when {
            totalSleepHours >= 7.5 -> 40
            totalSleepHours >= 6.5 -> 30
            totalSleepHours >= 5.5 -> 20
            else -> 10
        }
        val hrScore = when {
            avgHr in 60..80 -> 20
            avgHr in 50..59 -> 15
            avgHr in 81..100 -> 10
            else -> 5
        }
        val exerciseScore = (stepScore + sleepScore + hrScore).coerceIn(0, 100)

        HealthData(
            exerciseScore = exerciseScore,
            sleepHours = String.format("%.1f", totalSleepHours).toDouble(),
            steps = totalSteps.toInt(),
            heartRate = avgHr
        )
    }
}

/**
 * Permission helper: Activity でのリクエストを補助するユーティリティ。実装は Activity 内で ActivityResultLauncher を使ってください。
 */
class HealthConnectPermissionUtil(private val context: Context) {
    fun requiredPermissions(): Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(Steps::class),
        HealthPermission.getReadPermission(HeartRate::class),
        HealthPermission.getReadPermission(SleepSession::class)
    )
}

// --- Runtime bridge for reflection ---
/**
 * ブリッジクラス：MainActivity からリフレクションで呼び出すことを想定。
 * - hasPermissions(context): Boolean
 * - createRequestPermissionIntent(context): Intent
 */
object HealthConnectRuntimeBridge {
    @JvmStatic
    fun hasPermissions(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val permissions = setOf(
                HealthPermission.getReadPermission(Steps::class),
                HealthPermission.getReadPermission(HeartRate::class),
                HealthPermission.getReadPermission(SleepSession::class)
            )
            val granted = client.permissionController.getGrantedPermissions(permissions)
            granted.containsAll(permissions)
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun createRequestPermissionIntent(context: Context): Intent? {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            client.permissionController.createRequestPermissionIntent(
                setOf(
                    HealthPermission.getReadPermission(Steps::class),
                    HealthPermission.getReadPermission(HeartRate::class),
                    HealthPermission.getReadPermission(SleepSession::class)
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}
