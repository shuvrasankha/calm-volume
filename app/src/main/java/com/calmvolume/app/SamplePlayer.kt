package com.calmvolume.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val RATE = 22050
private const val SECONDS = 7

/**
 * Plays generated audio: quiet "speech" (a buzzy 130 Hz tone with a syllable rhythm), then a loud
 * explosion, then more speech. Toggle the effect between plays to hear the difference.
 */
class SamplePlayer(private val context: Context) {
    val playing = MutableStateFlow(false)
    private var track: AudioTrack? = null

    fun toggle() = if (track == null) play() else stop()

    private fun play() {
        val pcm = sample()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) = stop()
            override fun onPeriodicNotification(track: AudioTrack) {}
        })
        t.notificationMarkerPosition = pcm.size
        // Announce the session like a real player, so the fallback mode can process this test too
        announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, t.audioSessionId)
        t.play()
        track = t
        playing.value = true
    }

    fun stop() {
        val t = track ?: return
        track = null
        playing.value = false
        announce(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, t.audioSessionId)
        t.release()
    }

    private fun announce(action: String, session: Int) = context.sendBroadcast(
        Intent(action).putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName),
    )

    private fun sample(): ShortArray {
        val noise = Random(1)
        return ShortArray(RATE * SECONDS) { i ->
            val t = i.toDouble() / RATE
            val voice = (1..6).sumOf { sin(2 * PI * 130 * it * t) / it } / 2.45 * (1 + sin(2 * PI * 3 * t)) / 2
            val x = t - 2.5
            // Explosion at 2.5 s: decaying noise plus a 55 Hz thump, near full scale
            val v = if (x in 0.0..1.0) {
                0.95 * exp(-4 * x) * (0.6 * (noise.nextDouble() * 2 - 1) + 0.4 * sin(2 * PI * 55 * x))
            } else {
                0.04 * voice // about -28 dBFS
            }
            (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
