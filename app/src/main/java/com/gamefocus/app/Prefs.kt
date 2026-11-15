package com.gamefocus.app

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow

object FocusState {
    val activePkg = MutableStateFlow<String?>(null)
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gamefocus", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun games(c: Context): Set<String> = sp(c).getStringSet("games", emptySet()) ?: emptySet()
    fun setGames(c: Context, s: Set<String>) = sp(c).edit().putStringSet("games", HashSet(s)).apply()

    // "silence" = total silence, "alarms" = alarms can still ring
    fun mode(c: Context) = sp(c).getString("mode", "silence") ?: "silence"
    fun setMode(c: Context, m: String) = sp(c).edit().putString("mode", m).apply()
}

object Perm {
    fun usage(c: Context): Boolean {
        val ao = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dnd(c: Context): Boolean =
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
}
