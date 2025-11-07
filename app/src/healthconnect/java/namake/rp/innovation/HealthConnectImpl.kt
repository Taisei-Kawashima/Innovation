package namake.rp.innovation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 実機で Health Connect を有効にしたときにのみコンパイルされる実装。
 */
class HealthConnectRepositoryImpl(private val context: Context) : HealthRepository {
    override suspend fun fetchHealthData(): HealthData = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)

            val end = Instant.now()
            val start = end.minus(7, ChronoUnit.DAYS)
            val timeRange = TimeRangeFilter.between(start, end)

            Log.d("HealthApp", "Fetching data from $start to $end")

            // Steps
            val stepsRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRange
            )
            val stepsResponse = client.readRecords(stepsRequest)
            val totalSteps = stepsResponse.records.sumOf { rec -> rec.count }
            Log.d("HealthApp", "Steps records count: ${stepsResponse.records.size}, total: $totalSteps")

            // Sleep sessions
            val sleepRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange
            )
            val sleepResponse = client.readRecords(sleepRequest)
            val totalSleepSeconds = sleepResponse.records.fold(0L) { acc, session ->
                val startSec = session.startTime.epochSecond
                val endSec = session.endTime.epochSecond
                val duration = maxOf(0L, endSec - startSec)
                Log.d("HealthApp", "Sleep session: ${duration}s")
                acc + duration
            }
            val totalSleepHours = totalSleepSeconds / 3600.0
            Log.d("HealthApp", "Sleep records count: ${sleepResponse.records.size}, total hours: $totalSleepHours")

            // Heart rate
            val hrRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
            val hrResponse = client.readRecords(hrRequest)
            val allSamples = hrResponse.records.flatMap { it.samples }
            val avgHr = if (allSamples.isNotEmpty()) {
                allSamples.map { it.beatsPerMinute }.average().roundToInt()
            } else {
                0
            }
            Log.d("HealthApp", "HR records: ${hrResponse.records.size}, samples: ${allSamples.size}, avg: $avgHr")

            // 簡易スコア計算
            val stepScore = when {
                totalSteps >= 70000 -> 40
                totalSteps >= 50000 -> 30
                totalSteps >= 30000 -> 20
                totalSteps >= 10000 -> 15
                else -> 10
            }
            val sleepScore = when {
                totalSleepHours >= 49.0 -> 40  // 7時間 x 7日
                totalSleepHours >= 42.0 -> 30  // 6時間 x 7日
                totalSleepHours >= 35.0 -> 20  // 5時間 x 7日
                else -> 10
            }
            val hrScore = when {
                avgHr in 60..80 -> 20
                avgHr in 50..59 -> 15
                avgHr in 81..100 -> 10
                else -> 5
            }
            val exerciseScore = (stepScore + sleepScore + hrScore).coerceIn(0, 100)

            Log.d("HealthApp", "Scores: step=$stepScore, sleep=$sleepScore, hr=$hrScore, total=$exerciseScore")

            HealthData(
                exerciseScore = exerciseScore,
                sleepHours = String.format(Locale.US, "%.1f", totalSleepHours).toDouble(),
                steps = totalSteps.toInt(),
                heartRate = avgHr
            )
        } catch (e: Exception) {
            Log.e("HealthApp", "Error fetching health data", e)
            // エラー時はデフォルト値を返す
            HealthData(
                exerciseScore = 0,
                sleepHours = 0.0,
                steps = 0,
                heartRate = 0
            )
        }
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    /**
     * ブリッジクラス: MainActivity からリフレクションで呼び出す
     * これらのメソッドは常に有効です（Health Connect SDKが無ければフォールバックします）
     */
    object HealthConnectRuntimeBridge {
        @JvmStatic
        fun hasPermissions(context: Context): Boolean {
            return try {
                val client = HealthConnectClient.getOrCreate(context)
                val permissions = setOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(HeartRateRecord::class),
                    HealthPermission.getReadPermission(SleepSessionRecord::class)
                )
                val granted = runBlocking { client.permissionController.getGrantedPermissions() }
                granted.containsAll(permissions)
            } catch (e: Exception) {
                Log.e("HealthApp", "Error checking permissions", e)
                false
            }
        }

        @JvmStatic
        fun createRequestPermissionIntent(context: Context): Intent {
            // SDK の契約（Contract）を使用して Intent を構築する
            try {
                val perms = setOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(HeartRateRecord::class),
                    HealthPermission.getReadPermission(SleepSessionRecord::class)
                )
                val contract = androidx.health.connect.client.contracts.HealthPermissionsRequestContract()
                val intent = contract.createIntent(context, perms)
                Log.d("HealthApp", "Created permission intent via HealthPermissionsRequestContract")
                return intent
            } catch (e: Exception) {
                Log.w("HealthApp", "Failed to create intent via HealthPermissionsRequestContract: ${e.message}")
            }

            // フォールバック: healthconnect://permissions URI
            Log.d("HealthApp", "Falling back to healthconnect://permissions URI")
            return Intent(Intent.ACTION_VIEW).apply {
                data = "healthconnect://permissions".toUri()
            }
        }

        @JvmStatic
        fun requestPermissions(context: Context) {
            try {
                val intent = createRequestPermissionIntent(context)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Log.d("HealthApp", "Starting activity for permissions request")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("HealthApp", "Failed to request permissions", e)
            }
        }
    }
}