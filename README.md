# Selena

Selena is a background-first, voice-activated system assistant for Android (rooted and unrooted
devices). It runs entirely on-device — no cloud APIs, no paid SDKs.

## What it does

- Starts a persistent foreground service on first launch and immediately moves to background.
- Holds a CPU `WakeLock` so the voice loop keeps running after the screen turns off.
- Listens continuously for the hotword via **Porcupine** (offline, on-device).
- On hotword detection: plays a start beep, opens a 2.5-second microphone window, recognises
  speech offline via **Vosk**, routes the command, plays an end beep, and returns to idle.
- Executes commands: open installed apps, run shell commands, run elevated root commands (via
  Magisk `su`), open Termux and run a command inside it.
- Restarts automatically after device boot via `BOOT_COMPLETED`.

## Architecture

```
core/    – foreground service, WakeLock, state machine (IDLE → LISTENING → EXECUTING)
voice/   – hotword detection (Porcupine / mock), speech recognition (Vosk / mock), pipeline
command/ – command parser, command router, sealed command model
system/  – Android intent actions, shell executor, root executor (Magisk su), LSPosed scaffold
```

## Current status

| Component                    | Status                                                      |
|------------------------------|-------------------------------------------------------------|
| Foreground service + WakeLock | ✅ Complete                                                |
| State machine (+ tests)      | ✅ Complete                                                 |
| Voice pipeline loop          | ✅ Complete                                                 |
| Microphone PCM capture       | ✅ Complete                                                 |
| Hotword detection            | ✅ Porcupine integration (falls back to mock without key)   |
| Speech recognition / ASR     | ✅ Vosk integration (falls back to null without model)      |
| Command parsing (+ tests)    | ✅ Complete                                                 |
| Command routing (+ tests)    | ✅ Complete                                                 |
| App launching via Intent     | ✅ Complete                                                 |
| Shell command execution      | ✅ Complete                                                 |
| Elevated (root) execution    | ✅ Magisk `su` implementation                               |
| Termux integration           | ✅ Complete                                                 |
| Battery optimisation prompt  | ✅ Complete                                                 |
| LSPosed hook entry point     | 🔧 Scaffold — no hooks implemented yet                      |
| Boot auto-start              | ✅ Complete                                                 |

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
2. Unzip and copy the model **directory** into:
   ```
   app/src/main/assets/vosk-model-small-en-us-0.22/
   ```
3. The model name must match `VOSK_MODEL_NAME` in `build.gradle.kts`.
4. On first launch `StorageService.sync` copies the model to internal storage; subsequent
   launches reuse the cached copy.

Without a model the recognizer logs a clear error and returns `null` for every listening window.

## Root commands

Prefix voice commands with **"sudo"** (e.g. *"sudo run id"*). `MagiskRootExecutor` runs the
command via `su -c` — the app must be granted superuser permissions in Magisk Manager.

## Requirements

- Android 8.0+ (API 26)
- `RECORD_AUDIO` permission — requested at first launch
- For Porcupine: a free Picovoice access key (see Setup above)
- For Vosk: the model directory in `assets/` (see Setup above)
- For root features: rooted device with Magisk installed

## Running tests

```bash
./gradlew test
```
