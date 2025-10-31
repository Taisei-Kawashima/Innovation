package namake.rp.innovation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import namake.rp.innovation.ui.theme.InnovationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Permission state holder
        val permissionState = mutableStateOf(false)

        // Helper functions to call HealthConnectRuntimeBridge via reflection
        fun healthBridgeHasPermissions(ctx: Context): Boolean {
            return try {
                val cls = Class.forName("namake.rp.innovation.HealthConnectRuntimeBridge")
                val method = cls.getMethod("hasPermissions", Context::class.java)
                method.invoke(null, ctx) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun healthBridgeCreateIntent(ctx: Context): Intent? {
            return try {
                val cls = Class.forName("namake.rp.innovation.HealthConnectRuntimeBridge")
                val method = cls.getMethod("createRequestPermissionIntent", Context::class.java)
                method.invoke(null, ctx) as? Intent
            } catch (e: Exception) {
                null
            }
        }

        // Initialize permission state using the bridge (safe if bridge is absent)
        permissionState.value = healthBridgeHasPermissions(this)

        // Register launcher for Health Connect permission intent
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                permissionState.value = healthBridgeHasPermissions(this)
            }
        }

        // Implement HealthPermissionHandler that uses the launcher and reflection bridge
        val permissionHandler = object : HealthPermissionHandler {
            override fun hasPermissions(context: Context): Boolean = permissionState.value
            override fun requestPermissions(context: Context) {
                lifecycleScope.launch {
                    try {
                        val intent = healthBridgeCreateIntent(context)
                        if (intent != null) permissionLauncher.launch(intent)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }

        setContent {
            InnovationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HealthDashboard(modifier = Modifier.padding(innerPadding), permissionHandler = permissionHandler)
                }
            }
        }
    }
}

// Data model for health values
data class HealthData(
    val exerciseScore: Int,
    val sleepHours: Double,
    val steps: Int,
    val heartRate: Int
)

// Repository interface - later replace with real Health Connect implementation
interface HealthRepository {
    suspend fun fetchHealthData(): HealthData
}

// Mock implementation used for demo and UI wiring
class MockHealthRepository : HealthRepository {
    override suspend fun fetchHealthData(): HealthData {
        // Simulate network / SDK delay
        delay(500)
        return HealthData(
            exerciseScore = 75,
            sleepHours = 6.2,
            steps = 8342,
            heartRate = 68
        )
    }
}

@Composable
fun HealthDashboard(
    modifier: Modifier = Modifier,
    permissionHandler: HealthPermissionHandler = MockHealthPermissionHandler(),
    repository: HealthRepository? = null
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(permissionHandler.hasPermissions(context)) }

    // When granted changes, pick repository: if passed explicitly, use it; otherwise use real impl when granted
    val activeRepo = remember(granted) {
        when {
            repository != null -> repository
            granted -> HealthConnectRepositoryImpl(context)
            else -> MockHealthRepository()
        }
    }

    val loading = remember { mutableStateOf(true) }
    val dataState = remember { mutableStateOf<HealthData?>(null) }

    LaunchedEffect(activeRepo) {
        loading.value = true
        try {
            val d = activeRepo!!.fetchHealthData()
            dataState.value = d
        } finally {
            loading.value = false
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Permission banner when not granted
            if (!granted) {
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = "Health Connect の権限が未設定です", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(text = "健康データの取得には権限が必要です。", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            // In a real implementation this would launch the ActivityResult for Health Connect permission.
                            // For now we simulate granting so the UI can be tested.
                            permissionHandler.requestPermissions(context)
                            granted = true
                        }) {
                            Text("連携する")
                        }
                    }
                }
            }

            if (loading.value && dataState.value == null) {
                // Simple loading
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("データを取得しています...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val d = dataState.value ?: HealthData(0, 0.0, 0, 0)
                Text(text = "総合健康スコア", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Score card
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = "スコア", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(text = "${d.exerciseScore}/100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF7043))
                            Text(text = "運動・睡眠・心拍の総合指標", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        // simple ring placeholder
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(shape = RoundedCornerShape(50), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                androidx.compose.foundation.layout.Box(modifier = Modifier.size(72.dp).background(Color.White), contentAlignment = Alignment.Center) {
                                    Text(text = "${d.exerciseScore}", color = Color(0xFFFF7043), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Grid for Sleep / Steps / Heart rate
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStatCard(label = "睡眠", value = "${d.sleepHours} h")
                    MiniStatCard(label = "歩数", value = "${d.steps}")
                    MiniStatCard(label = "心拍数", value = "${d.heartRate} bpm")
                }

                // Actions / suggestion area
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(text = "今日の次のアクション", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "・あと15分の軽いウォーキングをしましょう", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "・今夜は23時までに就寝を目指す", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Greeting(name = "Android")
            }
        }
    }
}

@Composable
fun MiniStatCard(label: String, value: String) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF7043))
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InnovationTheme {
        HealthDashboard()
    }
}