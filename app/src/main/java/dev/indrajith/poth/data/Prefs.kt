package dev.indrajith.poth.data

import android.content.Context
import android.content.SharedPreferences

/** Small settings in SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("poth", Context.MODE_PRIVATE)

    /** While reading, the screen stays in the orientation it was locked in. */
    var rotationLocked: Boolean
        get() = sp.getBoolean("rotationLocked", false)
        set(value) = sp.edit().putBoolean("rotationLocked", value).apply()
}
