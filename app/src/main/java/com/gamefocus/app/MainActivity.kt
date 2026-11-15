package com.gamefocus.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object C {
    val Ink = Color(0xFF0C0A1D)
    val Panel = Color(0xFF16132E)
    val PanelHi = Color(0xFF241E4A)
    val Violet = Color(0xFF7B5CFF)
    val Pink = Color(0xFFFF5D8F)
    val Mint = Color(0xFF5CF2C8)
    val Text = Color(0xFFF1EEFF)
    val Muted = Color(0xFF9A93C4)
}

enum class OrbState { Setup, Off, Ready, Engaged }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = C.Violet, background = C.Ink, surface = C.Panel,
                    onSurface = C.Text, onBackground = C.Text
                )
            ) { App() }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var games by remember { mutableStateOf(Prefs.games(ctx)) }
    var mode by remember { mutableStateOf(Prefs.mode(ctx)) }
    var hasUsage by remember { mutableStateOf(Perm.usage(ctx)) }
    var hasDnd by remember { mutableStateOf(Perm.dnd(ctx)) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val active by FocusState.activePkg.collectAsState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                hasUsage = Perm.usage(ctx)
                hasDnd = Perm.dnd(ctx)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        loaded = true
    }

    val allPerms = hasUsage && hasDnd
    LaunchedEffect(enabled, allPerms) {
        val i = Intent(ctx, FocusService::class.java)
        if (enabled && allPerms) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val state = when {
        !allPerms -> OrbState.Setup
        !enabled -> OrbState.Off
        active != null -> OrbState.Engaged
        else -> OrbState.Ready
    }
    val activeLabel = apps.firstOrNull { it.pkg == active }?.label ?: active

    val q = query.trim()
    val filtered = apps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    val picked = filtered.filter { it.pkg in games }
    val gameList = filtered.filter { it.isGame && it.pkg !in games }
    val others = filtered.filter { !it.isGame && it.pkg !in games }

    val toggle: (AppItem, Boolean) -> Unit = { app, on ->
        games = if (on) games + app.pkg else games - app.pkg
        Prefs.setGames(ctx, games)
    }

    Box(Modifier.fillMaxSize().background(C.Ink)) {
        Box(
            Modifier.fillMaxWidth().height(460.dp)
                .background(Brush.verticalGradient(listOf(C.Violet.copy(alpha = 0.24f), Color.Transparent)))
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 56.dp)
        ) {
            item {
                Text(
                    "Game Focus",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                    color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif, letterSpacing = (-0.4).sp
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Orb(state) {
                        if (allPerms) {
                            val turningOn = !enabled
                            enabled = turningOn
                            Prefs.setEnabled(ctx, turningOn)
                            if (turningOn && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    val title = when (state) {
                        OrbState.Setup -> "Almost ready"
                        OrbState.Off -> "Paused"
                        OrbState.Ready -> "Guarding"
                        OrbState.Engaged -> "Silent"
                    }
                    val sub = when (state) {
                        OrbState.Setup -> "Finish the setup below to start"
                        OrbState.Off -> "Tap the button to start guarding your games"
                        OrbState.Ready -> if (games.isEmpty()) "Pick a game below to begin" else "Waiting for you to open a game"
                        OrbState.Engaged -> "Notifications are off while you play ${activeLabel ?: "your game"}"
                    }
                    Text(title, color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sub, color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            }
            item {
                if (!allPerms) {
                    SetupCard(step = if (!hasUsage) 1 else 2) {
                        val action = if (!hasUsage) Settings.ACTION_USAGE_ACCESS_SETTINGS
                        else Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    ModePicker(mode) { mode = it; Prefs.setMode(ctx, it) }
                }
            }
            item { SearchBar(query) { query = it } }

            if (!loaded) {
                item { Text("Loading your apps…", color = C.Muted, modifier = Modifier.padding(24.dp)) }
            }
            if (picked.isNotEmpty()) {
                item { SectionTitle("Silenced for these (${picked.size})") }
                items(picked, key = { it.pkg }) { AppRow(it, true) { on -> toggle(it, on) } }
            }
            if (gameList.isNotEmpty()) {
                item { SectionTitle("Games on this phone") }
                items(gameList, key = { it.pkg }) { AppRow(it, false) { on -> toggle(it, on) } }
            }
            if (others.isNotEmpty()) {
                item { SectionTitle("Other apps") }
                items(others, key = { it.pkg }) { AppRow(it, false) { on -> toggle(it, on) } }
            }
        }
    }
}

@Composable
fun Orb(state: OrbState, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(
        initialValue = 0.94f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )
    val scale = if (state == OrbState.Engaged) pulse else 1f
    val (a, b) = when (state) {
        OrbState.Setup -> C.Muted to C.PanelHi
        OrbState.Off -> C.Muted to C.PanelHi
        OrbState.Ready -> C.Violet to C.Mint
        OrbState.Engaged -> C.Pink to C.Violet
    }
    Box(
        Modifier.size(240.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2
            if (state == OrbState.Ready || state == OrbState.Engaged) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(a.copy(alpha = 0.40f), Color.Transparent), center = c, radius = r),
                    radius = r * scale
                )
            }
            drawCircle(color = C.Panel, radius = r * 0.70f)
            drawCircle(
                brush = Brush.sweepGradient(listOf(a, b, a), center = c),
                radius = r * 0.70f,
                style = Stroke(width = 6.dp.toPx())
            )
            val gr = r * 0.22f
            val glyph = if (state == OrbState.Engaged || state == OrbState.Ready) Color.White else C.Muted
            drawArc(
                color = glyph, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(c.x - gr, c.y - gr), size = Size(gr * 2, gr * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawLine(
                color = glyph, start = Offset(c.x, c.y - gr * 1.25f), end = Offset(c.x, c.y - gr * 0.1f),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun SetupCard(step: Int, onOpen: () -> Unit) {
    val title = if (step == 1) "Let Game Focus see which app is open" else "Allow it to switch Do Not Disturb"
    val body = if (step == 1) "Turn on Game Focus in the list that opens. It is only used to notice your game."
    else "Turn on Game Focus on the next screen. This is how notifications get silenced."
    val button = if (step == 1) "Open usage access" else "Open Do Not Disturb access"
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(C.Panel)
            .border(1.dp, C.Violet.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        Text("Step $step of 2", color = C.Violet, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = C.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(C.Violet, C.Pink)))
                .clickable(onClick = onOpen).padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) { Text(button, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ModePicker(mode: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("While a game is open", color = C.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Total silence", mode == "silence") { onPick("silence") }
            Chip("Alarms can ring", mode == "alarms") { onPick("alarms") }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) C.Violet else C.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, color = if (selected) Color.White else C.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(50)).background(C.Panel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = C.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search apps", color = C.Muted, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(C.Violet),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
fun AppRow(app: AppItem, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (checked) C.Violet.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon, contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (app.isGame) Text("Game", color = C.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.Violet, checkedThumbColor = Color.White,
                uncheckedTrackColor = C.PanelHi, uncheckedThumbColor = C.Muted,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
