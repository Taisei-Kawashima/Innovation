package namake.rp.innovation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRate
import androidx.health.connect.client.records.Steps
import androidx.health.connect.client.records.SleepSession
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
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
        val stepsRequest = ReadRecordsRequest(Steps::class, timeRangeFilter = timeRange)
        val stepsResponse = client.readRecords(stepsRequest)
        val totalSteps = stepsResponse.records.sumOf { rec -> (rec.count ?: 0).toLong() }

        // Sleep sessions: 合計睡眠時間（時間）
        val sleepRequest = ReadRecordsRequest(SleepSession::class, timeRangeFilter = timeRange)
        val sleepResponse = client.readRecords(sleepRequest)
        val totalSleepSeconds = sleepResponse.records.fold(0L) { acc, session ->
            val startSec = session.startTime?.epochSecond ?: 0L
            val endSec = session.endTime?.epochSecond ?: 0L
            acc + maxOf(0L, endSec - startSec)
        }
        val totalSleepHours = totalSleepSeconds / 3600.0

        // Heart rate: 平均
        val hrRequest = ReadRecordsRequest(HeartRate::class, timeRangeFilter = timeRange)
        val hrResponse = client.readRecords(hrRequest)
        val hrCount = hrResponse.records.size
        val avgHr = if (hrCount > 0) {
            hrResponse.records.mapNotNull { it.bpm }.average().roundToInt()
        } else 0

        // Log raw fetched values for debugging
        Log.d("HealthApp", "HC fetched: steps=$totalSteps, sleepHours=${String.format("%.2f", totalSleepHours)}, avgHr=$avgHr, hrCount=$hrCount")

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

        Log.d("HealthApp", "HC computed scores: stepScore=$stepScore, sleepScore=$sleepScore, hrScore=$hrScore, exerciseScore=$exerciseScore")

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
            // getGrantedPermissions is suspend; call from runBlocking to adapt to synchronous bridge API
            val granted = runBlocking { client.permissionController.getGrantedPermissions() }
            // granted is Set<String> (permission strings); compare using .toString() of each
            val needed = permissions.map { it.permission }
            granted.containsAll(needed)
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun createRequestPermissionIntent(context: Context): Intent? {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            // createRequestPermissionIntent is suspend in some APIs; use runBlocking
            runBlocking {
                client.permissionController.createRequestPermissionIntent(
                    setOf(
                        HealthPermission.getReadPermission(Steps::class),
                        HealthPermission.getReadPermission(HeartRate::class),
                        HealthPermission.getReadPermission(SleepSession::class)
                    )
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
