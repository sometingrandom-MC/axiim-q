# Axiim Q UHD — Modern Native Kotlin & Jetpack Compose App (`arm64-v8a` / Universal)

A ground-up native Android application in Kotlin and Jetpack Compose for controlling and streaming to the **Axiim Q UHD** wireless WiSA surround sound amplifier (`zopa`, firmware `v19.618.421`).

## Design & Architecture
- **Material 3 Expressive + Holo Aesthetic**: Combines deep Holo obsidian/slate glass surfaces, glowing `1dp` cyan/mint borders, and dynamic `ACCENT-COLOR` adaptation (`#00FFBF` / `#00A5DD` / `#33B5E5`) with Material 3 Expressive spring physics, morphing corner radii, and interactive canvas visualizations.
- **100% Clean-Room Native Implementation**: Written from scratch in Kotlin with Coroutines, `StateFlow`, `kotlinx.serialization`, and Jetpack Compose against the protocol specification in [`../docs/`](file:///home/sam/antigravity/Axiim%20Q/docs/README.md).

## Implemented Features
1. **SSDP Multicast Discovery & Device Management** ([`SsdpDiscoveryClient.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/network/SsdpDiscoveryClient.kt)):
   - Broadcasts `M-SEARCH` to `239.255.255.250:1900` (`ST: urn:axiim-com:device:zopa:1`).
   - Leniently parses the Q's `ST::` double-colon header quirk, extracts the hardware MAC address from the `USN` UUID, and tracks missed discovery sweeps (`MaxMissedAttempts = 2`) with manual IP entry support.
2. **Persistent JSON-RPC 2.0 Control & Push Event Bus (`TCP 3000`)** ([`AxiimRpcClient.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/network/AxiimRpcClient.kt), [`JsonFrameExtractor.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/network/JsonFrameExtractor.kt)):
   - Brace-balanced streaming JSON frame extractor handles back-to-back concatenated server responses and notifications without newline delimiters.
   - Real-time push synchronization for `audio.volume_changed`, `avr.input_changed`, `power.power_state`, `nowplaying.new_item`, `audio.eq_set`, `theme.accent-color`, `system.device-name`, and `diagnostic.customer`.
   - Built-in safety guard preventing `settings.get` with empty `params: []`.
3. **Master Control & Live HDMI Telemetry** ([`DashboardScreen.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/ui/screens/DashboardScreen.kt)):
   - Expressive 270° rotary Holo volume dial with drag-jitter suppression, mute toggle, 7-port AVR HDMI/internal input switcher (`avr.input_list`, `avr.input_set`), live HDMI video timing telemetry (`settings.get(["video"])`), and instant DSP toggles.
4. **CEC & OSD Remote Control** ([`RemoteScreen.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/ui/screens/RemoteScreen.kt)):
   - Tactile circular D-Pad, interactive Holo gesture touchpad with tap ripples and swipe trails, full number/TV/color keypad, media transport bar (`inject.key_press`), and secondary UDP `6210` `StringCommand` support for `*` and `#`.
5. **WiSA Wireless Speaker Stage, Manual Calibration & 8×10-Band EQ** ([`WisaEqScreen.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/ui/screens/WisaEqScreen.kt)):
   - Interactive 7.1 / 5.1 soundstage radar map with animated concentric acoustic waves during speaker identification tones (`audio.play_tone` / `audio.stop_tone`), speaker discovery with 2s `audio.setup_active` keepalive, channel assignment (`audio.set_speaker_loc`), and speaker renaming.
   - 12 EQ presets + linked custom 10-band EQ + independent per-speaker 8×10-band EQ (`uEQC1_1`..`uEQC8_10`, `-5.0` to `+5.0` dB in `0.5` dB steps) with an animated cubic-bezier frequency response spline and Subwoofer LPF crossover controls.
   - Manual per-channel volume trim (`-20` to `+20` dB) with pink noise reference tone (`audio.play_level_calibration`) and per-channel speaker distance (`0.0` to `40.0` ft in `0.1` ft steps).
6. **Media Streaming: "Listen on Phone", "Play to Q" & OData Library** ([`MediaScreen.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/ui/screens/MediaScreen.kt), [`IpAudioReceiver.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/network/IpAudioReceiver.kt), [`PlayToQHttpServer.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/network/PlayToQHttpServer.kt)):
   - **Listen on Phone (`audio.ip_out_*`)**: 48 kHz 16-bit stereo PCM over UDP RTP with an **80 ms anti-dropout jitter buffer**, 28-second keepalive timer, and 16-band stereo VU spectrum analyzer.
   - **Play to Q (`player.remote_src`)**: Embedded streaming HTTP/1.1 server on port `9999` with `Range: bytes=...` (`206 Partial Content`) support for local audio/video files and custom stream URLs.
   - **OData Library Browser (`Port 80`)**: Browses `/-/music`, `/-/music/artists`, `/-/music/albums`, `/-/videos`, `/-/videos/movies`, and `/-/pictures` (`player.cdata_src`).
7. **Full Hardware DSP & System Settings** ([`SettingsScreen.kt`](file:///home/sam/antigravity/Axiim%20Q/app/app/src/main/java/com/axiim/q/ui/screens/SettingsScreen.kt)):
   - Exposes Party Mode, Super Stereo, DTS Neural, DTS Neo:6, Dolby DRC Night Mode, Dolby TrueHD +10 dB LFE Boost, Auto Format Gain, Klipsch Speaker LEDs, HDMI Audio Out, Startup High Volume limit, Front-Panel Blue LED, Bluetooth A2DP sink pairing (`bluetooth.discoverable`), HDMI-CEC / ARC settings, and Diagnostics (`diagnostic.send`).
