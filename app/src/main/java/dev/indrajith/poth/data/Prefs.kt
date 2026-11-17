package dev.indrajith.poth.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings in SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("poth", Context.MODE_PRIVATE)

    /** While reading, the screen stays in the orientation it was locked in. */
    var rotationLocked: Boolean
        get() = sp.getBoolean("rotationLocked", false)
        set(value) = sp.edit().putBoolean("rotationLocked", value).apply()

    /** Reading speed is measured from pages read since this time (milliseconds since the epoch). */
    var speedSince: Long
        get() = sp.getLong("speedSince", 0)
        set(value) = sp.edit().putLong("speedSince", value).apply()

    private val _trackingPaused = MutableStateFlow(sp.getBoolean("trackingPaused", false))

    /** While true, reading is not recorded at all: pages read are not in the stats. */
    val trackingPaused: StateFlow<Boolean> = _trackingPaused

    fun pauseTracking(paused: Boolean) {
        sp.edit().putBoolean("trackingPaused", paused).apply()
        _trackingPaused.value = paused
    }
}
