package com.calmvolume.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keeps the effect alive; the saved settings are the only input, so the UI just writes them. */
class LevelerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var leveler: AudioLeveler
    private var job: Job? = null

    // Players announce their session with an implicit broadcast, which a manifest receiver
    // can no longer receive (API 26+), so this one is registered at runtime.
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (session <= 0) return
            if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) leveler.attach(session)
            else leveler.detach(session)
        }
    }

    override fun onCreate() {
        leveler = AudioLeveler(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Calm Volume", NotificationManager.IMPORTANCE_LOW))
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        // Exported: the broadcasts come from other apps
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14+ wants the type here; older versions only know the one in the manifest
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID, notice("Calm Volume is starting"), type)
        job?.cancel()
        job = scope.launch {
            settingsStore().settings.collect {
                if (!it.enabled) {
                    stopSelf()
                } else {
                    leveler.enable(it.preset)
                    getSystemService(NotificationManager::class.java)
                        .notify(ID, notice("Calm Volume is on – ${it.preset.name}."))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(sessionReceiver)
        leveler.disable()
        scope.cancel()
    }

    override fun onBind(intent: Intent?) = null

    private fun notice(text: String): Notification =
        Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(text).setOngoing(true).build()

    private companion object {
        const val ID = 1
        const val CHANNEL = "leveler"
    }
}

/** Restarts the service after a reboot if the user left it on. specialUse is allowed to start from BOOT_COMPLETED on Android 15. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (context.settingsStore().settings.first().enabled) {
                    ContextCompat.startForegroundService(context, Intent(context, LevelerService::class.java))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
