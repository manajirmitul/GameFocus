package com.gamefocus.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppItem(val pkg: String, val label: String, val isGame: Boolean, val icon: ImageBitmap)

@Suppress("DEPRECATION")
fun loadApps(c: Context): List<AppItem> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != c.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val ai = ri.activityInfo.applicationInfo
            val game = ai.category == ApplicationInfo.CATEGORY_GAME ||
                (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            AppItem(
                pkg = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                isGame = game,
                icon = ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}
