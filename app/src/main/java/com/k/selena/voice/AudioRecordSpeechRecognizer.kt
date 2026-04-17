package com.k.selena.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Real microphone speech recognizer scaffold.
 *
 * Opens an [AudioRecord] for the duration of the listening window and captures raw 16-bit PCM
 * mono audio at 16 kHz. The captured bytes are ready to be forwarded to an offline ASR engine.
 *
 * TODO: Feed the captured PCM bytes to an offline ASR engine, e.g.:
 *   - Vosk (https://alphacephei.com/vosk/) — small offline models, Android-friendly
 *   - Whisper.cpp (https://github.com/ggml-org/whisper.cpp) via JNI
 * Until an engine is wired in, this implementation returns null (no recognized command).
 */
class AudioRecordSpeechRecognizer : SpeechRecognizer {

    override fun recognizeForWindow(windowMs: Long): String? {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Cannot determine AudioRecord minimum buffer size; skipping capture")
            return null
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize; check RECORD_AUDIO permission")
            recorder.release()
            return null
        }

        var recordingStarted = false
        return try {
            recorder.startRecording()
            recordingStarted = true
            Log.i(TAG, "Microphone open – capturing ${windowMs}ms of PCM audio")

            val readBuffer = ByteArray(bufferSize)
            val baos = ByteArrayOutputStream()
            val deadline = System.currentTimeMillis() + windowMs

            while (System.currentTimeMillis() < deadline) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    baos.write(readBuffer, 0, read)
                }
            }

            Log.i(TAG, "Captured ${baos.size()} bytes of raw PCM")

            // TODO: Pass baos.toByteArray() to an offline ASR engine and return the transcript.
            null
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture error", e)
            null
        } finally {
            if (recordingStarted) {
                runCatching { recorder.stop() }
                    .onFailure { Log.w(TAG, "AudioRecord stop failed during cleanup", it) }
            }
            recorder.release()
            Log.i(TAG, "Microphone released")
        }
    }

    companion object {
        private const val TAG = "AudioRecordSpeechRecog"
        private const val SAMPLE_RATE = 16_000
    }
}
