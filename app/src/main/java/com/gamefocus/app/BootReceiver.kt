package com.gamefocus.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.enabled(context) && Perm.usage(context) && Perm.dnd(context)
        ) {
            ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
        }
    }
}
