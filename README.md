# InmoBridge

Tethered launcher system for INMO Air 3 smart glasses + Android phone.
Phone = brain (AI routing, config), glasses = HUD (two-zone D-pad launcher).

- `:core` — wire protocol (newline-delimited JSON envelopes) + IntentRouter (offline-first wake-word command matching)
- `:glasses` — transparent LauncherActivity, LauncherFocus two-zone state machine, custom-canvas LauncherView, auto-reconnecting BridgeClient
- `:phone` — BridgeService (foreground), TCP BridgeServer :8899, CommandRouter with pluggable AiProvider

Toolchain: AGP 8.13.2 / Kotlin 2.3.21 / Gradle 8.14.5 / JDK 21 (matches GamepadMouse workspace)
