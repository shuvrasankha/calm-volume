# Calm Volume: plan

Docs read before coding: `DynamicsProcessing` reference, foreground service types, FGS background-start restrictions.

## Architecture

Saved settings (DataStore) are the single source of truth. The UI only writes them; the service reacts.

| File | Job |
|---|---|
| `Preset.kt` | The preset table and the fixed limiter constants. Plain Kotlin. |
| `Settings.kt` | `SettingsStore` over DataStore: `enabled`, `preset`. Unknown preset names fall back to Medium. |
| `AudioLeveler.kt` | Owns the `DynamicsProcessing` instances and the `Status` shown in the UI. |
| `LevelerService.kt` | Foreground service: watches settings, drives the leveler, listens for player sessions. Also `BootReceiver`. |
| `SamplePlayer.kt` | Generates and plays the test sound (quiet speech, explosion, speech). Nothing bundled, nothing downloaded. |
| `MainActivity.kt` | One D-pad screen in Compose for TV. |

### Audio engine
- Effect on **session 0** (global mix): 1 MBC band (`MBC_BANDS`; cutoffs already spread for more), limiter on, pre/post EQ off, 2 channels first, then the widest output device if stereo is refused.
- Created successfully only if `hasControl()` and `setEnabled(true) == SUCCESS`; any exception means "not available". Nothing throws out of the leveler.
- Changing preset calls `setMbcBandAllChannelsTo` / `setLimiterAllChannelsTo` on the live effect; it is not recreated.
- **Fallback:** if session 0 fails, the service attaches a per-session effect for every `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast and releases it on `..._CLOSE_...`. If global works, per-session effects are skipped (they would process twice).
- Status: Off, All apps (global ok), Fallback (global failed), Blocked (a per-session attempt failed too).

### Manifest receiver or not?
The open/close-session broadcasts are implicit. Since API 26 a manifest receiver cannot get implicit broadcasts that are not on the exemption list, and these are not on it. So the receiver is **runtime-registered inside the service** (with `RECEIVER_EXPORTED`, because players in other apps send it). Only `BootReceiver` is in the manifest.

### Foreground service type
`specialUse` (with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property and `FOREGROUND_SERVICE_SPECIAL_USE`). None of the specific types fit: it is not `mediaPlayback` (the app plays nothing), not `dataSync`, not `systemExempted`. Android 14+ requires a type. `startForeground` passes the type only on API 34+; on 29–33 it passes 0, because those platforms do not know the `specialUse` bit.

### Boot
`BootReceiver` reads the saved `enabled` flag and starts the service. Android 15 forbids starting `dataSync`, `mediaPlayback`, `camera`, `microphone`, `mediaProjection` and `phoneCall` services from `BOOT_COMPLETED`; `specialUse` is not on that list, and `BOOT_COMPLETED` is exempt from the Android 12 background-start ban. It uses `goAsync()` so the DataStore read can finish.

## Risks
1. **Session 0 is deprecated and often unavailable.** The effect has to be listed in the device's `audio_effects` config for the global mix; many TV chipsets do not. Fallback exists but only helps players that announce their session, and Netflix/YouTube generally do not. This is the main way the app may do nothing on a given TV. I cannot test on a TV here.
2. **Passthrough.** Bitstreamed Dolby/DTS never goes through the Android mixer, so no effect can touch it. Documented in the UI and README.
3. **Another app holds the effect.** Priority 0 is shared; `hasControl()` false is reported as unavailable.
4. **Notification permission (Android 13+).** Declared, not requested: the service runs without it, the notification is just hidden. TVs rarely show it anyway.
5. **Loudness.** Post-gain is makeup gain in the compressor; the limiter ceiling (−3/−2 dB) is what protects against clipping.
