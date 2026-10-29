package com.calmvolume.app

/** One row of the preset table: compressor settings plus the limiter ceiling. All times in ms, levels in dB. */
enum class Preset(
    val thresholdDb: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val postGainDb: Float,
    val limiterThresholdDb: Float,
) {
    Light(-24f, 2f, 10f, 200f, 4f, -3f),
    Medium(-30f, 3f, 10f, 250f, 6f, -3f),
    Strong(-36f, 4f, 5f, 300f, 9f, -2f),
}

// The limiter is the same for every preset; only its threshold varies.
const val LIMITER_ATTACK_MS = 1f
const val LIMITER_RELEASE_MS = 60f
const val LIMITER_RATIO = 10f
