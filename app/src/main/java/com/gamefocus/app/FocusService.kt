package com.gamefocus.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var fg: String? = null
    private var lastQuery = 0L
    private var engaged = false
    private var misses = 0
    private var previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    private val tick = object : Runnable {
        override fun run() {
            try { poll() } catch (_: Exception) { }
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        handler.removeCallbacks(tick)
        lastQuery = System.currentTimeMillis() - 60 * 60 * 1000
        handler.post(tick)
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun poll() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> fg = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == fg) fg = null
            }
        }
        lastQuery = now - 3000
        apply(fg)
    }

    private fun apply(pkg: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        val isGame = pkg != null && pkg in Prefs.games(this)
        if (isGame) {
            misses = 0
            val target = if (Prefs.mode(this) == "alarms")
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            else
                NotificationManager.INTERRUPTION_FILTER_NONE
            if (!engaged) {
                val cur = nm.currentInterruptionFilter
                previousFilter = if (cur == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                    NotificationManager.INTERRUPTION_FILTER_ALL else cur
                engaged = true
            }
            if (nm.currentInterruptionFilter != target) nm.setInterruptionFilter(target)
            FocusState.activePkg.value = pkg
        } else if (engaged) {
            misses++
            if (misses >= 2) release(nm)
        }
    }

    private fun release(nm: NotificationManager) {
        nm.setInterruptionFilter(previousFilter)
        engaged = false
        misses = 0
        FocusState.activePkg.value = null
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("focus", "Game Focus", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Keeps Game Focus running in the background" }
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "focus")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle("Game Focus is on")
            .setContentText("Waiting for one of your games")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (engaged) {
            release(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        }
        super.onDestroy()
    }
}
