package com.calmvolume.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.Limiter
import android.media.audiofx.DynamicsProcessing.MbcBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.pow

enum class Status(val title: String, val detail: String) {
    Off("Off", "Turn on to even out loud scenes."),
    AllApps("Active on all apps", "Explosions and music are pulled down in every app."),
    Fallback(
        "Active on supported apps only (fallback)",
        "This TV blocks system-wide processing. Only apps that share their audio session are covered, and many do not.",
    ),
    Blocked("Blocked on this TV", "This TV does not allow audio effects, so Calm Volume cannot change the sound."),
}

private const val MBC_BANDS = 1 // raise to 3 later; cutoffs below spread themselves
private const val MAX_FREQ_HZ = 20000.0
private const val KNEE_DB = 6f
private const val NOISE_GATE_DB = -90f
private const val GLOBAL_SESSION = 0

/**
 * Owns the DynamicsProcessing instances: one on the global output mix (session 0), or, when the
 * TV refuses that, one per session that a player announces. Main thread only.
 */
class AudioLeveler(private val context: Context) {
    private var on = false
    private var preset = Preset.Medium
    private var global: DynamicsProcessing? = null
    private val sessions = mutableMapOf<Int, DynamicsProcessing>()
    private var sessionFailed = false

    fun enable(preset: Preset) {
        on = true
        this.preset = preset
        if (global == null) global = runCatching { create(GLOBAL_SESSION) }.getOrNull()
        // Global covers everything, so per-session effects would only process the audio twice
        if (global != null) releaseSessions()
        (listOfNotNull(global) + sessions.values).forEach { apply(it, preset) }
        publish()
    }

    fun attach(session: Int) {
        if (!on || global != null || session in sessions) return
        runCatching { create(session) }
            .onSuccess { sessions[session] = it; sessionFailed = false }
            .onFailure { sessionFailed = true }
        publish()
    }

    fun detach(session: Int) {
        sessions.remove(session)?.release()
    }

    fun disable() {
        on = false
        global?.release()
        global = null
        releaseSessions()
        publish()
    }

    private fun releaseSessions() {
        sessions.values.forEach { it.release() }
        sessions.clear()
    }

    private fun publish() {
        status.value = when {
            !on -> Status.Off
            global != null -> Status.AllApps
            sessionFailed -> Status.Blocked
            else -> Status.Fallback
        }
    }

    private fun create(session: Int): DynamicsProcessing {
        var failure: Exception? = null
        // Some TVs implement only one variant (this one's log: "variant 1 not available"), so try both
        val tries = channelCandidates().flatMap { ch ->
            listOf(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION)
                .map { ch to it }
        }
        for ((channels, variant) in tries) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    variant, channels,
                    false, 0, true, MBC_BANDS, false, 0, true,
                ).build()
                val dp = DynamicsProcessing(0, session, config)
                try {
                    check(dp.hasControl()) { "another app controls the effect" }
                    apply(dp, preset)
                    check(dp.setEnabled(true) == AudioEffect.SUCCESS) { "effect would not enable" }
                } catch (e: Exception) {
                    dp.release()
                    throw e
                }
                return dp
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure!!
    }

    /** Stereo first (what the mix normally is); a wider output is tried next if stereo is refused. */
    private fun channelCandidates(): List<Int> {
        val audio = context.getSystemService(AudioManager::class.java)
        val widest = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .flatMap { it.channelCounts.asList() }.maxOrNull() ?: 2
        return listOf(2, widest).distinct()
    }

    private fun apply(dp: DynamicsProcessing, p: Preset) {
        for (band in 0 until MBC_BANDS) {
            val cutoff = MAX_FREQ_HZ.pow((band + 1.0) / MBC_BANDS).toFloat()
            dp.setMbcBandAllChannelsTo(
                band,
                MbcBand(true, cutoff, p.attackMs, p.releaseMs, p.ratio, p.thresholdDb, KNEE_DB, NOISE_GATE_DB, 1f, 0f, p.postGainDb),
            )
        }
        dp.setLimiterAllChannelsTo(
            Limiter(true, true, 0, LIMITER_ATTACK_MS, LIMITER_RELEASE_MS, LIMITER_RATIO, p.limiterThresholdDb, 0f),
        )
    }

    companion object {
        // shortcut: process-wide, since exactly one service instance exists; use a bound service if that changes
        val status = MutableStateFlow(Status.Off)
    }
}
