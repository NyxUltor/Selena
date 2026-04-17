# Selena

Selena is a background-first, voice-activated system assistant for Android (rooted and unrooted
devices). It runs entirely on-device — no cloud APIs, no paid SDKs.

## What it does

- Starts a persistent foreground service on first launch and immediately moves to background.
- Holds a CPU `WakeLock` so the voice loop keeps running after the screen turns off.
- Listens continuously for the hotword **"Selena"** via a low-power polling loop.
- On hotword detection: plays a start beep, opens a 2.5-second microphone window, captures raw
  PCM audio, routes the recognised command, then plays an end beep and returns to idle.
- Executes commands: open installed apps, run shell commands, run elevated root commands, open
  Termux and run a command inside it.
- Restarts automatically after device boot via `BOOT_COMPLETED`.

## Architecture

```
core/    – foreground service, WakeLock, state machine (IDLE → LISTENING → EXECUTING)
voice/   – hotword detection interface + mock, speech recognition interface + AudioRecord scaffold
command/ – command parser, command router, sealed command model
system/  – Android intent actions, shell executor, root executor (Magisk scaffold), LSPosed scaffold
```

## Current status

| Component                    | Status                                                    |
|------------------------------|-----------------------------------------------------------|
| Foreground service + WakeLock | ✅ Complete                                              |
| State machine (+ tests)      | ✅ Complete                                               |
| Voice pipeline loop          | ✅ Complete                                               |
| Microphone PCM capture       | ✅ Scaffold — raw audio captured, ASR engine TBD          |
| Hotword detection            | 🔧 Mock — real engine integration point clearly marked    |
| Speech recognition / ASR     | 🔧 Scaffold — PCM captured, offline ASR engine TBD        |
| Command parsing (+ tests)    | ✅ Complete                                               |
| Command routing (+ tests)    | ✅ Complete                                               |
| App launching via Intent     | ✅ Complete                                               |
| Shell command execution      | ✅ Complete                                               |
| Elevated (root) execution    | 🔧 Scaffold — Magisk `su` integration point marked        |
| Termux integration           | ✅ Complete                                               |
| LSPosed hook entry point     | 🔧 Scaffold — no hooks implemented yet                    |
| Boot auto-start              | ✅ Complete                                               |

## Integrating a real hotword engine

Replace `MockHotwordDetector` with a real implementation of the `HotwordDetector` interface and
wire it into `SelenaForegroundService`:

- [Porcupine](https://picovoice.ai/platform/porcupine/) — offline, free tier available
- Custom TensorFlow Lite keyword-spotting model

See the `TODO` in `MockHotwordDetector.kt` for the exact integration point.

## Integrating offline speech recognition (ASR)

`AudioRecordSpeechRecognizer` already captures raw 16-bit PCM mono audio at 16 kHz. Wire an ASR
engine into `recognizeForWindow()` where the `TODO` comment is:

- [Vosk](https://alphacephei.com/vosk/) — small offline models, Android-friendly
- [Whisper.cpp](https://github.com/ggml-org/whisper.cpp) via JNI

## Root commands

Prefix voice commands with **"sudo"** (e.g. *"sudo run id"*). The `MagiskRootExecutor` stub is
ready for Magisk's `su` binary — see its `TODO` comment for the integration point.

## Requirements

- Android 8.0+ (API 26)
- `RECORD_AUDIO` permission — requested at first launch
- For root features: rooted device with Magisk installed

## Running tests

```bash
./gradlew test
```
