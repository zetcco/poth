package dev.indrajith.poth.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** poth's home-screen widgets. */
object Widgets {
    /** Redraws every one placed, with reading as it now stands. */
    suspend fun updateAll(context: Context) {
        ReadingWidget().updateAll(context)
        TodayWidget().updateAll(context)
    }
}
