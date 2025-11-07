package namake.rp.innovation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import namake.rp.innovation.ui.theme.InnovationTheme
import namake.rp.innovation.ui.dashboard.DashboardScreen
import namake.rp.innovation.ui.dashboard.HealthViewModel
import namake.rp.innovation.ui.dashboard.HealthViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            InnovationTheme {
                // ViewModelFactoryを使ってContextを渡す
                val viewModel: HealthViewModel = viewModel(
                    factory = HealthViewModelFactory(this@MainActivity)
                )
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}


