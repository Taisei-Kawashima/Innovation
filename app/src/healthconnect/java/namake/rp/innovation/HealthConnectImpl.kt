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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 実機で Health Connect を有効にしたときにのみコンパイルされる実装。
 */
class HealthConnectRepositoryImpl(private val context: Context) : HealthRepository {
    override suspend fun fetchHealthData(): HealthData = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)

            val todaysData = java.time.LocalDate.now()
            val sevenDaysAgo = todaysData.minusDays(7)
            val start = sevenDaysAgo.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            val end = todaysData.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)

            Log.d("HealthApp", "Fetching data from $sevenDaysAgo to $todaysData")

            // 日別歩数を取得
            val dailySteps = mutableListOf<Int>()
            for (i in 0..6) {
                val date = sevenDaysAgo.plusDays(i.toLong())
                val dayStart = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                val dayEnd = date.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                val timeRange = TimeRangeFilter.between(dayStart, dayEnd)

                val stepsRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeRange
                )
                val stepsResponse = client.readRecords(stepsRequest)
                val daySteps = stepsResponse.records.sumOf { rec -> rec.count }.toInt()
                dailySteps.add(daySteps)
                Log.d("HealthApp", "Steps for $date: $daySteps")
            }

            // 7日間の合計歩数
            val totalSteps = dailySteps.sum()
            val todaySteps = dailySteps.lastOrNull() ?: 0

            // Sleep sessions（7日間と本日）
            val sleepRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val sleepResponse = client.readRecords(sleepRequest)

            // 本日の睡眠時間を計算
            val today = java.time.LocalDate.now()
            val todayStart = today.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            val todayEnd = today.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)

            val todaySleepSeconds = sleepResponse.records
                .filter { session ->
                    val sessionDate = java.time.Instant.ofEpochSecond(session.startTime.epochSecond)
                    sessionDate.isBefore(todayEnd) && sessionDate.isAfter(todayStart)
                }
                .fold(0L) { acc, session ->
                    val startSec = session.startTime.epochSecond
                    val endSec = session.endTime.epochSecond
                    val duration = maxOf(0L, endSec - startSec)
                    Log.d("HealthApp", "Today's sleep session: ${duration}s")
                    acc + duration
                }
            val todaySleepHours = todaySleepSeconds / 3600.0

            // 昨日の睡眠時間を計算
            val yesterday = today.minusDays(1)
            val yesterdayStart = yesterday.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            val yesterdayEnd = today.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)

            val yesterdaySleepSeconds = sleepResponse.records
                .filter { session ->
                    val sessionDate = java.time.Instant.ofEpochSecond(session.startTime.epochSecond)
                    sessionDate.isBefore(yesterdayEnd) && sessionDate.isAfter(yesterdayStart)
                }
                .fold(0L) { acc, session ->
                    val startSec = session.startTime.epochSecond
                    val endSec = session.endTime.epochSecond
                    val duration = maxOf(0L, endSec - startSec)
                    Log.d("HealthApp", "Yesterday's sleep session: ${duration}s")
                    acc + duration
                }
            val yesterdaySleepHours = yesterdaySleepSeconds / 3600.0

            val totalSleepSeconds = sleepResponse.records.fold(0L) { acc, session ->
                val startSec = session.startTime.epochSecond
                val endSec = session.endTime.epochSecond
                val duration = maxOf(0L, endSec - startSec)
                acc + duration
            }
            val totalSleepHours = totalSleepSeconds / 3600.0

            // Heart rate（7日間）
            val hrRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val hrResponse = client.readRecords(hrRequest)
            val allSamples = hrResponse.records.flatMap { it.samples }
            val avgHr = if (allSamples.isNotEmpty()) {
                allSamples.map { it.beatsPerMinute }.average().roundToInt()
            } else {
                0
            }

            Log.d("HealthApp", "7-day summary: totalSteps=$totalSteps, todaySteps=$todaySteps, sleepHours=$totalSleepHours, todaySleepHours=$todaySleepHours, avgHr=$avgHr")
            Log.d("HealthApp", "Daily steps: $dailySteps")

            // スコア計算
            val stepScore = when {
                totalSteps >= 70000 -> 40
                totalSteps >= 50000 -> 30
                totalSteps >= 30000 -> 20
                totalSteps >= 10000 -> 15
                else -> 10
            }
            val sleepScore = when {
                totalSleepHours >= 49.0 -> 40
                totalSleepHours >= 42.0 -> 30
                totalSleepHours >= 35.0 -> 20
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
                sleepHours = String.format(Locale.US, "%.1f", totalSleepHours).toDouble(),
                steps = totalSteps,
                heartRate = avgHr,
                dailySteps = dailySteps,
                todaySteps = todaySteps,
                todaySleepHours = String.format(Locale.US, "%.1f", todaySleepHours).toDouble(),
                yesterdaySleepHours = String.format(Locale.US, "%.1f", yesterdaySleepHours).toDouble()
            )
        } catch (e: Exception) {
            Log.e("HealthApp", "Error fetching health data", e)
            HealthData(
                exerciseScore = 0,
                sleepHours = 0.0,
                steps = 0,
                heartRate = 0,
                dailySteps = emptyList(),
                todaySteps = 0,
                todaySleepHours = 0.0,
                yesterdaySleepHours = 0.0
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