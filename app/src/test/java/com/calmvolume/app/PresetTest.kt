package com.calmvolume.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTest {
    @Test
    fun tableMatchesSpec() {
        // threshold, ratio, attack, release, post-gain, limiter threshold
        fun row(p: Preset) = listOf(p.thresholdDb, p.ratio, p.attackMs, p.releaseMs, p.postGainDb, p.limiterThresholdDb)
        assertEquals(listOf(-24f, 2f, 10f, 200f, 4f, -3f), row(Preset.Light))
        assertEquals(listOf(-30f, 3f, 10f, 250f, 6f, -3f), row(Preset.Medium))
        assertEquals(listOf(-36f, 4f, 5f, 300f, 9f, -2f), row(Preset.Strong))
    }

    @Test
    fun strongerPresetsCompressHarder() {
        val ps = Preset.entries
        assertEquals(listOf("Light", "Medium", "Strong"), ps.map { it.name })
        assertEquals(ps.sortedByDescending { it.thresholdDb }, ps)
        assertEquals(ps.sortedBy { it.ratio }, ps)
        ps.forEach { assertTrue(it.thresholdDb < 0 && it.ratio > 1 && it.limiterThresholdDb < 0) }
    }
}
