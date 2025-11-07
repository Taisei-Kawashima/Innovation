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
import androidx.core.net.toUri
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import namake.rp.innovation.ui.theme.InnovationTheme

class MainActivity : ComponentActivity() {
    private val permissionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionState.value = healthBridgeHasPermissions(this)
        Log.d("HealthApp", "Initial permissionState=${permissionState.value}")

        val permissionHandler = object : HealthPermissionHandler {
            override fun hasPermissions(context: Context): Boolean {
                return permissionState.value
            }

            override fun requestPermissions(context: Context) {
                try {
                    Log.d("HealthApp", "Requesting Health Connect permissions")
                    // Call bridge.requestPermissions directly
                    val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl\$HealthConnectRuntimeBridge")
                    val method = cls.getMethod("requestPermissions", Context::class.java)
                    method.invoke(null, context)
                    Log.d("HealthApp", "Permission request launched")
                } catch (e: Exception) {
                    Log.e("HealthApp", "Failed to request permissions", e)
                    // As a last resort, open the URI directly
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = "healthconnect://permissions".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (ex: Exception) {
                        Log.e("HealthApp", "Fallback URI open failed", ex)
                    }
                }
            }
        }

        setContent {
            InnovationTheme {
                WebDashboard(permissionHandler = permissionHandler)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions after returning from permission UI
        val newState = healthBridgeHasPermissions(this)
        if (newState != permissionState.value) {
            permissionState.value = newState
            Log.d("HealthApp", "Permission state changed in onResume: ${permissionState.value}")
        }
    }

    private fun healthBridgeHasPermissions(ctx: Context): Boolean {
        return try {
            val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl\$HealthConnectRuntimeBridge")
            val method = cls.getMethod("hasPermissions", Context::class.java)
            method.invoke(null, ctx) as? Boolean ?: false
        } catch (e: Exception) {
            Log.d("HealthApp", "Bridge not available: ${e.message}")
            false
        }
    }
}


suspend fun evaluateJavascriptBoolean(webView: WebView, script: String, timeoutMs: Long = 2000): Boolean {
    val deferred = CompletableDeferred<Boolean>()
    try {
        webView.post {
            try {
                webView.evaluateJavascript(script) { result ->
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
    val permissionState = remember { mutableStateOf(permissionHandler.hasPermissions(context)) }
    val debugLog = remember { mutableStateOf(listOf<String>()) }

    fun addLog(msg: String) {
        Log.d("HealthApp", msg)
        debugLog.value = (debugLog.value + "[${System.currentTimeMillis() % 10000}] $msg").takeLast(20)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // WebView (takes up most space)
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        webViewRef.value = view
                        pageLoaded.value = true
                        addLog("WebView loaded: $url")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        Log.d("WebView", "${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                        return true
                    }
                }
                WebView.setWebContentsDebuggingEnabled(true)
                loadUrl("file:///android_asset/dashboard.html")
            }
        }, modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f))

        // Debug panel with permission button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    addLog("User clicked: Request Permissions button")
                    permissionState.value = permissionHandler.hasPermissions(context)
                    addLog("Current permission state: ${permissionState.value}")
                    if (!permissionState.value) {
                        addLog("Starting permission request...")
                        try {
                            permissionHandler.requestPermissions(context)
                            addLog("requestPermissions() called successfully")
                        } catch (e: Exception) {
                            addLog("ERROR in requestPermissions: ${e.message}")
                            e.printStackTrace()
                        }
                    } else {
                        addLog("Already has permissions!")
                    }
                }
            ) {
                androidx.compose.material3.Text(
                    if (permissionState.value) "✓ Has Permissions" else "Request Permissions"
                )
            }

            androidx.compose.material3.Button(
                onClick = {
                    addLog("User clicked: Check Permissions button")
                    permissionState.value = permissionHandler.hasPermissions(context)
                    addLog("Permission check result: ${permissionState.value}")
                }
            ) {
                androidx.compose.material3.Text("Check Permissions")
            }

            // Debug log display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline)
                    .padding(4.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                debugLog.value.forEach { logLine ->
                    androidx.compose.material3.Text(
                        logLine,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        addLog("=== LaunchedEffect started ===")
        addLog("Initial permission state: ${permissionHandler.hasPermissions(context)}")

        if (!permissionHandler.hasPermissions(context)) {
            addLog("No permissions, auto-requesting...")
            permissionHandler.requestPermissions(context)
            addLog("Auto-request completed")

            // Health Connect設定画面から戻るまで待機
            val start = System.currentTimeMillis()
            while (!permissionHandler.hasPermissions(context) &&
                System.currentTimeMillis() - start < 30_000) {
                addLog("Waiting for permissions... (${System.currentTimeMillis() - start}ms)")
                delay(1000)  // 1秒ごとにチェック
            }
            addLog("Wait completed. Has permissions: ${permissionHandler.hasPermissions(context)}")
        }

        val useReal = permissionHandler.hasPermissions(context)
        addLog("Using real implementation: $useReal")

        val repo: HealthRepository = if (useReal) {
            try {
                addLog("Creating real HealthConnectRepositoryImpl...")
                val cls = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl")
                val ctor = cls.getConstructor(Context::class.java)
                ctor.newInstance(context) as HealthRepository
            } catch (e: Exception) {
                addLog("ERROR creating real repo: ${e.message}")
                Log.e("HealthApp", "Failed to create real repo", e)
                MockHealthConnectRepositoryImpl(context)
            }
        } else {
            addLog("Using mock repository")
            MockHealthConnectRepositoryImpl(context)
        }

        try {
            addLog("Fetching health data...")
            val data = repo.fetchHealthData()
            val json = "{\"exerciseScore\":${data.exerciseScore},\"sleepHours\":${data.sleepHours},\"steps\":${data.steps},\"heartRate\":${data.heartRate}}"
            addLog("Fetched data: $json")

            while (!pageLoaded.value) {
                addLog("Waiting for WebView to load...")
                delay(100)
            }

            val webView = webViewRef.value
            if (webView != null) {
                try {
                    addLog("Setting Health Connect status to $useReal")
                    val statusScript = "(function(){try{if(typeof window.setHealthConnectStatus==='function'){window.setHealthConnectStatus($useReal);return true;}return false;}catch(e){return false;}})();"
                    evaluateJavascriptBoolean(webView, statusScript, 1000)
                    addLog("Status script executed")
                } catch (e: Exception) {
                    addLog("ERROR setting status: ${e.message}")
                    Log.e("HealthApp", "Failed to set HC status", e)
                }

                var injected = false
                repeat(10) { attempt ->
                    addLog("Injection attempt $attempt...")
                    val script = "(function(){try{if(typeof window.updateHealthData==='function'){window.updateHealthData($json);return true;}return false;}catch(e){return false;}})();"
                    val ok = evaluateJavascriptBoolean(webView, script, 1500)
                    addLog("Injection attempt $attempt: $ok")
                    if (ok) {
                        injected = true
                        return@repeat
                    }
                    delay(300)
                }

                if (!injected) {
                    addLog("Using last-effort injection")
                    webView.post {
                        webView.evaluateJavascript("try{window.updateHealthData($json);}catch(e){console.error(e);}", null)
                    }
                    addLog("Last-effort injection posted")
                }
            } else {
                addLog("ERROR: WebView is null!")
            }

            if (!useReal) {
                addLog("Polling for real permissions...")
                while (true) {
                    delay(30_000)
                    addLog("Poll check: hasPermissions=${permissionHandler.hasPermissions(context)}")
                    if (permissionHandler.hasPermissions(context)) {
                        addLog("Real permissions granted! Fetching real data...")
                        try {
                            val realRepo = Class.forName("namake.rp.innovation.HealthConnectRepositoryImpl")
                                .getConstructor(Context::class.java)
                                .newInstance(context) as? HealthRepository

                            val rd = realRepo?.fetchHealthData()
                            if (rd != null) {
                                val rjson = "{\"exerciseScore\":${rd.exerciseScore},\"sleepHours\":${rd.sleepHours},\"steps\":${rd.steps},\"heartRate\":${rd.heartRate}}"
                                addLog("Real data fetched: $rjson")
                                val wv = webViewRef.value
                                wv?.post {
                                    wv.evaluateJavascript("try{window.setHealthConnectStatus(true);window.updateHealthData($rjson);}catch(e){console.error(e);}", null)
                                }
                            } else {
                                addLog("ERROR: Real repo returned null data")
                            }
                        } catch (e: Exception) {
                            addLog("ERROR fetching real data: ${e.message}")
                            Log.e("HealthApp", "Failed to fetch real data", e)
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            addLog("ERROR in health data flow: ${e.message}")
            Log.e("HealthApp", "Error in health data flow", e)
        }
    }
}

@Composable
fun HealthDashboard(
    modifier: Modifier = Modifier,
    permissionHandler: HealthPermissionHandler = MockHealthPermissionHandler(),
    repository: HealthRepository? = null
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("この画面はプレビュー用です。Web ダッシュボードを使用してください。")
    }
}