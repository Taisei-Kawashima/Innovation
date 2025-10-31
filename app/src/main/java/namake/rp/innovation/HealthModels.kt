package namake.rp.innovation

// Shared data models and repository interface

data class HealthData(
    val exerciseScore: Int,
    val sleepHours: Double,
    val steps: Int,
    val heartRate: Int
)

interface HealthRepository {
    suspend fun fetchHealthData(): HealthData
}

