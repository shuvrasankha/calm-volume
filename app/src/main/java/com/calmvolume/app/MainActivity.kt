package com.calmvolume.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val Panel = Color(0xFF1E1E1E)
private val Muted = Color(0xFFB8B8B8)
private val FocusRing = Color(0xFFFFD400)

class MainActivity : ComponentActivity() {
    private lateinit var player: SamplePlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = SamplePlayer(this)
        setContent { Screen(settingsStore(), player) }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }
}

private val Descriptions = mapOf(Preset.Light to "Gentle", Preset.Medium to "Balanced", Preset.Strong to "Late night")

@Composable
private fun Screen(store: SettingsStore, player: SamplePlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { store.settings }.collectAsStateWithLifecycle(Settings())
    val status by AudioLeveler.status.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    // Also covers the app being killed while the saved setting is still on; starting a running service is harmless
    LaunchedEffect(settings.enabled) {
        if (settings.enabled) ContextCompat.startForegroundService(context, Intent(context, LevelerService::class.java))
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
            .padding(horizontal = 96.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Calm Volume", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Keeps explosions and loud music from drowning out dialogue.", color = Muted, fontSize = 18.sp)
        }

        Choice(
            label = "Reduce loud sounds",
            trailing = if (settings.enabled) "On" else "Off",
            selected = settings.enabled,
            fontSize = 30.sp,
            modifier = Modifier.fillMaxWidth().focusRequester(first),
        ) {
            // The service watches the saved settings, so this is all the toggle has to do
            scope.launch { store.setEnabled(!settings.enabled) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Strength", color = Muted, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Preset.entries.forEach { p ->
                    Choice(
                        label = if (p == settings.preset) "✓ ${p.name}" else p.name,
                        subtitle = Descriptions.getValue(p),
                        selected = p == settings.preset,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { store.setPreset(p) } }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).background(Panel, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(status.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(status.detail, color = Muted, fontSize = 17.sp)
            }
            Choice(
                label = if (playing) "Stop test" else "Play test sound",
                selected = false,
                modifier = Modifier.width(280.dp),
            ) { player.toggle() }
        }

        Text(
            "If you use a soundbar or receiver with Dolby/DTS passthrough, set TV audio output to PCM/Stereo or this app can't process the sound.",
            color = Muted, fontSize = 16.sp,
        )
    }
}

/**
 * White when selected, dark otherwise; the focused one gets a thick yellow ring and grows.
 * [trailing] puts the state on the right (the toggle); [subtitle] adds a second line.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val container = if (selected) Color.White else Panel
    val content = if (selected) Color.Black else Color.White
    Button(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        colors = ButtonDefaults.colors(
            containerColor = container, contentColor = content,
            focusedContainerColor = container, focusedContentColor = content,
            pressedContainerColor = container, pressedContentColor = content,
        ),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(4.dp, FocusRing))),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        shape = ButtonDefaults.shape(RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = if (trailing != null) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = if (trailing != null) Alignment.Start else Alignment.CenterHorizontally) {
                Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold)
                subtitle?.let { Text(it, fontSize = 16.sp) }
            }
            trailing?.let { Text(it, fontSize = fontSize, fontWeight = FontWeight.Bold) }
        }
    }
}
