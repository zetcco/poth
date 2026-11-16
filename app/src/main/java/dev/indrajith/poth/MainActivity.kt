package dev.indrajith.poth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.indrajith.poth.ui.PothRoot
import dev.indrajith.poth.ui.PothTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Set when the widget asked for the reading stats; the UI shows them and clears it. */
    private val openStats = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // poth is always dark, so the system bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_STATS) openStats.value = true
        setContent {
            PothTheme {
                PothRoot(openStats)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_STATS) openStats.value = true
    }

    companion object {
        const val ACTION_STATS = "dev.indrajith.poth.STATS"
    }
}
