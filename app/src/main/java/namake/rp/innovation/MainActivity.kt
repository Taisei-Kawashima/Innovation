package namake.rp.innovation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                val res = method.invoke(null, ctx) as? Boolean ?: false
                Log.d("HealthApp", "healthBridgeHasPermissions -> $res")
                res
            } catch (e: Exception) {
                Log.d("HealthApp", "healthBridgeHasPermissions: bridge not available: ${e.message}")
                false
            }
        }

        fun healthBridgeCreateIntent(ctx: Context): Intent? {
            return try {
                val cls = Class.forName("namake.rp.innovation.HealthConnectRuntimeBridge")
                val method = cls.getMethod("createRequestPermissionIntent", Context::class.java)
                val intent = method.invoke(null, ctx) as? Intent
                Log.d("HealthApp", "healthBridgeCreateIntent -> ${intent != null}")
                intent
            } catch (e: Exception) {
                Log.d("HealthApp", "healthBridgeCreateIntent: bridge not available: ${e.message}")
                null
            }
        }

        // Initialize permission state using the bridge (safe if bridge is absent)
        permissionState.value = healthBridgeHasPermissions(this)
        Log.d("HealthApp", "initial permissionState=${permissionState.value}")

        // Register launcher for Health Connect permission intent
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("HealthApp", "permissionLauncher resultCode=${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                permissionState.value = healthBridgeHasPermissions(this)
                Log.d("HealthApp", "permission granted -> permissionState=${permissionState.value}")
            } else {
                Log.d("HealthApp", "permission not granted or cancelled")
            }
        }

        // Implement HealthPermissionHandler that uses the launcher and reflection bridge
        val permissionHandler = object : HealthPermissionHandler {
            override fun hasPermissions(context: Context): Boolean = permissionState.value
            override fun requestPermissions(context: Context) {
                lifecycleScope.launch {
                    try {
                        val intent = healthBridgeCreateIntent(context)
                        Log.d("HealthApp", "requestPermissions: launching intent? ${intent != null}")
                        if (intent != null) permissionLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.d("HealthApp", "requestPermissions: failed to create/launch intent: ${e.message}")
                        // ignore
                    }
                }
            }
        }

        setContent {
            InnovationTheme {
                // Load the HTML dashboard in a WebView and pass HealthConnect data into it
                WebDashboard(permissionHandler = permissionHandler)
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

suspend fun evaluateJavascriptBoolean(webView: WebView, script: String, timeoutMs: Long = 2000): Boolean {
    val deferred = CompletableDeferred<Boolean>()
    try {
        webView.post {
            try {
                webView.evaluateJavascript(script) { result ->
                    // result is a JSON value like "true" or "false" (including quotes)
                    val cleaned = result?.trim() ?: "false"
                    val value = when (cleaned) {
                        "true", "\"true\"" -> true
                        else -> false
                    }
                    if (!deferred.isCompleted) deferred.complete(value)
                }
            } catch (e: Exception) {
                if (!deferred.isCompleted) deferred.complete(false)
            }
        }
    } catch (e: Exception) {
        if (!deferred.isCompleted) deferred.complete(false)
    }

    return try {
        kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
    } catch (t: Throwable) {
        false
    }
}

@Composable
fun WebDashboard(permissionHandler: HealthPermissionHandler) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val pageLoaded = remember { mutableStateOf(false) }

    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webViewRef.value = view
                    pageLoaded.value = true
                    Log.d("HealthApp", "WebView onPageFinished: $url")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d("WebViewConsole", "${consoleMessage.message()} (source: ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}) level=${consoleMessage.messageLevel()}")
                    return true
                }
            }
            WebView.setWebContentsDebuggingEnabled(true)
            loadUrl("file:///android_asset/dashboard.html")
        }
    }, modifier = Modifier.fillMaxSize()) { view ->
        // no-op
    }

    LaunchedEffect(Unit) {
        // Ensure permissions: if not granted, request and wait up to timeout for user to grant
        if (!permissionHandler.hasPermissions(context)) {
            Log.d("HealthApp", "No permissions: requesting")
            permissionHandler.requestPermissions(context)
            // wait for the user to grant permission (polling) up to 15 seconds
            val start = System.currentTimeMillis()
            val timeout = 15_000L
            while (!permissionHandler.hasPermissions(context) && System.currentTimeMillis() - start < timeout) {
                delay(500)
            }
            Log.d("HealthApp", "Permission check after wait: ${permissionHandler.hasPermissions(context)}")
        }

        val useReal = permissionHandler.hasPermissions(context)
        Log.d("HealthApp", "Using real HealthConnect implementation? $useReal")
        val repo: HealthRepository = if (useReal) {
            // Try to instantiate real HealthConnectRepositoryImpl via reflection if available
            try {
                val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl")
                val ctor = cls.getConstructor(Context::class.java)
                ctor.newInstance(context) as HealthRepository
            } catch (e: Exception) {
                Log.d("HealthApp", "Failed to instantiate real repo via reflection: ${e.message}")
                // Fallback to mock implementation
                MockHealthConnectRepositoryImpl(context)
            }
        } else {
            MockHealthConnectRepositoryImpl(context)
        }

        try {
            val d = repo.fetchHealthData()
            val json = "{\"exerciseScore\":${d.exerciseScore},\"sleepHours\":${d.sleepHours},\"steps\":${d.steps},\"heartRate\":${d.heartRate}}"
            Log.d("HealthApp", "fetched health data -> $json")
            // Wait for page to be loaded before injecting JS
            while (!pageLoaded.value) {
                delay(100)
            }

            // Notify WebView about Health Connect status (true = real, false = mock)
            val webView = webViewRef.value
            if (webView != null) {
                try {
                    val statusScript = "(function(){try{if(typeof window.setHealthConnectStatus==='function'){window.setHealthConnectStatus(${useReal});return true;}else{return false;}}catch(e){return false;}})();"
                    // best-effort notify (no need to wait long)
                    val statusOk = evaluateJavascriptBoolean(webView, statusScript, timeoutMs = 1000)
                    Log.d("HealthApp", "setHealthConnectStatus called -> $statusOk")
                } catch (e: Exception) {
                    Log.d("HealthApp", "error notifying setHealthConnectStatus: ${e.message}")
                    // ignore
                }
            }

            // Use robust injection: retry until window.updateHealthData is callable
            if (webView != null) {
                var injected = false
                repeat(10) { attempt ->
                    val script = "(function(){try{if(typeof window.updateHealthData==='function'){window.updateHealthData($json);return true;}else{return false;}}catch(e){return false;}})();"
                    val ok = evaluateJavascriptBoolean(webView, script, timeoutMs = 1500)
                    Log.d("HealthApp", "attempt=${attempt} updateHealthData callable? $ok")
                    if (ok) {
                        injected = true
                        return@repeat
                    }
                    delay(300)
                }
                if (!injected) {
                    // last effort without checking
                    webView.post { webView.evaluateJavascript("try{window.updateHealthData($json);}catch(e){}", null) }
                    Log.d("HealthApp", "last-effort injected without confirmation")
                } else {
                    Log.d("HealthApp", "injection succeeded")
                }
            }

            // If we used Mock (because permission not granted), keep polling periodically and inject updates
            if (!useReal) {
                // ensure WebView knows we are not connected
                val wv0 = webViewRef.value
                if (wv0 != null) {
                    try {
                        val statusFalse = "(function(){try{if(typeof window.setHealthConnectStatus==='function'){window.setHealthConnectStatus(false);return true;}else{return false;}}catch(e){return false;}})();"
                        wv0.post { wv0.evaluateJavascript(statusFalse, null) }
                    } catch (e: Exception) { Log.d("HealthApp", "notify not connected failed: ${e.message}") }
                }

                // poll every 30s to refresh data (or until permission is granted)
                while (true) {
                    delay(30_000)
                    if (permissionHandler.hasPermissions(context)) {
                        // permission granted later; switch to real repo and fetch once
                        try {
                            val realRepo = try {
                                val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl")
                                val ctor = cls.getConstructor(Context::class.java)
                                ctor.newInstance(context) as HealthRepository
                            } catch (exInst: Exception) {
                                Log.d("HealthApp", "failed to instantiate real repo during polling: ${exInst.message}")
                                null
                            }
                            val rd = realRepo?.fetchHealthData()
                            if (rd != null) {
                                val rjson = "{\"exerciseScore\":${rd.exerciseScore},\"sleepHours\":${rd.sleepHours},\"steps\":${rd.steps},\"heartRate\":${rd.heartRate}}"
                                val wv = webViewRef.value
                                if (wv != null) {
                                    // notify connected
                                    try {
                                        val statusTrue = "(function(){try{if(typeof window.setHealthConnectStatus==='function'){window.setHealthConnectStatus(true);return true;}else{return false;}}catch(e){return false;}})();"
                                        wv.post { wv.evaluateJavascript(statusTrue, null) }
                                        Log.d("HealthApp", "notified webview that HealthConnect is connected")
                                    } catch (ex: Exception) { Log.d("HealthApp", "notify connected failed: ${ex.message}") }

                                    var injected2 = false
                                    repeat(10) {
                                        val script2 = "(function(){try{if(typeof window.updateHealthData==='function'){window.updateHealthData($rjson);return true;}else{return false;}}catch(e){return false;}})();"
                                        val ok2 = evaluateJavascriptBoolean(wv, script2, timeoutMs = 1500)
                                        if (ok2) { injected2 = true; return@repeat }
                                        delay(300)
                                    }
                                    if (!injected2) {
                                        wv.post { wv.evaluateJavascript("try{window.updateHealthData($rjson);}catch(e){}", null) }
                                    }
                                    Log.d("HealthApp", "polled and injected real data after permission granted")
                                }
                            }
                        } catch (ex: Exception) {
                            Log.d("HealthApp", "error during polling fetch: ${ex.message}")
                            // ignore
                        }
                        break
                    } else {
                        // refresh mock data
                        try {
                            val md = MockHealthRepository().fetchHealthData()
                            val mjson = "{\"exerciseScore\":${md.exerciseScore},\"sleepHours\":${md.sleepHours},\"steps\":${md.steps},\"heartRate\":${md.heartRate}}"
                            val wv2 = webViewRef.value
                            if (wv2 != null) {
                                var injected3 = false
                                repeat(5) {
                                    val script3 = "(function(){try{if(typeof window.updateHealthData==='function'){window.updateHealthData($mjson);return true;}else{return false;}}catch(e){return false;}})();"
                                    val ok3 = evaluateJavascriptBoolean(wv2, script3, timeoutMs = 1000)
                                    if (ok3) { injected3 = true; return@repeat }
                                    delay(200)
                                }
                                if (!injected3) {
                                    wv2.post { wv2.evaluateJavascript("try{window.updateHealthData($mjson);}catch(e){}", null) }
                                }
                            }
                            Log.d("HealthApp", "refreshed mock data in polling loop -> $mjson")
                        } catch (ex: Exception) {
                            Log.d("HealthApp", "error refreshing mock data: ${ex.message}")
                            // ignore
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HealthApp", "fetch/inject error: ${e.message}")
            // ignore for now; you may show error in UI
        }
    }
}

@Composable
fun HealthDashboard(
    modifier: Modifier = Modifier,
    permissionHandler: HealthPermissionHandler = MockHealthPermissionHandler(),
    repository: HealthRepository? = null
) {
    // ...existing code... (kept for previews and fallback)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("この画面はプレビュー用です。Web ダッシュボードを使用してください。")
    }
}

// ...existing code... (MiniStatCard, Greeting, Preview already present earlier)
