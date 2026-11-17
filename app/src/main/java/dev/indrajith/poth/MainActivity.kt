package dev.indrajith.poth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
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

    override fun onResume() {
        super.onResume()
        stayAwake()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) stayAwake()
        return super.dispatchTouchEvent(event)
    }

    // A page can take longer to read than the phone's own screen timeout, so the screen stays on
    // while poth is open. Once nothing has touched it for AWAKE_FOR, the phone sleeps as it
    // would, so a book left open still locks.
    private val sleep = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    private fun stayAwake() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.removeCallbacks(sleep)
        window.decorView.postDelayed(sleep, AWAKE_FOR)
    }

    companion object {
        const val ACTION_STATS = "dev.indrajith.poth.STATS"
        private const val AWAKE_FOR = 10 * 60 * 1000L
    }
}
