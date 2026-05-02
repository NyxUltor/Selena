# Selena

Selena is a background-first, voice-activated system assistant for Android (rooted and unrooted
devices). It runs entirely on-device — no cloud APIs, no paid SDKs.

## What it does

- Starts a persistent foreground service on first launch and immediately moves to background.
- Holds a CPU `WakeLock` so the voice loop keeps running after the screen turns off.
- Listens continuously for the hotword via **Porcupine** (offline, on-device).
- On hotword detection: ducks background media (audio focus), plays a start beep, opens a
  2.5-second microphone window, recognises speech offline via **Vosk**, routes the command,
  plays an end beep, and returns to idle.
- Executes commands: open installed apps, run shell commands, open Termux and run a command.
- **Elevated (root) commands require explicit voice confirmation**: Selena reads back the command
  via TTS and waits 3 seconds for "yes" or "confirm" before executing via Magisk `su`.
- Detects whether the Vosk model is present in internal storage on first launch and attempts to
  copy it from assets automatically.
- Restarts automatically after device boot via `BOOT_COMPLETED`.

## Architecture

```
core/    – foreground service, WakeLock, state machine (IDLE → LISTENING → EXECUTING → AWAITING_CONFIRMATION)
voice/   – hotword detection (Porcupine / mock), speech recognition (Vosk / mock), pipeline,
           TTS command announcer, audio focus manager
command/ – command parser, command router (returns RouteResult), sealed command model
system/  – Android intent actions, shell executor, root executor (Magisk su),
           LSPosed hook registry (SelenaHook / HookRegistry)
model/   – ModelManager: detects Vosk model presence, copies from assets, download groundwork
```

## Current status

| Component                              | Status                                                              |
|----------------------------------------|---------------------------------------------------------------------|
| Foreground service + WakeLock          | ✅ Complete                                                         |
| State machine (+ tests)               | ✅ Complete (IDLE / LISTENING / EXECUTING / AWAITING_CONFIRMATION)  |
| Voice pipeline loop                   | ✅ Complete                                                         |
| Microphone PCM capture                | ✅ Complete                                                         |
| Hotword detection                     | ✅ Porcupine integration (falls back to mock without key)           |
| Speech recognition / ASR              | ✅ Vosk integration (falls back to null without model)              |
| Command parsing (+ tests)             | ✅ Complete                                                         |
| Command routing (+ tests)             | ✅ Complete — returns `RouteResult` (Executed / PendingConfirmation)|
| App launching via Intent              | ✅ Complete                                                         |
| Shell command execution               | ✅ Complete                                                         |
| Elevated (root) execution             | ✅ Magisk `su` — gated behind voice confirmation                    |
| **Safe sudo confirmation flow**       | ✅ TTS read-back + confirm window + cancel on timeout               |
| Termux integration                    | ✅ Complete                                                         |
| Battery optimisation prompt           | ✅ Complete                                                         |
| **Audio focus / ducking**             | ✅ Ducks background media during listening window                   |
| **Model management lifecycle**        | ✅ Detects presence, copies from assets; download groundwork ready  |
| **LSPosed hook registry**             | ✅ `SelenaHook` interface + `HookRegistry` + `LsposedHookEntryPoint`|
| Boot auto-start                       | ✅ Complete                                                         |

## Setup: hotword detection (Porcupine)

1. Create a free account at https://console.picovoice.ai/ and copy your **Access Key**.
2. Add it to your local `gradle.properties` (never commit this file):
   ```
   PICOVOICE_ACCESS_KEY=<your_key>
   ```
3. *(Optional)* Train a custom "Selena" keyword at https://console.picovoice.ai/ppn, download
   the `.ppn` file, and copy it to `app/src/main/assets/porcupine_keyword.ppn`.
   Without a custom model the built-in "PORCUPINE" keyword is used.
4. Adjust sensitivity (0–1) via `HOTWORD_SENSITIVITY` in `build.gradle.kts` (default 0.5).

Without a key the app falls back to `MockHotwordDetector` (triggers every 10 polls).

## Setup: offline speech recognition (Vosk)

1. Download a small English model from https://alphacephei.com/vosk/models
   — recommended: **vosk-model-small-en-us-0.22** (~40 MB).
2. Either:
   - Bundle the model in the APK: unzip and copy the directory into
     `app/src/main/assets/vosk-model-small-en-us-0.22/` (or as a zip:
     `app/src/main/assets/vosk-model-small-en-us-0.22.zip`).
     `ModelManager.copyFromAssets()` will extract it to internal storage on first launch.
   - Or place the model directly in internal storage at
     `<filesDir>/vosk-models/vosk-model-small-en-us-0.22/`.
3. The model name must match `VOSK_MODEL_NAME` in `build.gradle.kts`.

Without a model the recognizer logs a clear error and returns `null` for every listening window.

## Root commands and safety

Prefix voice commands with **"sudo"** (e.g. *"sudo run id"*). Selena will **not** execute the
command immediately. Instead it:

1. Transitions to `AWAITING_CONFIRMATION` state.
2. Reads back the command via TTS: *"Confirming: run id as root?"*.
3. Opens a 3-second confirmation window listening for **"yes"** or **"confirm"**.
4. Executes via `MagiskRootExecutor` only on explicit confirmation.
5. Cancels silently on any other response or silence.

The app must be granted superuser permissions in Magisk Manager.

## LSPosed hook registry

Add system-level capabilities by implementing `SelenaHook` and registering it:

```kotlin
LsposedHookEntryPoint.registry().register(object : SelenaHook {
    override val id = "my-hook"
    override val description = "Does something useful"
    override fun install() { /* Xposed hook code here */ }
})
```

`LsposedHookEntryPoint.onPackageLoaded(packageName)` installs all registered hooks when
the LSPosed module loads a target package. Failures in individual hooks are caught and logged
so one broken hook does not prevent others from loading.

## Requirements

- Android 8.0+ (API 26)
- `RECORD_AUDIO` permission — requested at first launch
- For Porcupine: a free Picovoice access key (see Setup above)
- For Vosk: the model directory in `assets/` or internal storage (see Setup above)
- For root features: rooted device with Magisk installed

## Running tests

```bash
./gradlew test
```
